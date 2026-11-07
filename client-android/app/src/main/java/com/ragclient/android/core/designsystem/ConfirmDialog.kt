package com.ragclient.android.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

data class ConfirmAction(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val run: () -> Unit
)

/** Destructive actions ask first; a list row is a crowded place to undo. */
@Composable
fun ConfirmDialog(action: ConfirmAction?, onDismiss: () -> Unit) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.title) },
        text = { Text(action.body) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); action.run() }) {
                Text(action.confirmLabel, color = AppColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}