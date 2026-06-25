package com.chatwoot.android.sdk

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chatwoot.android.sdk.style.DefaultStyle
import com.chatwoot.android.sdk.style.StyleConfig
import com.chatwoot.android.sdk.ui.ChatScreen

/**
 * The Chatwoot chat screen — plain, embeddable content.
 *
 * Requires the SDK to be configured first — see [Chatwoot.configure].
 *
 * The SDK deliberately does **not** own a window/sheet. To present the chat in a bottom sheet,
 * dialog, side panel, etc., wrap this composable in the host's own container and size it with
 * [modifier]; the chat then lives in the host's window and inherits its keyboard/inset handling
 * (no separate window to fight). When the host's container already lifts itself above the keyboard
 * (e.g. `adjustResize`), set [StyleConfig.insetsHandledByHost]; otherwise leave it off.
 *
 * @param show Whether the page is visible. When false, nothing is composed and the chat session
 *   (including the websocket) is torn down.
 * @param onFinish Called when the user closes the chat. Map this to your container's dismiss.
 * @param styleConfig Visual customisation; defaults to the stock Chatwoot look.
 * @param modifier Sizes/positions the chat within the host container. Defaults to filling the
 *   parent; pass e.g. `Modifier.fillMaxWidth().fillMaxHeight(0.9f)` for a bottom sheet.
 */
@Composable
public fun ChatPage(
    show: Boolean,
    onFinish: () -> Unit,
    styleConfig: StyleConfig = DefaultStyle,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    if (!show) return
    ChatScreen(onFinish = onFinish, style = styleConfig, modifier = modifier)
}
