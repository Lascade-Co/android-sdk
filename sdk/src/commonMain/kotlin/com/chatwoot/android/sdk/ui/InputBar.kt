package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.data.PickedFile
import com.chatwoot.android.sdk.data.mimeTypeForExtension
import com.chatwoot.android.sdk.media.rememberAudioRecorder
import com.chatwoot.android.sdk.media.rememberMicPermission
import com.chatwoot.android.sdk.style.StyleConfig
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun InputBar(
    style: StyleConfig,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onPickAttachment: (PickedFile) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val recorder = rememberAudioRecorder()
    val mic = rememberMicPermission()

    LaunchedEffect(recording) {
        if (recording) {
            elapsedMs = 0
            while (true) {
                delay(200)
                elapsedMs += 200
            }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.surfaceColor)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            if (recording) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                )
                Text(
                    text = formatElapsed(elapsedMs),
                    color = style.textColor,
                    fontSize = 15.sp,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                IconButton(onClick = { recording = false; recorder.cancel() }) {
                    Text(text = "✕", color = style.secondaryTextColor, fontSize = 20.sp)
                }
                SendButton(enabled = enabled, style = style, onClick = {
                    recording = false
                    scope.launch { recorder.stop()?.let(onPickAttachment) }
                })
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

            when {
                text.isNotBlank() -> SendButton(enabled = enabled, style = style, onClick = ::submit)
                else -> VoiceButton(
                    enabled = enabled && !mic.denied,
                    style = style,
                    onClick = { if (mic.granted) recorder.start().also { recording = true } else mic.request() },
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
