package com.chatwoot.android.sdk

import platform.Foundation.NSBundle
import platform.Foundation.NSNumber

internal actual fun platformDefaultConfig(): ChatwootConfig? {
    val bundle = NSBundle.mainBundle
    val baseUrl = bundle.objectForInfoDictionaryKey("ChatwootBaseUrl") as? String ?: return null
    val token = bundle.objectForInfoDictionaryKey("ChatwootWebsiteToken") as? String ?: return null
    val voiceNotes = (bundle.objectForInfoDictionaryKey("ChatwootVoiceNotesEnabled") as? NSNumber)?.boolValue ?: false
    return ChatwootConfig(baseUrl, token, voiceNotes)
}
