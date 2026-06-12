package com.chatwoot.android.sdk

/**
 * Connection settings for a Chatwoot website inbox.
 *
 * @property baseUrl Chatwoot installation URL, e.g. `https://app.chatwoot.com`.
 * @property websiteToken The website inbox token from the Chatwoot dashboard.
 */
public data class ChatwootConfig(
    val baseUrl: String,
    val websiteToken: String,
) {
    init {
        require(baseUrl.startsWith("http")) { "baseUrl must be an http(s) URL, got '$baseUrl'" }
        require(websiteToken.isNotBlank()) { "websiteToken must not be blank" }
    }

    internal val normalizedBaseUrl: String = baseUrl.trimEnd('/')
}

/**
 * SDK entry point. Either call [configure] once (e.g. in `Application.onCreate` /
 * `application(_:didFinishLaunchingWithOptions:)`) or declare the platform fallback:
 *
 * - Android: `<meta-data>` entries `com.chatwoot.android.BASE_URL` and
 *   `com.chatwoot.android.WEBSITE_TOKEN` in `AndroidManifest.xml`.
 * - iOS: `ChatwootBaseUrl` and `ChatwootWebsiteToken` keys in `Info.plist`.
 */
public object Chatwoot {
    private var explicit: ChatwootConfig? = null

    public fun configure(baseUrl: String, websiteToken: String) {
        explicit = ChatwootConfig(baseUrl, websiteToken)
    }

    internal val config: ChatwootConfig
        get() = explicit ?: platformDefaultConfig() ?: error(
            "Chatwoot SDK is not configured. Call Chatwoot.configure(baseUrl, websiteToken) " +
                "or provide the platform metadata (Android manifest <meta-data> " +
                "com.chatwoot.android.BASE_URL / com.chatwoot.android.WEBSITE_TOKEN, " +
                "iOS Info.plist ChatwootBaseUrl / ChatwootWebsiteToken)."
        )
}

/** Reads the platform fallback config (Android manifest meta-data / iOS Info.plist), if present. */
internal expect fun platformDefaultConfig(): ChatwootConfig?
