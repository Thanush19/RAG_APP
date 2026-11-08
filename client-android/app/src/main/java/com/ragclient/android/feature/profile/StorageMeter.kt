package com.ragclient.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppType
import kotlin.math.roundToInt

/** Mirrors the server's per-user cap (`DocumentService.MAX_USER_STORAGE`). */
const val USER_STORAGE_BYTES = 50L * 1024 * 1024

enum class StorageLevel { Normal, Warning, Critical }

/** The last tenth of free space reads red, the last third orange. */
fun storageLevel(usedBytes: Long, quotaBytes: Long = USER_STORAGE_BYTES): StorageLevel {
    val free = quotaBytes - usedBytes
    return when {
        free <= quotaBytes / 10 -> StorageLevel.Critical
        free <= quotaBytes * 3 / 10 -> StorageLevel.Warning
        else -> StorageLevel.Normal
    }
}

@Composable
fun StorageMeter(usedBytes: Long, modifier: Modifier = Modifier) {
    val color = when (storageLevel(usedBytes)) {
        StorageLevel.Normal -> AppColors.Accent
        StorageLevel.Warning -> AppColors.Warning
        StorageLevel.Critical -> AppColors.Error
    }
    val fraction = (usedBytes.toFloat() / USER_STORAGE_BYTES).coerceIn(0f, 1f)
    val percent = (fraction * 100).roundToInt()
    val free = (USER_STORAGE_BYTES - usedBytes).coerceAtLeast(0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            // The default stop dot sits at the far end in the same colour and
            // reads as "full" even at 22%.
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )
        Text(
            text = "Used $percent% · ${mb(free)} free of ${mb(USER_STORAGE_BYTES)}",
            style = AppType.Label,
            color = color
        )
    }
}

/** The cap is 50 MiB server-side; read it back in the same whole units. */
private fun mb(bytes: Long): String = "${(bytes / 1_048_576f).roundToInt()} MB"