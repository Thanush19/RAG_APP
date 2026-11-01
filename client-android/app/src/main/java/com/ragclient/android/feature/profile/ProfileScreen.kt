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
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.BuildConfig
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.data.storage.Session
import com.ragclient.android.feature.shell.ShellIntent
import com.ragclient.android.feature.shell.ShellViewModel

@Composable
fun ProfileScreen(viewModel: ShellViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileContent(
        session = state.session,
        documentCount = state.documents.size,
        chatCount = state.conversations.size,
        onSignOut = { viewModel.onIntent(ShellIntent.SignOut) }
    )
}

@Composable
private fun ProfileContent(
    session: Session,
    documentCount: Int,
    chatCount: Int,
    onSignOut: () -> Unit
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

        StatLine(label = "Documents", value = documentCount.toString())
        StatLine(label = "Chats", value = chatCount.toString())
        StatLine(label = "Backend", value = BuildConfig.API_BASE_URL)

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
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = AppType.Body,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        onSignOut = {}
    )
}
