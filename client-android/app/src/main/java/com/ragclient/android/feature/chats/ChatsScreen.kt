package com.ragclient.android.feature.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.domain.model.ConversationSummary
import com.ragclient.android.feature.shell.ShellIntent
import com.ragclient.android.feature.shell.ShellViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full-screen chat manager reached from Profile. The drawer stays a
 * five-row shortcut; this is where the whole history lives and can be
 * renamed or deleted.
 */
@Composable
fun ChatsScreen(
    viewModel: ShellViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatsContent(
        conversations = state.conversations,
        documents = state.documents,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onNewChat = onNewChat,
        onRename = { id, title -> viewModel.onIntent(ShellIntent.RenameConversation(id, title)) },
        onDelete = { viewModel.onIntent(ShellIntent.DeleteConversation(it)) }
    )
}

@Composable
private fun ChatsContent(
    conversations: List<ConversationSummary>,
    documents: List<LibraryDocument>,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var renameText by remember(renaming) { mutableStateOf(renaming?.title.orEmpty()) }
    var pendingDelete by remember { mutableStateOf<ConfirmAction?>(null) }

    ConfirmDialog(pendingDelete, onDismiss = { pendingDelete = null })

    renaming?.let { target ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(target.id, renameText)
                        renaming = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            }
        )
    }

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
                text = "Chat history",
                style = AppType.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onNewChat) {
                Icon(AppIcons.NewChat, contentDescription = "New chat", tint = AppColors.Icon)
            }
        }

        if (conversations.isEmpty()) {
            Text(
                text = "No chats yet.",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(conversations, key = { it.id }) { conversation ->
                ChatRow(
                    conversation = conversation,
                    scopedName = documents
                        .firstOrNull { it.id == conversation.scopedDocumentId }
                        ?.fileName,
                    onOpen = { onOpenChat(conversation.id) },
                    onRename = { renaming = conversation },
                    onDelete = {
                        pendingDelete = ConfirmAction(
                            title = "Delete chat",
                            body = "Delete \"${conversation.title}\"? " +
                                "Its questions and answers are gone.",
                            confirmLabel = "Delete chat",
                            run = { onDelete(conversation.id) }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatRow(
    conversation: ConversationSummary,
    scopedName: String?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = AppType.Body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(scopedName, relativeTime(conversation.updatedAt))
                        .joinToString(" · "),
                    style = AppType.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = AppIcons.More,
                        contentDescription = "Options for ${conversation.title}",
                        tint = AppColors.Icon
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = AppColors.Error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

internal fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatsPreview() {
    RagClientTheme {
        ChatsContent(
            conversations = listOf(
                ConversationSummary("1", "what are his skills", System.currentTimeMillis()),
                ConversationSummary(
                    "2",
                    "New chat",
                    System.currentTimeMillis() - 90_000_000,
                    scopedDocumentId = "d1"
                )
            ),
            documents = listOf(
                LibraryDocument("d1", "resume.pdf", "pdf", 1024, com.ragclient.android.domain.model.DocumentStatus.READY, null)
            ),
            onBack = {},
            onOpenChat = {},
            onNewChat = {},
            onRename = { _, _ -> },
            onDelete = {}
        )
    }
}