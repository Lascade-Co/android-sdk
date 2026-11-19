package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.data.PickedFile
import com.chatwoot.android.sdk.data.mimeTypeForExtension
import com.chatwoot.android.sdk.style.StyleConfig
import com.chatwoot.android.sdk.voice.VoiceNoteRecorder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
internal fun InputBar(
    style: StyleConfig,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onPickAttachment: (PickedFile) -> Unit,
    voiceNoteRecorder: VoiceNoteRecorder? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val voiceState = voiceNoteRecorder?.let { recorder ->
        remember(recorder) {
            VoiceNoteState(recorder, onClip = { clip ->
                onPickAttachment(PickedFile(clip.fileName, clip.mimeType, clip.bytes))
            })
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(voiceState, voiceNoteRecorder?.access) {
        voiceNoteRecorder?.let { voiceState?.onAccessChanged(it.access) }
    }
    LaunchedEffect(voiceState) {
        voiceNoteRecorder?.interruptions?.collect { voiceState?.discard() }
    }
    LaunchedEffect(voiceState, voiceState?.recording) {
        while (voiceState?.recording == true) {
            delay(200)
            voiceState.tick()
        }
    }
    DisposableEffect(lifecycleOwner, voiceState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voiceState?.discard()
            if (event == Lifecycle.Event.ON_RESUME) {
                voiceNoteRecorder?.let { voiceState?.onAccessChanged(it.access) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceState?.discard()
        }
    }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty() || !enabled) return
        onSend(value)
        text = ""
    }

    fun consume(file: PlatformFile?) {
        file ?: return
        scope.launch {
            val bytes = file.readBytes()
            val mime = file.mimeType()?.let { "${it.primaryType}/${it.subtype}" }
                ?: mimeTypeForExtension(file.extension)
            onPickAttachment(PickedFile(file.name, mime, bytes))
        }
    }

    val mediaPicker = rememberFilePickerLauncher(type = FileKitType.ImageAndVideo) { consume(it) }
    val filePicker = rememberFilePickerLauncher(type = FileKitType.File()) { consume(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.surfaceColor)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        if (voiceState?.showAccessNote == true) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    text = style.micAccessOffMessage,
                    color = style.secondaryTextColor,
                    fontSize = 13.sp,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = voiceState::openSettings) {
                    Text(style.openSettingsLabel, color = style.accentColor, fontFamily = style.fontFamily)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = style.inputMinHeight)
                .border(1.dp, style.inputBorderColor, style.inputShape)
                .clip(style.inputShape)
                .background(style.inputFieldColor)
                .padding(start = 6.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (voiceState?.recording == true) {
                Box(
                    modifier = Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape)
                        .background(Color(0xFFE53935)),
                )
                Text(
                    text = formatVoiceElapsed(voiceState.elapsedMs),
                    color = style.textColor,
                    fontSize = 15.sp,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                IconButton(
                    onClick = voiceState::discard,
                    modifier = Modifier.semantics { contentDescription = style.voiceNoteCancelLabel },
                ) {
                    Text(text = "✕", color = style.secondaryTextColor, fontSize = 20.sp)
                }
                SendButton(
                    enabled = enabled,
                    style = style,
                    contentDescription = style.voiceNoteSendLabel,
                    onClick = { scope.launch { voiceState.send() } },
                )
                return@Row
            }

            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    val attachmentIcon = style.attachmentIcon
                    if (attachmentIcon != null) {
                        attachmentIcon()
                    } else {
                        Text(text = "+", color = style.secondaryTextColor, fontSize = 24.sp, fontFamily = style.fontFamily)
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Photo or video") },
                        onClick = { menuOpen = false; mediaPicker.launch() },
                    )
                    DropdownMenuItem(
                        text = { Text("File") },
                        onClick = { menuOpen = false; filePicker.launch() },
                    )
                }
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                maxLines = 3,
                textStyle = TextStyle(color = style.textColor, fontSize = 16.sp, fontFamily = style.fontFamily),
                cursorBrush = SolidColor(style.accentColor),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = style.inputHint,
                            color = style.secondaryTextColor,
                            fontSize = 16.sp,
                            fontFamily = style.fontFamily,
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )

            if (shouldShowVoiceButton(voiceState != null, text)) {
                voiceState?.let { VoiceButton(enabled = enabled, style = style, onClick = it::tapMic) }
            } else {
                SendButton(enabled = enabled && text.isNotBlank(), style = style, onClick = ::submit)
            }
        }
    }
}

private fun formatVoiceElapsed(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
