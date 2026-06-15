package com.chatwoot.android.sdk.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient by design: Chatwoot payloads carry many fields the SDK doesn't need. */
internal val ChatwootJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
internal data class MessageDto(
    val id: Long,
    val content: String? = null,
    // 0 = incoming (contact), 1 = outgoing (agent), 2 = activity, 3 = template
    @SerialName("message_type") val messageType: Int = 0,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("conversation_id") val conversationId: Long? = null,
    val status: String? = null,
    val private: Boolean = false,
    val sender: SenderDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
internal data class AttachmentDto(
    val id: Long? = null,
    // image | audio | video | file | … — anything other than the first three renders as a file.
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("data_url") val dataUrl: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val extension: String? = null,
)

@Serializable
internal data class SenderDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("available_name") val availableName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val thumbnail: String? = null,
    val type: String? = null,
)

@Serializable
internal data class MessagesPayloadDto(
    val payload: List<MessageDto> = emptyList(),
)

@Serializable
internal data class AgentDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("availability_status") val availabilityStatus: String? = null,
)

@Serializable
internal data class AgentsPayloadDto(
    val payload: List<AgentDto> = emptyList(),
)

// --- Request bodies (mirrors app/javascript/widget/api/endPoints.js in chatwoot/chatwoot) ---

@Serializable
internal data class OutgoingMessageDto(
    val content: String,
    val timestamp: String,
    // No default — defaults are skipped during encoding and the widget always sends this key.
    @SerialName("referer_url") val refererUrl: String,
)

@Serializable
internal data class SendMessageRequest(
    val message: OutgoingMessageDto,
)

@Serializable
internal data class CreateConversationRequest(
    val message: OutgoingMessageDto,
)

/**
 * Flat contact body for `PATCH /api/v1/widget/contact[/set_user]`. Null fields are dropped during
 * encoding (`explicitNulls = false`), so each call sends only what the host supplied.
 */
@Serializable
internal data class ContactRequest(
    val identifier: String? = null,
    @SerialName("identifier_hash") val identifierHash: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("custom_attributes") val customAttributes: Map<String, String>? = null,
)
