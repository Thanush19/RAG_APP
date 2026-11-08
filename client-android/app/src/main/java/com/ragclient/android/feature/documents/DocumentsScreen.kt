package com.ragclient.android.feature.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppIcons
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.ConfirmAction
import com.ragclient.android.core.designsystem.ConfirmDialog
import com.ragclient.android.core.designsystem.RagClientTheme
import com.ragclient.android.core.designsystem.color
import com.ragclient.android.core.designsystem.formatSize
import com.ragclient.android.core.designsystem.label
import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.domain.model.ConversationSummary
import com.ragclient.android.domain.model.DocumentStatus
import com.ragclient.android.feature.shell.ShellIntent
import com.ragclient.android.feature.shell.ShellViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The full-screen library reached from Profile: one card per file with its
 * first page as the preview, the way a photo grid reads. Everything the
 * drawer's overflow menu can do is here too, plus upload and reprocess.
 */
@Composable
fun DocumentsScreen(
    viewModel: ShellViewModel,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onAskDocument: (String) -> Unit,
    onNewChatInDocument: (String) -> Unit,
    onUpload: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DocumentsContent(
        documents = state.documents,
        conversations = state.conversations,
        onBack = onBack,
        onOpenDocument = onOpenDocument,
        onAskDocument = onAskDocument,
        onNewChatInDocument = onNewChatInDocument,
        onUpload = onUpload,
        onReprocess = { viewModel.onIntent(ShellIntent.RetryDocument(it)) },
        onDeleteChats = { viewModel.onIntent(ShellIntent.DeleteChatsForDocument(it)) },
        onDeleteDocument = { viewModel.onIntent(ShellIntent.DeleteDocument(it)) },
        loadThumbnail = viewModel::thumbnail
    )
}

@Composable
private fun DocumentsContent(
    documents: List<LibraryDocument>,
    conversations: List<ConversationSummary>,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onAskDocument: (String) -> Unit,
    onNewChatInDocument: (String) -> Unit,
    onUpload: () -> Unit,
    onReprocess: (String) -> Unit,
    onDeleteChats: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    loadThumbnail: suspend (String, Int) -> ImageBitmap?
) {
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    ConfirmDialog(pendingConfirm, onDismiss = { pendingConfirm = null })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.Back, contentDescription = "Back", tint = AppColors.Icon)
            }
            Text(
                text = "Documents uploaded",
                style = AppType.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onUpload) {
                Icon(AppIcons.Upload, contentDescription = "Upload new file", tint = AppColors.Icon)
            }
        }

        if (documents.isEmpty()) {
            Text(
                text = "Nothing uploaded yet.",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            OutlinedButton(onClick = onUpload) { Text("Upload new file") }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(documents, key = { it.id }) { document ->
                val chatCount = conversations.count { it.scopedDocumentId == document.id }
                DocumentCard(
                    document = document,
                    chatCount = chatCount,
                    onOpen = { onOpenDocument(document.id) },
                    onAsk = { onAskDocument(document.id) },
                    onNewChat = { onNewChatInDocument(document.id) },
                    onReprocess = { onReprocess(document.id) },
                    onDeleteChats = {
                        pendingConfirm = ConfirmAction(
                            title = "Delete chats",
                            body = "Delete ${plural(chatCount)} in ${document.fileName}? " +
                                "Answers already given are gone.",
                            confirmLabel = "Delete chats",
                            run = { onDeleteChats(document.id) }
                        )
                    },
                    onDelete = {
                        pendingConfirm = ConfirmAction(
                            title = "Delete document",
                            body = "Remove ${document.fileName} from your library? " +
                                "Chats about this file are deleted too.",
                            confirmLabel = "Delete document",
                            run = { onDeleteDocument(document.id) }
                        )
                    },
                    loadThumbnail = loadThumbnail
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: LibraryDocument,
    chatCount: Int,
    onOpen: () -> Unit,
    onAsk: () -> Unit,
    onNewChat: () -> Unit,
    onReprocess: () -> Unit,
    onDeleteChats: () -> Unit,
    onDelete: () -> Unit,
    loadThumbnail: suspend (String, Int) -> ImageBitmap?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.File,
                    contentDescription = null,
                    tint = AppColors.Icon,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = document.fileName,
                    style = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = AppIcons.More,
                            contentDescription = "Options for ${document.fileName}",
                            tint = AppColors.Icon
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ask this document") },
                            onClick = {
                                menuOpen = false
                                onAsk()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Start a new chat in this document") },
                            onClick = {
                                menuOpen = false
                                onNewChat()
                            }
                        )
                        // The server answers 409 "already processed" for a
                        // READY file, so reprocess is a retry, not a refresh.
                        if (document.status == DocumentStatus.FAILED) {
                            DropdownMenuItem(
                                text = { Text("Reprocess") },
                                onClick = {
                                    menuOpen = false
                                    onReprocess()
                                }
                            )
                        }
                        if (chatCount > 0) {
                            DropdownMenuItem(
                                text = { Text("Delete ${plural(chatCount)}") },
                                onClick = {
                                    menuOpen = false
                                    onDeleteChats()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete document", color = AppColors.Error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            DocumentPreview(document, loadThumbnail)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(document.status.color())
                )
                Text(
                    text = if (chatCount > 0) {
                        "${document.status.label()} · ${plural(chatCount)}"
                    } else {
                        document.status.label()
                    },
                    style = AppType.Label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Text(
                    text = listOfNotNull(formatSize(document.fileSize), addedOn(document.createdAt))
                        .joinToString(" · "),
                    style = AppType.Label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentPreview(
    document: LibraryDocument,
    loadThumbnail: suspend (String, Int) -> ImageBitmap?
) {
    var bitmap by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(document.id) {
        bitmap = loadThumbnail(document.id, THUMBNAIL_WIDTH_PX)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 12.dp)
            .clip(AppShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val preview = bitmap
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "First page of ${document.fileName}",
                // A portrait page in a landscape window: keep the title, not
                // the middle of the body text.
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = AppIcons.File,
                contentDescription = null,
                tint = AppColors.Muted,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

private fun plural(count: Int): String = if (count == 1) "1 chat" else "$count chats"

private fun addedOn(createdAt: String?): String? =
    createdAt?.take(10)?.let {
        runCatching {
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }.getOrNull()
    }

private const val THUMBNAIL_WIDTH_PX = 720

@Preview(showBackground = true)
@Composable
private fun DocumentsPreview() {
    RagClientTheme {
        DocumentsContent(
            documents = listOf(
                LibraryDocument("1", "resume_srivastava_rahul.pdf", "pdf", 245_000, DocumentStatus.READY, "2026-11-01T10:00:00"),
                LibraryDocument("2", "broken.pdf", "pdf", 9_650_000, DocumentStatus.FAILED, null)
            ),
            conversations = listOf(
                ConversationSummary("c1", "skills", 0L, scopedDocumentId = "1")
            ),
            onBack = {},
            onOpenDocument = {},
            onAskDocument = {},
            onNewChatInDocument = {},
            onUpload = {},
            onReprocess = {},
            onDeleteChats = {},
            onDeleteDocument = {},
            loadThumbnail = { _, _ -> null }
        )
    }
}