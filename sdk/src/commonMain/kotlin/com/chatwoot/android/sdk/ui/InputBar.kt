package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.data.PickedFile
import com.chatwoot.android.sdk.data.mimeTypeForExtension
import com.chatwoot.android.sdk.style.StyleConfig
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
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
    val scope = rememberCoroutineScope()

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
            onPickAttachment(PickedFile(file.name, mimeTypeForExtension(file.extension), bytes))
        }
    }

    val mediaPicker = rememberFilePickerLauncher(type = FileKitType.ImageAndVideo) { consume(it) }
    val filePicker = rememberFilePickerLauncher(type = FileKitType.File()) { consume(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.surfaceColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Text(text = "📎", color = style.secondaryTextColor, fontSize = 20.sp)
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
            textStyle = TextStyle(color = style.textColor, fontSize = 15.sp),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(text = "Type your message…", color = style.secondaryTextColor, fontSize = 15.sp)
                }
                innerTextField()
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = ::submit, enabled = enabled && text.isNotBlank()) {
            Text(
                text = "➤",
                color = if (text.isNotBlank()) style.primaryColor else style.secondaryTextColor,
                fontSize = 20.sp,
            )
        }
    }
}
