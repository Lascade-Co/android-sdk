package com.chatwoot.android.sdk.data

import com.russhwolf.settings.Settings

/**
 * Persists the `cw_conversation` session JWT (SharedPreferences / NSUserDefaults) so the
 * same contact + conversation is resumed across app launches. Keyed per website token —
 * switching inboxes must not leak another inbox's session.
 */
internal class TokenStore(private val settings: Settings = Settings()) {

    fun conversationToken(websiteToken: String): String? =
        settings.getStringOrNull(key(websiteToken))

    fun saveConversationToken(websiteToken: String, token: String) {
        settings.putString(key(websiteToken), token)
    }

    private fun key(websiteToken: String) = "cw_conversation_$websiteToken"
}
