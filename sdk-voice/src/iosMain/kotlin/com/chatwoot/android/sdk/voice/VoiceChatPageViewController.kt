package com.chatwoot.android.sdk.voice

import androidx.compose.ui.window.ComposeUIViewController
import com.chatwoot.android.sdk.ChatPage
import com.chatwoot.android.sdk.style.DefaultStyle
import com.chatwoot.android.sdk.style.StyleConfig
import platform.UIKit.UIViewController

/** UIKit demo entry point that installs the opt-in voice-note recorder. */
public fun VoiceChatPageViewController(
    onFinish: () -> Unit = {},
    styleConfig: StyleConfig = DefaultStyle,
): UIViewController = ComposeUIViewController {
    ChatPage(show = true, onFinish = onFinish, styleConfig = styleConfig, voiceNoteRecorder = rememberVoiceNoteRecorder())
}
