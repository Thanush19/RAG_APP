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
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppIcons
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.RagClientTheme
import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.ChatRole
import com.ragclient.android.domain.model.DocumentStatus
import com.ragclient.android.domain.model.RagSourceModel

@Composable
fun ChatScreen(
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit,
    onUploadDocument: () -> Unit,
    onOpenDocumentChat: (documentId: String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenSource = onOpenSource,
        onUploadDocument = onUploadDocument,
        onOpenDocumentChat = onOpenDocumentChat
    )
}

@Composable
private fun ChatContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    onOpenSource: (documentId: String, pageNumber: Int) -> Unit,
    onUploadDocument: () -> Unit,
    onOpenDocumentChat: (documentId: String) -> Unit
) {
    val listState = rememberLazyListState()
    var showAttach by remember { mutableStateOf(false) }

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
                item {
                    EmptyCanvas(
                        scopedDocument = state.documents.firstOrNull {
                            it.id == state.scopedDocumentId
                        },
                        documents = state.documents
                    )
                }
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
            scopedDocumentName = state.scopedDocumentName,
            onValueChange = { onIntent(ChatIntent.ChangePrompt(it)) },
            onSend = { onIntent(ChatIntent.Send) },
            onStop = { onIntent(ChatIntent.Stop) },
            onClearScope = { onIntent(ChatIntent.ClearScope) },
            onAttach = { showAttach = true }
        )

        if (showAttach) {
            AttachDialog(
                documents = state.documents,
                onUpload = {
                    showAttach = false
                    onUploadDocument()
                },
                onPick = {
                    showAttach = false
                    onOpenDocumentChat(it)
                },
                onDismiss = { showAttach = false }
            )
        }
    }
}

/**
 * The blank-thread screen. A scoped thread names its file and says what the
 * file is doing right now; an unscoped one carries a tagline, and names the
 * newest library file once an upload has landed so the canvas is never mute.
 */
@Composable
private fun EmptyCanvas(
    scopedDocument: LibraryDocument?,
    documents: List<LibraryDocument>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (scopedDocument != null) {
            Text(
                text = "Ask questions from",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = scopedDocument.fileName,
                style = AppType.Display,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when (scopedDocument.status) {
                    DocumentStatus.UPLOADED, DocumentStatus.PROCESSING ->
                        "Still indexing — this thread answers from it the moment it is ready."
                    DocumentStatus.FAILED ->
                        "Indexing failed — retry it from Documents uploaded."
                    else -> "Every answer in this thread comes from this file alone."
                },
                style = AppType.Body,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val newest = documents.maxByOrNull { it.createdAt.orEmpty() }
            Text(
                text = "Ask your documents anything.",
                style = AppType.Display,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (newest == null) {
                    "Tap + to upload a PDF and start asking."
                } else {
                    "${newest.fileName} is in your library — tap + to ask from it, " +
                        "or ask across everything."
                },
                style = AppType.Body,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The composer's add button opens this: upload a new file, or point the
 * thread at one already in the library. The file list is a second step of
 * the same dialog so the choice never moves away from the composer.
 */
@Composable
private fun AttachDialog(
    documents: List<LibraryDocument>,
    onUpload: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (picking) "Ask from a file" else "Add to this chat")
        },
        text = {
            if (picking) {
                if (documents.isEmpty()) {
                    Text(
                        text = "Nothing uploaded yet.",
                        style = AppType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(documents, key = { it.id }) { document ->
                            DocumentPickRow(
                                document = document,
                                onClick = { onPick(document.id) }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AttachOption(
                        icon = AppIcons.Upload,
                        label = "Upload new file",
                        onClick = onUpload
                    )
                    AttachOption(
                        icon = AppIcons.File,
                        label = "Ask from an existing file",
                        onClick = { picking = true }
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun AttachOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.Icon,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = AppType.Body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun DocumentPickRow(document: LibraryDocument, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = AppShapes.Card,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = AppIcons.Ask,
                contentDescription = null,
                tint = AppColors.Icon,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = document.fileName,
                style = AppType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )
        }
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
                text = answerMarkup(displayAnswer(message.text)),
                style = AppType.AnswerBody,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (ShowCitations) {
                SourceRow(sources = message.sources, onOpenSource = onOpenSource)
            }
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
                // Re-parsed from the whole buffer on every token rather than
                // incrementally, which is what keeps a half-arrived `**` from
                // ever being shown.
                text = answerMarkup(displayAnswer(text.ifBlank { "Thinking" }), streaming = true),
                style = AppType.AnswerBody,
                color = MaterialTheme.colorScheme.onBackground
            )
            BlinkingCaret()
        }
        if (ShowCitations) {
            SourceRow(sources = sources, onOpenSource = onOpenSource)
        }
    }
}

/**
 * Citation UI is switched off for now: the sources still arrive over SSE,
 * stay in `ChatMessage.sources` and in Room, and the deep-link handler is
 * still wired — only the chips and the inline `[S1]` markers are hidden.
 */
private const val ShowCitations = false

/** Removes the `[S1]`-style markers the model drops into the answer text. */
internal fun displayAnswer(text: String): String = text
    .replace(InlineCitationMarker, "")
    .replace(TrailingSpaceBeforePunctuation, "")
    .trim()

private val InlineCitationMarker = Regex(""" ?\[[Ss]\d+(?: *, *[Ss]\d+)*\]""")
private val TrailingSpaceBeforePunctuation = Regex(" +(?=[.,;:!?)])")

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
                    imageVector = AppIcons.Close,
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
    scopedDocumentName: String?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClearScope: () -> Unit,
    onAttach: () -> Unit
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
            if (scopedDocumentName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, top = 10.dp)
                ) {
                    Text(
                        text = "Asking $scopedDocumentName",
                        style = AppType.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // A resume can be long; without a weight it would push
                        // the "ask all documents" button off the drawer-wide row.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(onClick = onClearScope, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Ask all documents",
                            tint = AppColors.Icon,
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
                // The add button opens the upload / existing-file dialog; it
                // sits in the field's leading slot like the send disc does
                // trailing, so the focus outline wraps both.
                leadingIcon = {
                    IconButton(
                        onClick = onAttach,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = "Add a document",
                            tint = AppColors.Icon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
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
                                AppIcons.Close
                            } else {
                                AppIcons.Send
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
            onOpenSource = { _, _ -> },
            onUploadDocument = {},
            onOpenDocumentChat = {}
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
            onOpenSource = { _, _ -> },
            onUploadDocument = {},
            onOpenDocumentChat = {}
        )
    }
}
