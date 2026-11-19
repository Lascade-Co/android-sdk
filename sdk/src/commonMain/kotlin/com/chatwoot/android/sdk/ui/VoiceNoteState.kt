package com.chatwoot.android.sdk.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatwoot.android.sdk.voice.MicAccess
import com.chatwoot.android.sdk.voice.VoiceNoteClip
import com.chatwoot.android.sdk.voice.VoiceNoteRecorder
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

/** Maximum length of a contact voice note before it is sent automatically. */
internal const val VOICE_NOTE_MAX_DURATION_MS: Long = 120_000L

internal fun shouldShowVoiceButton(hasRecorder: Boolean, text: String): Boolean =
    hasRecorder && text.isEmpty()

/** UI-independent interaction state; [nowMs] can be controlled in tests. */
internal class VoiceNoteState(
    private val recorder: VoiceNoteRecorder,
    private val onClip: (VoiceNoteClip) -> Unit,
    private val nowMs: () -> Long = TimeSource.Monotonic.markNow().let { origin ->
        { origin.elapsedNow().inWholeMilliseconds }
    },
) {
    var recording: Boolean by mutableStateOf(false)
        private set
    var sending: Boolean by mutableStateOf(false)
        private set
    var showAccessNote: Boolean by mutableStateOf(false)
        private set
    var elapsedMs: Long by mutableStateOf(0L)
        private set

    private var waitingForGrant = false
    private var beganAtMs = 0L
    private var generation = 0L
    private var capSendRequested = false

    fun tapMic() {
        if (recording || sending) return
        when (recorder.access) {
            MicAccess.Granted -> begin()
            MicAccess.Askable -> {
                waitingForGrant = true
                recorder.requestAccess()
            }
            MicAccess.Blocked -> showAccessNote = true
        }
    }

    fun onAccessChanged(access: MicAccess) {
        if (access == MicAccess.Granted) {
            showAccessNote = false
            if (waitingForGrant) {
                waitingForGrant = false
                begin()
            }
        } else if (access == MicAccess.Blocked) {
            waitingForGrant = false
        }
    }

    fun openSettings() = recorder.openSettings()

    private fun begin() {
        if (!recorder.start()) return
        generation++
        beganAtMs = nowMs()
        elapsedMs = 0L
        capSendRequested = false
        recording = true
    }

    /** Reports the cap once so the caller can launch [send] outside its timer effect. */
    fun tick(): Boolean {
        if (!recording) return false
        elapsedMs = (nowMs() - beganAtMs).coerceAtLeast(0L)
        if (elapsedMs < VOICE_NOTE_MAX_DURATION_MS || capSendRequested) return false
        capSendRequested = true
        return true
    }

    suspend fun send() {
        if (!recording || sending) return
        recording = false
        sending = true
        val sendGeneration = generation
        try {
            val clip = recorder.stop()
            if (sendGeneration == generation && clip != null && clip.bytes.isNotEmpty()) onClip(clip)
        } catch (cancelled: CancellationException) {
            runCatching { recorder.cancel() }
            throw cancelled
        } catch (_: Exception) {
            runCatching { recorder.cancel() }
        } finally {
            sending = false
            elapsedMs = 0L
        }
    }

    fun discard() {
        waitingForGrant = false
        generation++
        if (recording || sending) recorder.cancel()
        recording = false
        capSendRequested = false
        elapsedMs = 0L
    }
}
