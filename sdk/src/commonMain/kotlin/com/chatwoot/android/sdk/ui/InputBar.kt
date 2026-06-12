package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.style.StyleConfig

@Composable
internal fun InputBar(style: StyleConfig, enabled: Boolean, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty() || !enabled) return
        onSend(value)
        text = ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.surfaceColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            modifier = Modifier.weight(1f).padding(end = 8.dp),
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
