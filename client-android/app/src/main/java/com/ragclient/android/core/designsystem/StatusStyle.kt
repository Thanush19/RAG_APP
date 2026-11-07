package com.ragclient.android.core.designsystem

import androidx.compose.ui.graphics.Color
import com.ragclient.android.domain.model.DocumentStatus

fun DocumentStatus.color(): Color = when (this) {
    DocumentStatus.READY -> AppColors.StatusReady
    DocumentStatus.PROCESSING -> AppColors.StatusProcessing
    DocumentStatus.FAILED -> AppColors.StatusFailed
    DocumentStatus.UPLOADED -> AppColors.Muted
}

fun DocumentStatus.label(): String = when (this) {
    DocumentStatus.READY -> "Ready"
    DocumentStatus.PROCESSING -> "Indexing…"
    DocumentStatus.FAILED -> "Failed — retry"
    DocumentStatus.UPLOADED -> "Queued"
}