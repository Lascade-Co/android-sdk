package com.chatwoot.android.sdk.net

import com.chatwoot.android.sdk.ChatwootConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal fun defaultHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(ChatwootJson) }
    install(WebSockets)
    expectSuccess = true
}

/**
 * Chatwoot website-widget REST API. All endpoints are scoped by `website_token`; everything
 * except the bootstrap additionally authenticates with the session JWT via `X-Auth-Token`.
 */
internal class WidgetApi(
    private val config: ChatwootConfig,
    private val client: HttpClient,
) {
    private val base = config.normalizedBaseUrl

    /** Bootstraps (or resumes, when [conversationToken] is set) a contact session. */
    suspend fun fetchSession(conversationToken: String?): WidgetSession {
        val html = client.get("$base/widget") {
            parameter("website_token", config.websiteToken)
            if (!conversationToken.isNullOrBlank()) parameter("cw_conversation", conversationToken)
            // This endpoint is HTML-only; without an explicit Accept, ContentNegotiation's
            // application/json gets a 406 from Rails.
            header(HttpHeaders.Accept, "text/html")
        }.bodyAsText()
        return WidgetPageParser.parse(html) ?: error(
            "Could not bootstrap the Chatwoot widget from $base — " +
                "check the baseUrl and websiteToken."
        )
    }

    suspend fun getMessages(authToken: String): List<MessageDto> =
        client.get("$base/api/v1/widget/messages") {
            authenticated(authToken)
        }.body<MessagesPayloadDto>().payload

    suspend fun sendMessage(authToken: String, content: String): MessageDto =
        client.post("$base/api/v1/widget/messages") {
            authenticated(authToken)
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(outgoing(content)))
        }.body()

    /** First message of a session goes through conversation creation. */
    suspend fun createConversation(authToken: String, content: String) {
        client.post("$base/api/v1/widget/conversations") {
            authenticated(authToken)
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(outgoing(content)))
        }
    }

    suspend fun getAgents(): List<AgentDto> =
        client.get("$base/api/v1/widget/inbox_members") {
            parameter("website_token", config.websiteToken)
        }.body<AgentsPayloadDto>().payload

    private fun HttpRequestBuilder.authenticated(authToken: String) {
        parameter("website_token", config.websiteToken)
        header("X-Auth-Token", authToken)
    }

    @OptIn(ExperimentalTime::class)
    private fun outgoing(content: String) =
        OutgoingMessageDto(content = content, timestamp = Clock.System.now().toString(), refererUrl = "")
}
