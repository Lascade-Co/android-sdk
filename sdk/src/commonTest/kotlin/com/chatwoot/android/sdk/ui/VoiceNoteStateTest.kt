package com.chatwoot.android.sdk.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatwoot.android.sdk.voice.MicAccess
import com.chatwoot.android.sdk.voice.VoiceNoteClip
import com.chatwoot.android.sdk.voice.VoiceNoteRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceNoteStateTest {
    @Test
    fun voiceActionOnlyWithProviderAndEmptyText() {
        assertFalse(shouldShowVoiceButton(false, ""))
        assertFalse(shouldShowVoiceButton(true, "hello"))
        assertTrue(shouldShowVoiceButton(true, ""))
    }

    @Test
    fun requestStartsOnlyAfterGrant() {
        val recorder = FakeRecorder(MicAccess.Askable)
        val state = VoiceNoteState(recorder, {}) { 0L }
        state.tapMic()
        assertEquals(1, recorder.requests)
        assertFalse(state.recording)
        recorder.access = MicAccess.Granted
        state.onAccessChanged(recorder.access)
        assertTrue(state.recording)
        assertEquals(1, recorder.starts)
        state.onAccessChanged(recorder.access)
        assertEquals(1, recorder.starts)
    }

    @Test
    fun sameAskableStateAfterPromptClearsAutoStart() {
        val recorder = FakeRecorder(MicAccess.Askable)
        val state = VoiceNoteState(recorder, {}) { 0L }
        state.tapMic()
        state.onAccessChanged(MicAccess.Askable)
        recorder.access = MicAccess.Granted
        state.onAccessChanged(recorder.access)
        assertFalse(state.recording)
        assertEquals(0, recorder.starts)
    }

    @Test
    fun blockedAccessShowsSettingsAndGrantHidesNote() {
        val recorder = FakeRecorder(MicAccess.Blocked)
        val state = VoiceNoteState(recorder, {}) { 0L }
        state.tapMic()
        assertTrue(state.showAccessNote)
        assertFalse(state.recording)
        state.openSettings()
        assertEquals(1, recorder.settingsOpens)
        recorder.access = MicAccess.Granted
        state.onAccessChanged(recorder.access)
        assertFalse(state.showAccessNote)
        assertFalse(state.recording)
    }

    @Test
    fun failedStartLeavesNoNewState() {
        val recorder = FakeRecorder(MicAccess.Granted).also { it.canStart = false }
        val state = VoiceNoteState(recorder, {}) { 0L }
        state.tapMic()
        assertFalse(state.recording)
        assertEquals(0L, state.elapsedMs)
    }

    @Test
    fun capStopsAndSendsOnce() = runTest {
        val recorder = FakeRecorder(MicAccess.Granted)
        var now = 0L
        val clips = mutableListOf<VoiceNoteClip>()
        val state = VoiceNoteState(recorder, clips::add) { now }
        state.tapMic()
        now = VOICE_NOTE_MAX_DURATION_MS - 1
        state.tick()
        assertTrue(state.recording)
        now++
        state.tick()
        assertFalse(state.recording)
        assertEquals(1, recorder.stops)
        assertEquals(1, clips.size)
        state.tick()
        state.send()
        assertEquals(1, recorder.stops)
    }

    @Test
    fun nullAndEmptyClipsSendNothing() = runTest {
        val recorder = FakeRecorder(MicAccess.Granted)
        val clips = mutableListOf<VoiceNoteClip>()
        val state = VoiceNoteState(recorder, clips::add) { 0L }
        recorder.nextClip = null
        state.tapMic()
        state.send()
        recorder.nextClip = VoiceNoteClip("empty.m4a", "audio/mp4", byteArrayOf())
        state.tapMic()
        state.send()
        assertTrue(clips.isEmpty())
    }

    @Test
    fun discardAndSecondSendCannotDuplicate() = runTest {
        val recorder = FakeRecorder(MicAccess.Granted)
        val clips = mutableListOf<VoiceNoteClip>()
        val state = VoiceNoteState(recorder, clips::add) { 0L }
        state.tapMic()
        state.discard()
        assertFalse(state.recording)
        assertEquals(1, recorder.cancels)
        state.send()
        assertEquals(0, recorder.stops)

        val gate = CompletableDeferred<Unit>()
        recorder.stopGate = gate
        state.tapMic()
        val first = async(start = CoroutineStart.UNDISPATCHED) { state.send() }
        assertFalse(state.recording)
        state.send()
        assertEquals(1, recorder.stops)
        gate.complete(Unit)
        first.await()
        assertEquals(1, clips.size)
    }

    @Test
    fun discardDuringStopSuppressesClip() = runTest {
        val recorder = FakeRecorder(MicAccess.Granted)
        val clips = mutableListOf<VoiceNoteClip>()
        val state = VoiceNoteState(recorder, clips::add) { 0L }
        val gate = CompletableDeferred<Unit>()
        recorder.stopGate = gate
        state.tapMic()
        val send = async(start = CoroutineStart.UNDISPATCHED) { state.send() }
        state.discard()
        gate.complete(Unit)
        send.await()
        assertTrue(clips.isEmpty())
    }

    @Test
    fun failedStopDiscardsTakeAndSendsNothing() = runTest {
        val recorder = FakeRecorder(MicAccess.Granted).also { it.failStop = true }
        val clips = mutableListOf<VoiceNoteClip>()
        val state = VoiceNoteState(recorder, clips::add) { 0L }
        state.tapMic()
        state.send()
        assertFalse(state.recording)
        assertFalse(state.sending)
        assertEquals(1, recorder.cancels)
        assertTrue(clips.isEmpty())
    }
}

private class FakeRecorder(initialAccess: MicAccess) : VoiceNoteRecorder {
    override var access: MicAccess by mutableStateOf(initialAccess)
    override val interruptions: Flow<Unit> = emptyFlow()
    var canStart = true
    var nextClip: VoiceNoteClip? = VoiceNoteClip("note.m4a", "audio/mp4", byteArrayOf(1))
    var stopGate: CompletableDeferred<Unit>? = null
    var failStop = false
    var requests = 0
    var settingsOpens = 0
    var starts = 0
    var stops = 0
    var cancels = 0
    override fun requestAccess() { requests++ }
    override fun openSettings() { settingsOpens++ }
    override fun start(): Boolean { starts++; return canStart }
    override suspend fun stop(): VoiceNoteClip? {
        stops++
        stopGate?.await()
        if (failStop) error("capture failed")
        return nextClip
    }
    override fun cancel() { cancels++ }
}
