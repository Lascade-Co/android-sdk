package com.chatwoot.android.sdk.data

import com.chatwoot.android.sdk.Chatwoot
import com.chatwoot.android.sdk.ChatwootConfig
import com.chatwoot.android.sdk.ChatwootIdentity
import com.chatwoot.android.sdk.net.AttachmentDto
import com.chatwoot.android.sdk.net.CableClient
import com.chatwoot.android.sdk.net.CableEvent
import com.chatwoot.android.sdk.net.ContactRequest
import com.chatwoot.android.sdk.net.MessageDto
import com.chatwoot.android.sdk.net.WidgetApi
import com.chatwoot.android.sdk.net.WidgetSession
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal enum class AttachmentType { Image, Video, Audio, File }

/** A rendered attachment. [url] is the original; [thumbUrl] is a preview when the server has one. */
internal data class ChatAttachment(
    val type: AttachmentType,
    val url: String,
    val thumbUrl: String?,
    val fileName: String?,
    val fileSize: Long?,
    val width: Int?,
    val height: Int?,
)

/** A chat entry as rendered by the UI. */
internal data class ChatMessage(
    val id: Long,
    val content: String,
    val fromContact: Boolean,
    val isActivity: Boolean,
    val senderName: String?,
    val createdAt: Long,
    val attachments: List<ChatAttachment> = emptyList(),
    // Optimistic upload state — only set on the local placeholder while a send is in flight.
    val pending: Boolean = false,
    val failed: Boolean = false,
    val uploadProgress: Float? = null,
    val localPreview: PickedFile? = null,
)

internal data class ChatUiState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val agentTyping: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
)

/**
 * Owns the session lifecycle: bootstrap (resuming any persisted `cw_conversation` token),
 * history fetch, live RoomChannel events, and sending. The first send of a fresh session
 * goes through conversation creation.
 */
