package com.ragclient.android.feature.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ragclient.android.R
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppIcons
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.ConfirmAction
import com.ragclient.android.feature.wake.ServerWakeNotice
import com.ragclient.android.feature.wake.rememberHostWaking
import com.ragclient.android.core.designsystem.ConfirmDialog
import com.ragclient.android.core.designsystem.color
import com.ragclient.android.core.designsystem.label
import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.data.storage.Session
import com.ragclient.android.data.upload.UploadJob
import com.ragclient.android.domain.model.DocumentStatus
import com.ragclient.android.feature.chat.ChatScreen
import com.ragclient.android.feature.chat.ChatViewModel
import com.ragclient.android.feature.chats.ChatsScreen
import com.ragclient.android.feature.documents.DocumentsScreen
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
    val hostWaking = rememberHostWaking()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Upload feedback is a toast, not a banner: it must not cover the canvas
    // or the manager headers while a file is moving.
    LaunchedEffect(state.toast) {
        state.toast?.let { text ->
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.onIntent(ShellIntent.ToastShown)
        }
    }

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

    val openChat: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // The managers and the viewer draw their own back header; a hamburger and
    // app title above it was a second, duplicate chrome row.
    val ownsHeader = currentRoute in setOf(
        Routes.CHATS,
        Routes.DOCUMENTS,
        Routes.VIEWER_PATTERN,
        Routes.STAGED_VIEWER_PATTERN
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = state,
                onNewChat = {
                    closeDrawer()
                    openChat(Routes.chat(Routes.CHAT_NEW))
                },
                onOpenChat = { id ->
                    closeDrawer()
                    openChat(Routes.chat(id))
                },
                onDeleteChat = { viewModel.onIntent(ShellIntent.DeleteConversation(it)) },
                onAddDocument = {
                    closeDrawer()
                    pickDocuments()
                },
                onCancelUpload = { job -> viewModel.onIntent(ShellIntent.CancelUpload(job)) },
                onPreviewUpload = { path ->
                    closeDrawer()
                    Routes.stagedViewer(path)?.let { navController.navigate(it) }
                },
                onOpenDocument = { id ->
                    closeDrawer()
                    navController.navigate(Routes.viewer(id, 1))
                },
                onAskDocument = { id ->
                    closeDrawer()
                    openChat(Routes.chatForDocument(id))
                },
                onNewChatInDocument = { id ->
                    closeDrawer()
                    openChat(Routes.newChatInDocument(id))
                },
                onDeleteDocument = { viewModel.onIntent(ShellIntent.DeleteDocument(it)) },
                onDeleteChatsForDocument = {
                    viewModel.onIntent(ShellIntent.DeleteChatsForDocument(it))
                },
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
                if (ownsHeader) {
                    // Consume the status bar so the screen's own header starts
                    // below it, without drawing any chrome of our own.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            )
                    )
                } else {
                    ShellTopBar(onMenu = { scope.launch { drawerState.open() } })
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Visible on every screen, so a file picked from the new-chat
                // canvas shows its progress without opening the drawer.
                if (hostWaking) {
                    ServerWakeNotice(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                state.uploads.firstOrNull()?.let { job ->
                    UploadStrip(
                        job = job,
                        onCancel = { viewModel.onIntent(ShellIntent.CancelUpload(job)) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.CHAT_LATEST,
                        modifier = Modifier.fillMaxSize()
                    ) {
                composable(
                    route = Routes.CHAT_PATTERN,
                    arguments = listOf(
                        navArgument(ChatViewModel.ARG_CONVERSATION) {
                            type = NavType.StringType
                            defaultValue = Routes.CHAT_LATEST_ID
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
                        },
                        onUploadDocument = { pickDocuments() },
                        onOpenDocumentChat = { documentId ->
                            openChat(Routes.chatForDocument(documentId))
                        }
                    )
                }

                composable(Routes.SEARCH) {
                    SearchScreen(
                        onOpenResult = { documentId, page ->
                            navController.navigate(Routes.viewer(documentId, page))
                        },
                        onAskDocument = { documentId ->
                            openChat(Routes.chatForDocument(documentId))
                        }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        viewModel = viewModel,
                        onOpenDocuments = { navController.navigate(Routes.DOCUMENTS) },
                        onOpenChats = { navController.navigate(Routes.CHATS) }
                    )
                }

                composable(Routes.CHATS) {
                    ChatsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        // Push, not openChat: the manager must stay under the
                        // thread so BACK returns to the list, not the launcher.
                        onOpenChat = { navController.navigate(Routes.chat(it)) },
                        onNewChat = { navController.navigate(Routes.chat(Routes.CHAT_NEW)) }
                    )
                }

                composable(Routes.DOCUMENTS) {
                    DocumentsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenDocument = { navController.navigate(Routes.viewer(it, 1)) },
                        onAskDocument = { navController.navigate(Routes.chatForDocument(it)) },
                        onNewChatInDocument = {
                            navController.navigate(Routes.newChatInDocument(it))
                        },
                        onUpload = { pickDocuments() }
                    )
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

                composable(
                    route = Routes.STAGED_VIEWER_PATTERN,
                    arguments = listOf(
                        navArgument(ViewerViewModel.ARG_DIRECTORY) { type = NavType.StringType },
                        navArgument(ViewerViewModel.ARG_NAME) { type = NavType.StringType }
                    )
                ) {
                    ViewerScreen(onBack = { navController.popBackStack() })
                }
                    }

                    state.banner?.let { text ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
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
    }
}

@Composable
private fun ShellTopBar(onMenu: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Background first so the colour still paints behind the status
            // bar; the insets are then consumed here, so the Scaffold hands
            // the NavHost a top padding that already includes them.
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = AppIcons.Menu,
                contentDescription = "Open menu",
                tint = AppColors.Icon
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
        // Search and New chat live in the drawer; repeating them here was a
        // second copy of the same two controls.
    }
}

@Composable
private fun AppDrawer(
    state: ShellState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onAddDocument: () -> Unit,
    onCancelUpload: (UploadJob) -> Unit,
    onPreviewUpload: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onAskDocument: (String) -> Unit,
    onNewChatInDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onDeleteChatsForDocument: (String) -> Unit,
    onRetryDocument: (String) -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    /** Destructive menu entries ask first; the drawer is a crowded place to undo. */
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }
    val askConfirm: (ConfirmAction) -> Unit = { pendingConfirm = it }

    // Both lists open at five rows; "See more" reveals the next five.
    var chatsShown by remember { mutableStateOf(DrawerPageSize) }
    var documentsShown by remember { mutableStateOf(DrawerPageSize) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(330.dp)
    ) {
        ConfirmDialog(pendingConfirm, onDismiss = { pendingConfirm = null })

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
                items(state.conversations.take(chatsShown), key = { it.id }) { conversation ->
                    val scopedName = state.documents
                        .firstOrNull { it.id == conversation.scopedDocumentId }
                        ?.fileName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationDrawerItem(
                            selected = false,
                            onClick = { onOpenChat(conversation.id) },
                            label = {
                                Column {
                                    Text(
                                        text = conversation.title,
                                        style = AppType.Body,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    // Which file this thread belongs to, so a
                                    // document chat is not another anonymous row.
                                    if (scopedName != null) {
                                        Text(
                                            text = scopedName,
                                            style = AppType.Label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteChat(conversation.id) }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = "Delete chat",
                                tint = AppColors.Icon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (state.conversations.size > chatsShown) {
                    item {
                        SeeMoreRow { chatsShown += DrawerPageSize }
                    }
                }
            }

            if (state.uploads.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.uploads_section)) }
                items(state.uploads, key = { it.workId }) { job ->
                    UploadRow(
                        job = job,
                        onCancel = { onCancelUpload(job) },
                        onPreview = { job.stagedPath?.let(onPreviewUpload) }
                    )
                }
            }

            item { SectionLabel("Documents") }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onAddDocument,
                    icon = {
                        Icon(AppIcons.Upload, contentDescription = null, tint = AppColors.Icon)
                    },
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
                items(state.documents.take(documentsShown), key = { it.id }) { document ->
                    val chatCount = state.conversations.count {
                        it.scopedDocumentId == document.id
                    }
                    DocumentRow(
                        document = document,
                        chatCount = chatCount,
                        onOpen = { onOpenDocument(document.id) },
                        onAsk = { onAskDocument(document.id) },
                        onNewChat = { onNewChatInDocument(document.id) },
                        onDeleteChats = {
                            askConfirm(
                                ConfirmAction(
                                    title = "Delete chats",
                                    body = "Delete ${pluralize(chatCount, "chat")} " +
                                        "in ${document.fileName}? Answers already given are gone.",
                                    confirmLabel = "Delete chats",
                                    run = { onDeleteChatsForDocument(document.id) }
                                )
                            )
                        },
                        onDelete = {
                            askConfirm(
                                ConfirmAction(
                                    title = "Delete document",
                                    body = "Remove ${document.fileName} from your library? " +
                                        "Chats about this file are deleted too.",
                                    confirmLabel = "Delete document",
                                    run = { onDeleteDocument(document.id) }
                                )
                            )
                        },
                        onRetry = { onRetryDocument(document.id) }
                    )
                }
                if (state.documents.size > documentsShown) {
                    item {
                        SeeMoreRow { documentsShown += DrawerPageSize }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onSearch,
                    icon = {
                        Icon(AppIcons.Search, contentDescription = null, tint = AppColors.Icon)
                    },
                    label = { Text("Search inside documents", style = AppType.Body) }
                )
            }

            item {
                NavigationDrawerItem(
                    selected = false,
                    onClick = onProfile,
                    icon = {
                        Icon(AppIcons.Settings, contentDescription = null, tint = AppColors.Icon)
                    },
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
private fun SeeMoreRow(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "See more", style = AppType.Body, color = AppColors.Accent)
    }
}

private const val DrawerPageSize = 5

@Composable
private fun UploadRow(
    job: UploadJob,
    onCancel: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPreview)
        ) {
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
                imageVector = AppIcons.Close,
                contentDescription = stringResource(R.string.upload_cancel),
                tint = AppColors.Icon,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun UploadStrip(job: UploadJob, onCancel: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "upload")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "upload-pulse"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = AppShapes.Card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = AppIcons.Upload,
                contentDescription = null,
                tint = AppColors.Accent.copy(alpha = pulse),
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = job.fileName,
                    style = AppType.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (job.running) {
                    LinearProgressIndicator(
                        progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(3.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(3.dp)
                    )
                }
            }
            Text(
                text = if (job.running) "${job.percent}%" else "Queued",
                style = AppType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp)
            )
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Cancel upload",
                    tint = AppColors.Icon,
                    modifier = Modifier.size(18.dp)
                )
            }
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
                imageVector = AppIcons.NewChat,
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
    chatCount: Int,
    onOpen: () -> Unit,
    onAsk: () -> Unit,
    onNewChat: () -> Unit,
    onDeleteChats: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

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
                        text = if (chatCount > 0) {
                            "${document.status.label()} · ${pluralize(chatCount, "chat")}"
                        } else {
                            document.status.label()
                        },
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
                    imageVector = AppIcons.Retry,
                    contentDescription = "Retry processing",
                    tint = AppColors.Icon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onAsk) {
            Icon(
                imageVector = AppIcons.Ask,
                contentDescription = "Ask this document",
                tint = AppColors.Icon,
                modifier = Modifier.size(18.dp)
            )
        }

        // The two destructive actions live behind a menu, so the row keeps the
        // two icons it had before and neither of them can wipe data by mistake.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = AppIcons.More,
                    contentDescription = "Options for ${document.fileName}",
                    tint = AppColors.Icon,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Start a new chat in this document") },
                    onClick = {
                        menuOpen = false
                        onNewChat()
                    }
                )
                // No dead "Delete 0 chats" row: the item only exists when
                // there is something in this document to lose.
                if (chatCount > 0) {
                    DropdownMenuItem(
                        text = { Text("Delete ${pluralize(chatCount, "chat")}") },
                        onClick = {
                            menuOpen = false
                            onDeleteChats()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete document") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun pluralize(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/** The backend ingests PDFs only (`DocumentService.validateFile`), so the picker must not offer more. */
private val DOCUMENT_MIME_TYPES = arrayOf("application/pdf")
