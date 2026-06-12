package com.chatwoot.android.sdk.data

import com.chatwoot.android.sdk.ChatwootConfig
import com.chatwoot.android.sdk.net.CableClient
import com.chatwoot.android.sdk.net.CableEvent
import com.chatwoot.android.sdk.net.MessageDto
import com.chatwoot.android.sdk.net.WidgetApi
import com.chatwoot.android.sdk.net.WidgetSession
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A chat entry as rendered by the UI. */
internal data class ChatMessage(
    val id: Long,
    val content: String,
    val fromContact: Boolean,
    val isActivity: Boolean,
    val senderName: String?,
    val createdAt: Long,
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

    suspend fun connect(scope: CoroutineScope) {
        val s = api.fetchSession(tokenStore.conversationToken(config.websiteToken))
        tokenStore.saveConversationToken(config.websiteToken, s.authToken)
        session = s

        refreshMessages(s)
        _state.update { it.copy(loading = false) }

        scope.launch {
            CableClient(client, config.normalizedBaseUrl, s.pubsubToken).events().collect { event ->
                onCableEvent(event, s)
            }
        }
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
        _state.update { state ->
            state.copy(messages = (state.messages.filter { it.id != message.id } + message).sorted())
        }
    }

    private fun List<ChatMessage>.sorted() = sortedWith(compareBy({ it.createdAt }, { it.id }))
}

private fun MessageDto.toChatMessage(): ChatMessage? {
    if (private) return null
    val text = content?.takeIf { it.isNotBlank() } ?: return null
    return ChatMessage(
        id = id,
        content = text,
        fromContact = messageType == 0,
        isActivity = messageType == 2,
        senderName = sender?.availableName ?: sender?.name,
        createdAt = createdAt,
    )
}
