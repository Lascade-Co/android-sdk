package com.chatwoot.android.sdk.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual customisation for [com.chatwoot.android.sdk.ChatPage]. All theming flows through
 * this one object; pass a copy of [DefaultStyle] with overrides.
 *
 * The stock look is a dark, translucent-bubble theme with a vertical-gradient backdrop, a
 * dashed outline around each bubble, and a rounded "pill" input bar with a circular send
 * button — designed to sit inside a dark host UI.
 *
 * @property primaryColor Header bar fill.
 * @property onPrimaryColor Content (title, close icon) drawn on top of [primaryColor].
 * @property backgroundColor Solid fallback for the message list when [backgroundGradient] is empty.
 * @property backgroundGradient Top→bottom gradient painted behind the message list. Empty = use
 *   [backgroundColor].
 * @property surfaceColor Background of the input-bar area at the bottom.
 * @property outgoingBubbleColor Bubble fill for messages sent by the contact (the app user).
 * @property onOutgoingBubbleColor Text color inside outgoing bubbles.
 * @property incomingBubbleColor Bubble fill for agent/bot messages.
 * @property onIncomingBubbleColor Text color inside incoming bubbles.
 * @property bubbleBorderColor Dashed outline drawn around every bubble; use [Color.Transparent]
 *   to disable.
 * @property textColor Primary text color, used for the message input.
 * @property secondaryTextColor De-emphasised text: input placeholder, typing indicator,
 *   activity messages (e.g. "Conversation was resolved").
 * @property inputFieldColor Fill of the rounded input "pill".
 * @property inputBorderColor Outline of the input pill.
 * @property inputShape Shape clipping the input pill.
 * @property inputMinHeight Resting height of the input "pill". It still grows for multi-line input.
 * @property inputHint Placeholder text shown in the message field while it is empty.
 * @property accentColor Fill of the circular send button (and the text cursor).
 * @property onAccentColor Content (the send arrow) drawn on top of [accentColor].
 * @property bubbleShape Shape clipping every message bubble.
 * @property fontFamily Font applied to all body text. Null = platform default.
 * @property titleFontFamily Font for the header title. Null = falls back to [fontFamily].
 * @property cancelIcon Optional header close icon. Null = default close glyph.
 * @property attachmentIcon Optional override for the attachment ("+") button inside the input
 *   pill. Null = a default "+" glyph.
 * @property sendIcon Optional override for the glyph inside the circular send button. Null = a
 *   built-in up-arrow drawn on a canvas.
 * @property micIcon Optional override for the glyph inside the circular voice-note button (shown
 *   when the input is empty and mic permission is available). Null = a built-in "voice levels"
 *   glyph (three vertical bars, the middle one elongated) drawn on a canvas, matching the send
 *   button's white-circle treatment.
 * @property insetsHandledByHost Set true when the host's View layer already applies the system-bar
 *   and keyboard insets — e.g. an Activity using `adjustResize` with a `fitsSystemWindows` root. The
 *   SDK then consumes no window insets of its own (neither the IME on the input bar nor the status
 *   bar on the header), avoiding double insets. Leave false (default) for edge-to-edge Android hosts
 *   and iOS, where Compose applies the insets.
 * @property title Header title of the chat page.
 * @property timeFormatter Optional per-message time formatter. Null = no time label.
 * @property dateSeparatorFormatter Optional day-separator formatter. Null = no day separators.
 */
public data class StyleConfig(
    val primaryColor: Color = Color(0xFF252A3D),
    val onPrimaryColor: Color = Color.White,
    val backgroundColor: Color = Color(0xFF15161A),
    val backgroundGradient: List<Color> = listOf(Color(0xFF252A3D), Color(0xFF15161A)),
    val surfaceColor: Color = Color(0xFF131518),
    val outgoingBubbleColor: Color = Color(0x4D165324),
    val onOutgoingBubbleColor: Color = Color(0xCCFFFFFF),
    val incomingBubbleColor: Color = Color(0x4D15161A),
    val onIncomingBubbleColor: Color = Color(0xCCFFFFFF),
    val bubbleBorderColor: Color = Color(0x1FFFFFFF),
    val textColor: Color = Color(0xB3FFFFFF),
    val secondaryTextColor: Color = Color(0x99FFFFFF),
    val inputFieldColor: Color = Color(0xBF0D1219),
    val inputBorderColor: Color = Color(0x1AFFFFFF),
    val inputShape: Shape = RoundedCornerShape(34.dp),
    val inputMinHeight: Dp = 52.dp,
    val inputHint: String = "Message…",
    val accentColor: Color = Color.White,
    val onAccentColor: Color = Color(0xFF121922),
    val bubbleShape: Shape = RoundedCornerShape(22.dp),
    val fontFamily: FontFamily? = null,
    val titleFontFamily: FontFamily? = null,
    val cancelIcon: (@Composable () -> Unit)? = null,
    val attachmentIcon: (@Composable () -> Unit)? = null,
    val sendIcon: (@Composable () -> Unit)? = null,
    val micIcon: (@Composable () -> Unit)? = null,
    val insetsHandledByHost: Boolean = false,
    val title: String = "Chat with us",
    val timeFormatter: ((epochSeconds: Long) -> String)? = null,
    val dateSeparatorFormatter: ((epochSeconds: Long) -> String)? = null,
)

/** The stock Chatwoot look. */
public val DefaultStyle: StyleConfig = StyleConfig()
