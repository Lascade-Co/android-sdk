package com.chatwoot.android.sdk.voice

import androidx.compose.runtime.Composable

/** Creates and remembers the platform voice-note recorder for a chat page. */
@Composable
public expect fun rememberVoiceNoteRecorder(): VoiceNoteRecorder
