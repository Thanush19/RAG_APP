package com.ragclient.android.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppType

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = if (state.pageCount > 0) {
                    "${state.pageCount} page${if (state.pageCount == 1) "" else "s"}"
                } else {
                    "Document"
                },
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            state.error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = AppType.Body,
                        color = AppColors.Error
                    )
                    Button(onClick = { viewModel.onIntent(ViewerIntent.Retry) }) {
                        Text("Retry", style = AppType.Body)
                    }
                }
            }

            state.pageCount == 0 -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            else -> PdfPager(state = state, onIntent = viewModel::onIntent)
        }
    }
}

@Composable
private fun PdfPager(
    state: ViewerState,
    onIntent: (ViewerIntent) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    LaunchedEffect(state.initialPage, state.pageCount) {
        if (state.pageCount > 0) {
            pagerState.scrollToPage(state.initialPage.coerceIn(0, state.pageCount - 1))
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { index ->
        PageView(
            bitmap = state.pages[index],
            onVisible = { widthPx -> onIntent(ViewerIntent.PageVisible(index, widthPx)) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PageView(
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onVisible: (widthPx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(widthPx, bitmap) {
        if (widthPx > 0 && bitmap == null) onVisible(widthPx)
    }

    Box(
        modifier = modifier
            .onSizeChanged { size -> widthPx = size.width }
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Document page",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 120.dp)
                    .size(24.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
