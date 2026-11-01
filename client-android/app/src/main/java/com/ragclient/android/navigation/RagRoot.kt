package com.ragclient.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.feature.auth.AuthScreen
import com.ragclient.android.feature.auth.AuthViewModel
import com.ragclient.android.feature.shell.AppShell

/**
 * Session gate: the app shows the sign-in flow or the drawer shell
 * based on the stored session, so a cold start lands straight in the
 * last chat when the token is still valid.
 */
@Composable
fun RagRoot() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    if (authState.isSignedIn) {
        AppShell()
    } else {
        AuthScreen(viewModel = authViewModel)
    }
}
