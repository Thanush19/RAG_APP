package com.ragclient.android.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** M2 lands the PdfRenderer pager; deep-links carry documentId + page. */
@Composable
fun ViewerScreen(
    documentId: String?,
    page: Int?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Viewer for ${documentId ?: "?"} (p${page ?: 1}) — wired in M2",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
