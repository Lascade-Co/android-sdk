package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.style.StyleConfig

private val HeaderCloseButtonSize = 50.dp

@Composable
internal fun ChatHeader(style: StyleConfig, connected: Boolean, onFinish: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.primaryColor)
            // Fill behind the status bar (no gap above the header); content sits below it. Skip this
            // inset when the host already applies it (insetsHandledByHost) to avoid double padding.
            .then(if (style.insetsHandledByHost) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars))
            .padding(start = 16.dp, end = 0.dp, top = 0.dp, bottom = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = HeaderCloseButtonSize + 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = style.title,
                color = style.onPrimaryColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Start,
                fontFamily = style.titleFontFamily ?: style.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!connected) {
                Text(
                    text = "connecting…",
                    color = style.onPrimaryColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = style.fontFamily,
                )
            }
        }
        IconButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(HeaderCloseButtonSize),
        ) {
            val cancelIcon = style.cancelIcon
            if (cancelIcon != null) {
                cancelIcon()
            } else {
                Text(text = "✕", color = style.onPrimaryColor, fontSize = 18.sp)
            }
        }
    }
}

@Composable
internal fun ErrorBanner(error: String, style: StyleConfig, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x4D5A1A1A))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = error,
            color = Color(0xFFFF8A80),
            fontSize = 13.sp,
            fontFamily = style.fontFamily,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Text(text = "✕", color = Color(0xFFFF8A80), fontSize = 14.sp)
        }
    }
}
