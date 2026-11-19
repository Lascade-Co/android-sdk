package com.chatwoot.android.sdk.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

@Composable
public actual fun rememberVoiceNoteRecorder(): VoiceNoteRecorder {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember(context.applicationContext) { AndroidVoiceNoteRecorder(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        recorder.onPermissionResult(granted)
    }

    SideEffect {
        recorder.activity = activity
        recorder.launcher = launcher
        recorder.refreshAccess()
    }
    DisposableEffect(lifecycleOwner, recorder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recorder.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.cancel()
            recorder.launcher = null
            recorder.activity = null
        }
    }
    return recorder
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class AndroidVoiceNoteRecorder(private val context: Context) : VoiceNoteRecorder {
    private val preferences = context.getSharedPreferences("chatwoot_sdk_voice", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val interruptionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    var activity: Activity? = null
    var launcher: ActivityResultLauncher<String>? = null

    override var access: MicAccess by mutableStateOf(MicAccess.Askable)
        private set

    override val interruptions: Flow<Unit> = interruptionEvents

    init {
        refreshAccess()
    }

    fun refreshAccess() {
        access = androidMicAccess(
            granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            askedBefore = preferences.getBoolean("mic_permission_asked", false),
            rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true,
        )
    }

    override fun requestAccess() {
        refreshAccess()
        if (access != MicAccess.Askable || launcher == null) return
        preferences.edit().putBoolean("mic_permission_asked", true).apply()
        launcher?.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun onPermissionResult(granted: Boolean) {
        access = androidMicAccess(
            granted = granted,
            askedBefore = true,
            rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true,
        )
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun start(): Boolean {
        refreshAccess()
        if (access != MicAccess.Granted) return false
        cancel()
        val file = runCatching { File.createTempFile("cw_voice_", ".m4a", context.cacheDir) }.getOrNull()
            ?: return false
        var created: MediaRecorder? = null
        val recorder = try {
            @Suppress("DEPRECATION")
            val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            created = instance
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                setOnErrorListener { _, _, _ -> interruptionEvents.tryEmit(Unit) }
                prepare()
                start()
            }
        } catch (_: Exception) {
            runCatching { created?.release() }
            file.delete()
            return false
        }
        mediaRecorder = recorder
        outputFile = file
        if (!registerSilencingCallback()) {
            cancel()
            return false
        }
        return true
    }

    override suspend fun stop(): VoiceNoteClip? {
        val recorder = mediaRecorder ?: return null
        val file = outputFile
        mediaRecorder = null
        outputFile = null
        unregisterSilencingCallback()
        val stopped = runCatching { recorder.stop() }.isSuccess
        runCatching { recorder.release() }
        if (!stopped || file == null) {
            file?.delete()
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
                if (bytes.isEmpty()) null else VoiceNoteClip(file.name, "audio/mp4", bytes)
            } finally {
                file.delete()
            }
        }
    }

    override fun cancel() {
        unregisterSilencingCallback()
        mediaRecorder?.let { recorder ->
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun registerSilencingCallback(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    mediaRecorder?.activeRecordingConfiguration?.isClientSilenced == true
                ) {
                    interruptionEvents.tryEmit(Unit)
                }
            }
        }
        if (runCatching {
                audioManager.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
            }.isFailure
        ) return false
        recordingCallback = callback
        return true
    }

    private fun unregisterSilencingCallback() {
        recordingCallback?.let { runCatching { audioManager.unregisterAudioRecordingCallback(it) } }
        recordingCallback = null
    }
}