internal class ChatRepository(
    private val config: ChatwootConfig,
    private val client: HttpClient,
    private val api: WidgetApi = WidgetApi(config, client),
    private val tokenStore: TokenStore = TokenStore(),
) {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var session: WidgetSession? = null
    private var hasConversation = false

    // Optimistic placeholders get descending negative ids so they never collide with real ones.
    private var nextTempId = -1L

    suspend fun connect(scope: CoroutineScope) {
        // A different identified user than the persisted session means we must not resume the
        // previous contact's conversation — drop the stored token so bootstrap creates a fresh one.
        val wanted = Chatwoot.identity.value
        if (wanted.identifier != null &&
            wanted.identifier != tokenStore.activeIdentifier(config.websiteToken)
        ) {
            tokenStore.clearSession(config.websiteToken)
        }

        val s = api.fetchSession(tokenStore.conversationToken(config.websiteToken))
        tokenStore.saveConversationToken(config.websiteToken, s.authToken)
        tokenStore.saveActiveIdentifier(config.websiteToken, wanted.identifier)
        session = s

        runCatching { flushIdentity(s.authToken, wanted) }

        refreshMessages(s)
        _state.update { it.copy(loading = false) }

        scope.launch {
            CableClient(client, config.normalizedBaseUrl, s.pubsubToken).events().collect { event ->
                onCableEvent(event, s)
            }
        }
        // Push later attribute changes (the value we just flushed is dropped).
        scope.launch {
            Chatwoot.identity.drop(1).collect { runCatching { flushIdentity(s.authToken, it) } }
        }
    }

    /** Sends the current identity to Chatwoot: `set_user` when identified, plain update otherwise. */
    private suspend fun flushIdentity(authToken: String, id: ChatwootIdentity) {
        if (id.isEmpty) return
        val body = ContactRequest(
            identifier = id.identifier,
            identifierHash = id.identifierHash,
            name = id.name,
            email = id.email,
            phoneNumber = id.phoneNumber,
            avatarUrl = id.avatarUrl,
            customAttributes = id.customAttributes.ifEmpty { null },
        )
        if (id.identifier != null) api.setUser(authToken, body) else api.updateContact(authToken, body)
    }

    suspend fun send(content: String) {
        val s = session ?: error("ChatRepository.send called before connect")
        if (hasConversation) {
            upsert(api.sendMessage(s.authToken, content))
        } else {
            api.createConversation(s.authToken, content)
            hasConversation = true
            refreshMessages(s)
        }
    }

    /**
     * Uploads [file] as an attachment-only message. Shows an optimistic local bubble
     * immediately, then reconciles it with the server's response (the websocket echo of the
     * same id is deduped by [upsert]). On failure the placeholder is marked [ChatMessage.failed].
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sendAttachment(file: PickedFile) {
        val s = session ?: error("ChatRepository.sendAttachment called before connect")
        val tempId = nextTempId--
        val onProgress = { progress: Float ->
            _state.update { it.copy(messages = it.messages.withProgress(tempId, progress)) }
        }
        _state.update { it.copy(messages = it.messages.withUpserted(optimistic(tempId, file))) }
        try {
            if (hasConversation) {
                // POST /messages returns the created Message — reconcile directly.
                val real = api.sendAttachment(s.authToken, file, onProgress).toChatMessage()
                _state.update { it.copy(messages = it.messages.reconcilingTemp(tempId, real)) }
            } else {
                // The create response can't be parsed (string message_type) — drop the
                // placeholder and refetch so the stored attachment loads from GET /messages.
                api.createConversationWithAttachment(s.authToken, file, onProgress)
                hasConversation = true
                _state.update { it.copy(messages = it.messages.reconcilingTemp(tempId, null)) }
                refreshMessages(s)
            }
        } catch (e: Throwable) {
            _state.update { it.copy(messages = it.messages.withFailed(tempId)) }
            throw e
        }
    }

    private suspend fun onCableEvent(event: CableEvent, s: WidgetSession) {
        when (event) {
            is CableEvent.MessageCreated -> upsert(event.message)
            is CableEvent.MessageUpdated -> upsert(event.message)
            is CableEvent.TypingChanged -> _state.update { it.copy(agentTyping = event.typing) }
            CableEvent.Connected -> {
                _state.update { it.copy(connected = true) }
                // Catch up on anything broadcast while we were offline.
                runCatching { refreshMessages(s) }
            }
            CableEvent.Disconnected ->
                _state.update { it.copy(connected = false, agentTyping = false) }
        }
    }

    private suspend fun refreshMessages(s: WidgetSession) {
        val history = api.getMessages(s.authToken).mapNotNull { it.toChatMessage() }
        hasConversation = hasConversation || history.isNotEmpty()
        _state.update { state ->
            state.copy(messages = (history + state.messages.filter { m -> history.none { it.id == m.id } }).sorted())
        }
    }

    private fun upsert(dto: MessageDto) {
        val message = dto.toChatMessage() ?: return
        _state.update { state -> state.copy(messages = state.messages.withUpserted(message)) }
    }
}

// --- Pure message-list reducers (kept top-level so they're unit-testable without HTTP) ---

private fun List<ChatMessage>.sorted() = sortedWith(compareBy({ it.createdAt }, { it.id }))

/** Replaces any same-id entry with [message], keeping the list sorted. */
internal fun List<ChatMessage>.withUpserted(message: ChatMessage): List<ChatMessage> =
    (filter { it.id != message.id } + message).sorted()

/** Removes the optimistic [tempId] placeholder and upserts the reconciled [real] message. */
internal fun List<ChatMessage>.reconcilingTemp(tempId: Long, real: ChatMessage?): List<ChatMessage> {
    val withoutTemp = filter { it.id != tempId }
    return if (real == null) withoutTemp.sorted() else withoutTemp.withUpserted(real)
}

internal fun List<ChatMessage>.withProgress(tempId: Long, progress: Float): List<ChatMessage> =
    map { if (it.id == tempId) it.copy(uploadProgress = progress.coerceIn(0f, 1f)) else it }

internal fun List<ChatMessage>.withFailed(tempId: Long): List<ChatMessage> =
    map { if (it.id == tempId) it.copy(failed = true, pending = false) else it }

@OptIn(ExperimentalTime::class)
internal fun optimistic(tempId: Long, file: PickedFile): ChatMessage = ChatMessage(
    id = tempId,
    content = "",
    fromContact = true,
    isActivity = false,
    senderName = null,
    createdAt = Clock.System.now().epochSeconds,
    attachments = emptyList(),
    pending = true,
    uploadProgress = 0f,
    localPreview = file,
)

internal fun MessageDto.toChatMessage(): ChatMessage? {
    if (private) return null
    // Trim surrounding whitespace and trailing blank lines for display (sent and received alike).
    val text = content?.trim()?.takeIf { it.isNotEmpty() }
    val isActivity = messageType == 2
    val mapped = attachments.mapNotNull { it.toChatAttachment() }
    // Drop empty noise, but never an attachment- or activity-carrying message.
    if (text == null && mapped.isEmpty() && !isActivity) return null
    return ChatMessage(
        id = id,
        content = text.orEmpty(),
        fromContact = messageType == 0,
        isActivity = isActivity,
        senderName = sender?.availableName ?: sender?.name,
        createdAt = createdAt,
        attachments = mapped,
    )
}

private fun AttachmentDto.toChatAttachment(): ChatAttachment? {
    val url = dataUrl ?: thumbUrl ?: return null
    val type = when (fileType?.lowercase()) {
        "image" -> AttachmentType.Image
        "video" -> AttachmentType.Video
        "audio" -> AttachmentType.Audio
        else -> AttachmentType.File
    }
    return ChatAttachment(
        type = type,
        url = url,
        thumbUrl = thumbUrl,
        fileName = extension?.let { "file.$it" },
        fileSize = fileSize,
        width = width,
        height = height,
    )
}
