package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.chatwoot.android.sdk.ChatViewModel
import com.chatwoot.android.sdk.style.StyleConfig

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreen(
    onFinish: () -> Unit,
    style: StyleConfig,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val dateLabels = remember(state.messages, style.dateSeparatorFormatter) {
        style.dateSeparatorFormatter?.let { format ->
            state.messages.map { format(it.createdAt) }
        } ?: emptyList()
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    val background = if (style.backgroundGradient.size >= 2) {
        Brush.verticalGradient(style.backgroundGradient)
    } else {
        Brush.verticalGradient(listOf(style.backgroundColor, style.backgroundColor))
    }

    Column(
        modifier = modifier
            .background(background)
            .then(
                if (style.insetsHandledByHost) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                },
            ),
    ) {
        ChatHeader(style = style, connected = state.connected, onFinish = onFinish)

        state.error?.let { error ->
            ErrorBanner(error = error, style = style, onDismiss = viewModel::dismissError)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading) {
                CircularProgressIndicator(
                    color = style.accentColor,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.messages.forEachIndexed { index, message ->
                        val label = dateLabels.getOrNull(index)
                        if (label != null && label != dateLabels.getOrNull(index - 1)) {
                            stickyHeader(key = "date_${message.id}") { DateSeparator(text = label, style = style) }
                        }
                        item(key = message.id) { MessageBubble(message = message, style = style) }
                    }
                }
            }
        }

        if (state.agentTyping) {
            TypingIndicator(style = style)
        }

        InputBar(
            style = style,
            enabled = !state.loading,
            onSend = viewModel::send,
            onPickAttachment = viewModel::sendAttachment,
        )
    }
}
