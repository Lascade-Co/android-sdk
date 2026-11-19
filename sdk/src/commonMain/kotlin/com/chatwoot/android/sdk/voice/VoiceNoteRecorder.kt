package com.chatwoot.android.sdk.voice

import kotlinx.coroutines.flow.Flow

/** Current microphone access. [Blocked] means the system will not prompt again. */
public enum class MicAccess { Granted, Askable, Blocked }

/** An encoded voice note ready for the normal attachment upload path. */
public class VoiceNoteClip(
    public val fileName: String,
    public val mimeType: String,
    public val bytes: ByteArray,
)

/** Platform recording contract supplied by the optional voice module. */
public interface VoiceNoteRecorder {
    /** Compose-observable access state, refreshed when the app resumes. */
    public val access: MicAccess

    /** Ask for access using the platform system prompt. */
    public fun requestAccess()

    /** Open this application's system settings page. */
    public fun openSettings()

    /** Start a new clip, returning false if capture could not begin. */
    public fun start(): Boolean

    /** Finish the clip; null means that there is nothing usable to send. */
    public suspend fun stop(): VoiceNoteClip?

    /** Discard the active clip and remove its temporary file. */
    public fun cancel()

    /** Emits when capture becomes invalid because of an audio interruption. */
    public val interruptions: Flow<Unit>
}
