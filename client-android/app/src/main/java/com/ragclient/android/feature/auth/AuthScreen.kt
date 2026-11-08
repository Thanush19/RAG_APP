package com.ragclient.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.BuildConfig
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.RagClientTheme
import com.ragclient.android.feature.wake.ServerWakeNotice
import com.ragclient.android.feature.wake.rememberHostWaking

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waking = rememberHostWaking()

    AuthContent(state = state, waking = waking, onIntent = viewModel::onIntent)
}

@Composable
private fun AuthContent(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    waking: Boolean = false
) {
    val isRegister = state.mode == AuthMode.Register

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // This screen is the root when signed out, so there is no
                // Scaffold handing it insets — it takes all of them itself.
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BrandMark()

            Text(
                text = if (isRegister) "Create your library" else "Welcome back",
                style = AppType.Display,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Ask questions of the documents you own — nothing else is searched.",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (isRegister) {
                Field(
                    value = state.name,
                    onValueChange = { onIntent(AuthIntent.ChangeField(AuthField.Name, it)) },
                    label = "Name"
                )
            }

            Field(
                value = state.email,
                onValueChange = { onIntent(AuthIntent.ChangeField(AuthField.Email, it)) },
                label = "Email",
                keyboardType = KeyboardType.Email
            )

            Field(
                value = state.password,
                onValueChange = { onIntent(AuthIntent.ChangeField(AuthField.Password, it)) },
                label = "Password",
                keyboardType = KeyboardType.Password,
                isPassword = true
            )

            state.error?.let { problem ->
                Text(
                    text = problem,
                    style = AppType.Body,
                    color = AppColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { onIntent(AuthIntent.Submit) },
                enabled = if (isRegister) state.canSubmitRegister else state.submitEnabled,
                shape = AppShapes.PromptBar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (state.inFlight) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = if (isRegister) "  Creating account…" else "  Signing in…",
                            style = AppType.Body,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = if (isRegister) "Create account" else "Sign in",
                        style = AppType.Body,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (waking) {
                ServerWakeNotice()
            }

            TextButton(onClick = { onIntent(AuthIntent.SwitchMode) }) {
                Text(
                    text = if (isRegister) "Already have an account? Sign in"
                    else "New here? Create an account",
                    style = AppType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (BuildConfig.IS_DEV_BUILD) {
                Text(
                    text = "${BuildConfig.API_BASE_URL}  •  dev",
                    style = AppType.Label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(AppColors.Accent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "R",
            style = AppType.Display,
            color = AppColors.OnAccent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = AppType.Body) },
        singleLine = true,
        shape = AppShapes.Card,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun AuthSignInPreview() {
    RagClientTheme {
        AuthContent(
            state = AuthState(email = "demo@example.com", password = "hunter2abc"),
            onIntent = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthRegisterErrorPreview() {
    RagClientTheme {
        AuthContent(
            state = AuthState(
                mode = AuthMode.Register,
                name = "Demo",
                email = "demo@example.com",
                password = "hunter2abc",
                error = "Email is required."
            ),
            onIntent = {}
        )
    }
}
