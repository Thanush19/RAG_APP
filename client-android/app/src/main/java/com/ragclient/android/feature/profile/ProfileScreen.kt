package com.ragclient.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.BuildConfig
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppIcons
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.data.storage.Session
import com.ragclient.android.feature.shell.ShellIntent
import com.ragclient.android.feature.shell.ShellViewModel

@Composable
fun ProfileScreen(
    viewModel: ShellViewModel,
    onOpenDocuments: () -> Unit,
    onOpenChats: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileContent(
        session = state.session,
        documentCount = state.documents.size,
        usedBytes = state.documents.sumOf { it.fileSize },
        chatCount = state.conversations.size,
        onSignOut = { viewModel.onIntent(ShellIntent.SignOut) },
        onOpenDocuments = onOpenDocuments,
        onOpenChats = onOpenChats,
        // The LAN address is a staging debugging aid; a production build
        // must not advertise where it talks to.
        backendUrl = BuildConfig.API_BASE_URL.takeIf { BuildConfig.IS_DEV_BUILD }
    )
}

@Composable
private fun ProfileContent(
    session: Session,
    documentCount: Int,
    chatCount: Int,
    onSignOut: () -> Unit,
    onOpenDocuments: () -> Unit = {},
    onOpenChats: () -> Unit = {},
    backendUrl: String? = null,
    usedBytes: Long = 0L
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent)
            ) {
                Text(
                    text = session.initials,
                    style = AppType.Display,
                    color = AppColors.OnAccent
                )
            }
            Column {
                Text(
                    text = session.displayName ?: "Signed in",
                    style = AppType.Display,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = session.email.orEmpty(),
                    style = AppType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatLine(label = "Documents", value = documentCount.toString(), onClick = onOpenDocuments)
            StorageMeter(usedBytes = usedBytes)
        }
        StatLine(label = "Chats", value = chatCount.toString(), onClick = onOpenChats)
        backendUrl?.let { StatLine(label = "Backend", value = it) }

        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = AppColors.Error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Sign out", style = AppType.Body)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = if (onClick != null) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onClick != null) {
                Icon(
                    imageVector = AppIcons.Forward,
                    contentDescription = null,
                    tint = AppColors.Icon,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilePreview() {
    ProfileContent(
        session = Session(
            isAuthenticated = true,
            displayName = "Demo User",
            email = "demo@example.com"
        ),
        documentCount = 3,
        chatCount = 7,
        onSignOut = {},
        backendUrl = "http://192.168.1.5:8080/",
        usedBytes = 12_500_000
    )
}
