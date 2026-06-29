package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.data.AttachmentType
import com.chatwoot.android.sdk.data.ChatMessage
import com.chatwoot.android.sdk.style.StyleConfig

private val BubbleCorner = 22.dp

@Composable
internal fun MessageBubble(message: ChatMessage, style: StyleConfig) {
    if (message.isActivity) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.content,
                color = style.secondaryTextColor,
                fontSize = 12.sp,
                fontFamily = style.fontFamily,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        return
    }

    val fromContact = message.fromContact
    val hasText = message.content.isNotBlank()
    val hasAttachments = message.attachments.isNotEmpty() || message.pending || message.failed
    val bareMedia = !hasText && hasAttachments && (message.pending || message.failed ||
        message.attachments.all { it.type == AttachmentType.Image || it.type == AttachmentType.Video })

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromContact) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (bareMedia) {
            AttachmentContent(message = message, style = style, onContact = fromContact)
            return@Box
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(BubbleCorner))
                .background(if (fromContact) style.outgoingBubbleColor else style.incomingBubbleColor)
                .dashedBorder(1.dp, style.bubbleBorderColor, BubbleCorner)
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val time = style.timeFormatter?.invoke(message.createdAt)
                if (hasText) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val contentColor = if (fromContact) style.onOutgoingBubbleColor else style.onIncomingBubbleColor
                        val annotatedContent = remember(message.content, contentColor) {
                            linkify(message.content, contentColor)
                        }
                        Text(
                            text = annotatedContent,
                            color = contentColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFamily = style.fontFamily,
                        )
                        time?.let {
                            Text(
                                text = it,
                                color = style.secondaryTextColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = style.fontFamily,
                                modifier = Modifier.align(Alignment.Bottom),
                            )
                        }
                    }
                }
                if (hasAttachments) {
                    AttachmentContent(message = message, style = style, onContact = fromContact)
                }
                if (!hasText && time != null) {
                    Text(
                        text = time,
                        color = style.secondaryTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = style.fontFamily,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

private val UrlRegex = Regex("""(?:https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)

private fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in UrlRegex.findAll(text)) {
        append(text.substring(index, match.range.first))
        val raw = match.value
        val url = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
        val href = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        withLink(
            LinkAnnotation.Url(
                href,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            ),
        ) {
            append(url)
        }
        append(raw.substring(url.length))
        index = match.range.last + 1
    }
    append(text.substring(index))
}
