package com.ragclient.android.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.RagClientTheme
import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.ChatRole
import com.ragclient.android.domain.model.RagSourceModel

@Composable
fun ChatScreen(
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenSource = onOpenSource
    )
}

@Composable
private fun ChatContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingText.length, state.error) {
        val last = state.messages.size + if (state.isGenerating) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.messages.isEmpty() && !state.isGenerating) {
                item { EmptyCanvas() }
            }

            items(state.messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onOpenSource = onOpenSource
                )
            }

            if (state.isGenerating) {
                item {
                    StreamingRow(
                        text = state.streamingText,
                        sources = state.streamingSources,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }

        state.error?.let { problem ->
            ErrorBanner(problem, onDismiss = { onIntent(ChatIntent.DismissError) })
        }

        PromptBar(
            value = state.prompt,
            canSend = state.canSend,
            isGenerating = state.isGenerating,
            scopedDocumentId = state.scopedDocumentId,
            onValueChange = { onIntent(ChatIntent.ChangePrompt(it)) },
            onSend = { onIntent(ChatIntent.Send) },
            onStop = { onIntent(ChatIntent.Stop) },
            onClearScope = { onIntent(ChatIntent.ClearScope) }
        )
    }
}

@Composable
private fun EmptyCanvas() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Ask your documents.",
            style = AppType.Display,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Answers cite the exact page they came from.",
            style = AppType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit
) {
    when (message.role) {
        ChatRole.User -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Text(
                    text = message.text,
                    style = AppType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        ChatRole.Assistant -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message.text,
                style = AppType.AnswerBody,
                color = MaterialTheme.colorScheme.onBackground
            )
            SourceRow(sources = message.sources, onOpenSource = onOpenSource)
        }
    }
}

@Composable
private fun StreamingRow(
    text: String,
    sources: List<RagSourceModel>,
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = text.ifBlank { "Thinking" },
                style = AppType.AnswerBody,
                color = MaterialTheme.colorScheme.onBackground
            )
            BlinkingCaret()
        }
        SourceRow(sources = sources, onOpenSource = onOpenSource)
    }
}

/**
 * The answer's trailing block, on/off like a real text caret. Kept in its own
 * composable so the blink only recomposes this `Text`, not the streaming
 * answer above it.
 */
@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CaretCycleMillis, easing = LinearEasing)),
        label = "caret-phase"
    )

    Text(
        text = " ▍",
        style = AppType.AnswerBody,
        color = AppColors.Accent.copy(alpha = if (phase < 0.5f) 1f else 0f)
    )
}

/** 1060 ms cycle = 530 ms on + 530 ms off, the interval text editors use. */
private const val CaretCycleMillis = 1_060

@Composable
private fun SourceRow(
    sources: List<RagSourceModel>,
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit
) {
    if (sources.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        sources.take(4).forEachIndexed { index, source ->
            ElevatedSuggestionChip(
                onClick = {
                    source.pageNumber?.let { page -> onOpenSource(source.documentId, page) }
                },
                label = {
                    Text(
                        text = "S${index + 1} · p${source.pageNumber ?: "?"}",
                        style = AppType.Label
                    )
                }
            )
        }
    }
}

@Composable
private fun ErrorBanner(problem: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = AppShapes.Card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = problem,
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PromptBar(
    value: String,
    canSend: Boolean,
    isGenerating: Boolean,
    scopedDocumentId: String?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClearScope: () -> Unit
) {
    val actionEnabled = canSend || isGenerating

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = AppShapes.PromptBar,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            // No navigationBarsPadding here: this screen is inside the
            // shell's Scaffold, whose content padding already reserves the
            // bottom system bar. Adding it again doubles the gap.
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            if (scopedDocumentId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, top = 10.dp)
                ) {
                    Text(
                        text = "Asking one document only",
                        style = AppType.Label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onClearScope, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Ask all documents",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Ask anything…", style = AppType.Body) },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp, max = 160.dp),
                shape = AppShapes.PromptBar,
                // The send button lives in the field's own trailing slot so the
                // focus outline wraps it; as a sibling of the field it sat
                // outside the border.
                trailingIcon = {
                    IconButton(
                        onClick = { if (isGenerating) onStop() else onSend() },
                        enabled = actionEnabled,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(44.dp)
                            .background(
                                color = if (actionEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isGenerating) {
                                Icons.Filled.Close
                            } else {
                                Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = if (isGenerating) "Stop" else "Send",
                            tint = if (actionEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatWithAnswerPreview() {
    RagClientTheme {
        ChatContent(
            state = ChatState(
                isLoading = false,
                messages = listOf(
                    ChatMessage(1, ChatRole.User, "What does section 3 say?"),
                    ChatMessage(
                        2,
                        ChatRole.Assistant,
                        "Section 3 covers the ingestion pipeline and its retries.",
                        sources = listOf(RagSourceModel("c1", "d1", 3, 0.81f))
                    )
                )
            ),
            onIntent = {},
            onOpenSource = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatStreamingPreview() {
    RagClientTheme {
        ChatContent(
            state = ChatState(
                isLoading = false,
                isGenerating = true,
                streamingText = "Section 3 explains ",
                messages = listOf(ChatMessage(1, ChatRole.User, "Explain section 3"))
            ),
            onIntent = {},
            onOpenSource = { _, _ -> }
        )
    }
}
