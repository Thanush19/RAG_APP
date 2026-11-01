package com.ragclient.android.feature.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ragclient.android.R
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.data.storage.Session
import com.ragclient.android.data.upload.UploadJob
import com.ragclient.android.domain.model.DocumentStatus
import com.ragclient.android.feature.chat.ChatScreen
import com.ragclient.android.feature.chat.ChatViewModel
import com.ragclient.android.feature.profile.ProfileScreen
import com.ragclient.android.feature.search.SearchScreen
import com.ragclient.android.navigation.Routes
import com.ragclient.android.feature.viewer.ViewerScreen
import com.ragclient.android.feature.viewer.ViewerViewModel
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    navController: NavHostController = rememberNavController(),
    viewModel: ShellViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onIntent(ShellIntent.Upload(it)) } }

    val askToNotify = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { picker.launch(DOCUMENT_MIME_TYPES) }

    /**
     * A denied notification permission does not stop the upload — it only
     * hides the progress row, so the picker still opens either way.
     */
    val pickDocuments: () -> Unit = {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) picker.launch(DOCUMENT_MIME_TYPES) else askToNotify.launch(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = state,
                onNewChat = {
                    closeDrawer()
                    navController.navigate(Routes.chat("new")) { launchSingleTop = true }
                },
                onOpenChat = { id ->
                    closeDrawer()
                    navController.navigate(Routes.chat(id)) { launchSingleTop = true }
                },
                onDeleteChat = { viewModel.onIntent(ShellIntent.DeleteConversation(it)) },
                onAddDocument = {
                    closeDrawer()
                    pickDocuments()
                },
                onCancelUpload = { viewModel.onIntent(ShellIntent.CancelUpload(it)) },
                onOpenDocument = { id ->
                    closeDrawer()
                    navController.navigate(Routes.viewer(id, 1))
                },
                onAskDocument = { id ->
                    closeDrawer()
                    navController.navigate(Routes.chat("new", id)) { launchSingleTop = true }
                },
                onDeleteDocument = { viewModel.onIntent(ShellIntent.DeleteDocument(it)) },
                onRetryDocument = { viewModel.onIntent(ShellIntent.RetryDocument(it)) },
                onSearch = {
                    closeDrawer()
                    navController.navigate(Routes.SEARCH)
                },
                onProfile = {
                    closeDrawer()
                    navController.navigate(Routes.PROFILE)
                },
                onSignOut = { viewModel.onIntent(ShellIntent.SignOut) }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ShellTopBar(
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = { navController.navigate(Routes.chat("new")) { launchSingleTop = true } },
                    onSearch = { navController.navigate(Routes.SEARCH) }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.CHAT_LATEST,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable(
                    route = Routes.CHAT_PATTERN,
                    arguments = listOf(
                        navArgument(ChatViewModel.ARG_CONVERSATION) {
                            type = NavType.StringType
                            defaultValue = ChatViewModel.LATEST_CONVERSATION
                        },
                        navArgument(ChatViewModel.ARG_SCOPE) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = "none"
                        }
                    )
                ) {
                    ChatScreen(
                        onOpenSource = { documentId, page ->
                            navController.navigate(Routes.viewer(documentId, page))
                        }
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(
                        onOpenResult = { documentId, page ->
                            navController.navigate(Routes.viewer(documentId, page))
                        },
                        onAskDocument = { documentId ->
                            navController.navigate(Routes.chat("new", documentId))
                        }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(viewModel = viewModel)
                }

                composable(
                    route = Routes.VIEWER_PATTERN,
                    arguments = listOf(
                        navArgument(ViewerViewModel.ARG_DOCUMENT) { type = NavType.StringType },
                        navArgument(ViewerViewModel.ARG_PAGE) {
                            type = NavType.IntType
                            defaultValue = 1
                        }
                    )
                ) {
                    ViewerScreen(onBack = { navController.popBackStack() })
                }
            }

            state.banner?.let { text ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = AppShapes.Card,
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable { viewModel.onIntent(ShellIntent.BannerShown) }
                    ) {
                        Text(
                            text = text,
                            style = AppType.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellTopBar(
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = AppType.Display,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search documents",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(onClick = onNewChat) {
            Icon(
                imageVector = Icons.Filled.Create,
                contentDescription = "New chat",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun AppDrawer(
    state: ShellState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onAddDocument: () -> Unit,
    onCancelUpload: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onAskDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onRetryDocument: (String) -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(330.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item { DrawerHeader(session = state.session, onProfile = onProfile) }

            item { NewChatButton(onNewChat) }

            item { SectionLabel("Chats") }

            if (state.conversations.isEmpty()) {
                item { DrawerHint("No chats yet.") }
            } else {
                items(state.conversations, key = { it.id }) { conversation ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationDrawerItem(
                            selected = false,
                            onClick = { onOpenChat(conversation.id) },
                            label = {
                                Text(
                                    text = conversation.title,
                                    style = AppType.Body,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteChat(conversation.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (state.uploads.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.uploads_section)) }
                items(state.uploads, key = { it.workId }) { job ->
                    UploadRow(job = job, onCancel = { onCancelUpload(job.workId) })
                }
            }

            item { SectionLabel("Documents") }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onAddDocument,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = {
                        Text(
                            text = if (state.uploads.isEmpty()) {
                                "Add document"
                            } else {
                                stringResource(
                                    R.string.upload_more_count,
                                    state.uploads.size
                                )
                            },
                            style = AppType.Body
                        )
                    }
                )
            }

            if (state.documents.isEmpty()) {
                item { DrawerHint("Nothing uploaded yet.") }
            } else {
                items(state.documents, key = { it.id }) { document ->
                    DocumentRow(
                        document = document,
                        onOpen = { onOpenDocument(document.id) },
                        onAsk = { onAskDocument(document.id) },
                        onDelete = { onDeleteDocument(document.id) },
                        onRetry = { onRetryDocument(document.id) }
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onSearch,
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search inside documents", style = AppType.Body) }
                )
            }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onProfile,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Profile", style = AppType.Body) }
                )
            }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onSignOut,
                    label = {
                        Text(
                            text = "Sign out",
                            style = AppType.Body,
                            color = AppColors.Error
                        )
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            state.banner?.let { text ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = AppShapes.Card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = text,
                            style = AppType.Body,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHint(text: String) {
    Text(
        text = text,
        style = AppType.Body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun UploadRow(job: UploadJob, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = job.fileName,
                style = AppType.Body,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (job.running) {
                    stringResource(R.string.upload_row_percent, job.percent)
                } else {
                    stringResource(R.string.upload_queued)
                },
                style = AppType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.upload_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DrawerHeader(
    session: Session,
    onProfile: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .clickable(onClick = onProfile)
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppColors.Accent)
        ) {
            Text(
                text = session.initials,
                style = AppType.Label,
                color = AppColors.OnAccent
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = session.displayName ?: session.email ?: "Signed in",
                style = AppType.Body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = session.email.orEmpty(),
                style = AppType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NewChatButton(onNewChat: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(AppShapes.PromptBar)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onNewChat)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Create,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "New chat",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = AppType.Label,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun DocumentRow(
    document: LibraryDocument,
    onOpen: () -> Unit,
    onAsk: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationDrawerItem(
            selected = false,
            onClick = onOpen,
            icon = {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(document.status.color())
                )
            },
            label = {
                Column {
                    Text(
                        text = document.fileName,
                        style = AppType.Body,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = document.status.label(),
                        style = AppType.Label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.weight(1f)
        )

        if (document.status == DocumentStatus.FAILED) {
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry processing",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onAsk) {
            Icon(
                imageVector = Icons.Filled.Create,
                contentDescription = "Ask this document",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete document",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun DocumentStatus.color(): Color = when (this) {
    DocumentStatus.READY -> AppColors.StatusReady
    DocumentStatus.PROCESSING -> AppColors.StatusProcessing
    DocumentStatus.FAILED -> AppColors.StatusFailed
    DocumentStatus.UPLOADED -> AppColors.Muted
}

private fun DocumentStatus.label(): String = when (this) {
    DocumentStatus.READY -> "Ready"
    DocumentStatus.PROCESSING -> "Indexing…"
    DocumentStatus.FAILED -> "Failed — retry"
    DocumentStatus.UPLOADED -> "Queued"
}

/** The backend ingests PDFs only (`DocumentService.validateFile`), so the picker must not offer more. */
private val DOCUMENT_MIME_TYPES = arrayOf("application/pdf")
