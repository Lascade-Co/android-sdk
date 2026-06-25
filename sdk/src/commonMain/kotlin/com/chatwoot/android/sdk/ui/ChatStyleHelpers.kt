package com.chatwoot.android.sdk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.style.StyleConfig
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rounded dashed outline drawn on top of the content (matches the host "Journey AI" look). */
internal fun Modifier.dashedBorder(strokeWidth: Dp, color: Color, cornerRadius: Dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
            ),
        )
    }

private val typingDotColors = listOf(
    Color(0xFF00A3FF),
    Color(0xFF04FA9C),
    Color(0xFFB0FA04),
)

/** Three sine-wave dots after a label — shown while an agent is typing. */
@Composable
internal fun TypingIndicator(style: StyleConfig, label: String = "typing") {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
        label = "phase",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = style.secondaryTextColor, fontSize = 13.sp, fontFamily = style.fontFamily)
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            typingDotColors.forEachIndexed { index, color ->
                val offsetY = (sin(phase + index * (2f * PI.toFloat() / 3f)) * 6f).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetY) }
                        .size(5.5.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

/**
 * Circular send button. Renders [StyleConfig.sendIcon] when the host supplies one, otherwise a
 * canvas-drawn up-arrow (no icon/resource dependency). Dims while [enabled] is false.
 */
@Composable
internal fun SendButton(enabled: Boolean, style: StyleConfig, onClick: () -> Unit) {
    val bg = if (enabled) style.accentColor else style.accentColor.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f),
        contentAlignment = Alignment.Center,
    ) {
        val custom = style.sendIcon
        if (custom != null) {
            custom()
        } else {
            val fg = style.onAccentColor
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.12f
                val top = Offset(w / 2f, h * 0.18f)
                drawLine(fg, Offset(w / 2f, h * 0.82f), top, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fg, Offset(w * 0.27f, h * 0.45f), top, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fg, Offset(w * 0.73f, h * 0.45f), top, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Circular voice-note button — the same white-circle treatment as [SendButton], shown when the
 * input is empty. Renders [StyleConfig.micIcon] when the host supplies one, otherwise a
 * canvas-drawn "voice levels" glyph: three vertical bars with the middle one elongated.
 */
@Composable
internal fun VoiceButton(enabled: Boolean, style: StyleConfig, onClick: () -> Unit) {
    val bg = if (enabled) style.accentColor else style.accentColor.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f),
        contentAlignment = Alignment.Center,
    ) {
        val custom = style.micIcon
        if (custom != null) {
            custom()
        } else {
            val fg = style.onAccentColor
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val mid = h / 2f
                val stroke = w * 0.13f
                val gap = w * 0.27f
                // Two short side bars and a tall middle bar — reads as a voice / audio-levels mark.
                val shortHalf = h * 0.16f
                val tallHalf = h * 0.36f
                drawLine(fg, Offset(cx - gap, mid - shortHalf), Offset(cx - gap, mid + shortHalf), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fg, Offset(cx, mid - tallHalf), Offset(cx, mid + tallHalf), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fg, Offset(cx + gap, mid - shortHalf), Offset(cx + gap, mid + shortHalf), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

/** Centered "Today / Yesterday / date" chip shown above the first message of each day. */
@Composable
internal fun DateSeparator(text: String, style: StyleConfig) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xB2FFFFFF),
            fontSize = 12.sp,
            fontFamily = style.fontFamily,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF2B2C30))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
