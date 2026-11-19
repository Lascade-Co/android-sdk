package com.chatwoot.android.sdk.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@Composable
public actual fun rememberVoiceNoteRecorder(): VoiceNoteRecorder {
    val recorder = remember { IosVoiceNoteRecorder() }
    DisposableEffect(recorder) {
        val center = NSNotificationCenter.defaultCenter
        val interruptionObserver = center.addObserverForName(
            AVAudioSessionInterruptionNotification,
            null,
            null,
        ) { notification ->
            val type = notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber
            if (type?.unsignedIntegerValue == AVAudioSessionInterruptionTypeBegan) recorder.onInterruption()
        }
        val foregroundObserver = center.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            null,
        ) { recorder.refreshAccess() }
        onDispose {
            recorder.cancel()
            center.removeObserver(interruptionObserver)
            center.removeObserver(foregroundObserver)
        }
    }
    return recorder
}

@OptIn(ExperimentalForeignApi::class)
private class IosVoiceNoteRecorder : VoiceNoteRecorder {
    private val interruptionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var recorder: AVAudioRecorder? = null
    private var fileUrl: NSURL? = null
    private var savedSession: SessionState? = null

    override var access: MicAccess by mutableStateOf(readAccess())
        private set

    override val interruptions: Flow<Unit> = interruptionEvents

    fun refreshAccess() {
        access = readAccess()
    }

    fun onInterruption() {
        if (recorder != null) interruptionEvents.tryEmit(Unit)
    }

    override fun requestAccess() {
        if (access != MicAccess.Askable) return
        val update: (Boolean) -> Unit = { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                access = if (granted) MicAccess.Granted else MicAccess.Blocked
            }
        }
        if (usesAudioApplication()) {
            AVAudioApplication.requestRecordPermissionWithCompletionHandler(update)
        } else {
            AVAudioSession.sharedInstance().requestRecordPermission(update)
        }
    }

    override fun openSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url)
        }
    }

    override fun start(): Boolean {
        refreshAccess()
        if (access != MicAccess.Granted) return false
        cancel()
        val session = AVAudioSession.sharedInstance()
        val previousCategory = session.category ?: return false
        val previousMode = session.mode ?: return false
        savedSession = SessionState(
            previousCategory,
            previousMode,
            session.categoryOptions,
        )
        val activated = runCatching {
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeDefault, 0u, null) &&
                session.setActive(true, null)
        }.getOrDefault(false)
        if (!activated) {
            restoreSession()
            return false
        }

        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + "cw_voice_${NSUUID().UUIDString}.m4a")
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(double = 44_100.0),
            AVNumberOfChannelsKey to NSNumber(int = 1),
            AVEncoderBitRateKey to NSNumber(int = 64_000),
            AVEncoderAudioQualityKey to NSNumber(long = AVAudioQualityHigh),
        )
        val created = runCatching { AVAudioRecorder(url, settings, null) }.getOrNull()
        if (created == null || !runCatching { created.record() }.getOrDefault(false)) {
            deleteFile(url)
            restoreSession()
            return false
        }
        recorder = created
        fileUrl = url
        return true
    }

    override suspend fun stop(): VoiceNoteClip? {
        val active = recorder ?: return null
        val url = fileUrl
        recorder = null
        fileUrl = null
        runCatching { active.stop() }
        restoreSession()
        if (url == null) return null
        return try {
            val data = NSData.dataWithContentsOfURL(url) ?: return null
            val length = data.length.toInt()
            if (length <= 0) return null
            val bytes = ByteArray(length)
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, length.toULong()) }
            VoiceNoteClip(url.lastPathComponent ?: "voice_note.m4a", "audio/mp4", bytes)
        } finally {
            deleteFile(url)
        }
    }

    override fun cancel() {
        runCatching { recorder?.stop() }
        recorder = null
        fileUrl?.let(::deleteFile)
        fileUrl = null
        restoreSession()
    }

    private fun restoreSession() {
        val prior = savedSession ?: return
        savedSession = null
        val session = AVAudioSession.sharedInstance()
        runCatching { session.setCategory(prior.category, prior.mode, prior.options, null) }
        runCatching { session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null) }
    }

    private fun deleteFile(url: NSURL) {
        NSFileManager.defaultManager.removeItemAtURL(url, null)
    }

    private fun readAccess(): MicAccess = if (usesAudioApplication()) {
        when (AVAudioApplication.sharedInstance().recordPermission) {
            AVAudioApplicationRecordPermissionGranted -> MicAccess.Granted
            AVAudioApplicationRecordPermissionDenied -> MicAccess.Blocked
            else -> MicAccess.Askable
        }
    } else {
        when (AVAudioSession.sharedInstance().recordPermission) {
            AVAudioSessionRecordPermissionGranted -> MicAccess.Granted
            AVAudioSessionRecordPermissionDenied -> MicAccess.Blocked
            else -> MicAccess.Askable
        }
    }

    private fun usesAudioApplication(): Boolean =
        (UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0) >= 17
}

private data class SessionState(val category: String, val mode: String, val options: ULong)
