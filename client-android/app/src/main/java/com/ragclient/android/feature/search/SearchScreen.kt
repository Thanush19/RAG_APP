package com.ragclient.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragclient.android.core.designsystem.AppColors
import com.ragclient.android.core.designsystem.AppIcons
import com.ragclient.android.core.designsystem.AppShapes
import com.ragclient.android.core.designsystem.AppType
import com.ragclient.android.core.designsystem.RagClientTheme

@Composable
fun SearchScreen(
    onOpenResult: (documentId: String, pageNumber: Int) -> Unit,
    onAskDocument: (documentId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenResult = onOpenResult,
        onAskDocument = onAskDocument
    )
}

@Composable
private fun SearchContent(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onOpenResult: (documentId: String, pageNumber: Int) -> Unit,
    onAskDocument: (documentId: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(SearchIntent.ChangeQuery(it)) },
            placeholder = { Text("Search inside your documents", style = AppType.Body) },
            singleLine = true,
            shape = AppShapes.Card,
            trailingIcon = {
                IconButton(onClick = { onIntent(SearchIntent.Run) }, enabled = state.canSearch) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = "Search",
                        tint = AppColors.Icon
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        state.error?.let { problem ->
            Text(
                text = problem,
                style = AppType.Body,
                color = AppColors.Error,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        if (state.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Searching…",
                    style = AppType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.hasSearched && state.results.isEmpty()) {
            Text(
                text = "No passage in your documents matched that. Try different wording.",
                style = AppType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.results, key = { it.chunkId }) { hit ->
                ResultCard(
                    hit = hit,
                    onOpen = {
                        hit.pageNumber?.let { page -> onOpenResult(hit.documentId, page) }
                    },
                    onAsk = { onAskDocument(hit.documentId) }
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    hit: SearchHit,
    onOpen: () -> Unit,
    onAsk: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = AppShapes.Card,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${hit.fileName} · p${hit.pageNumber ?: "?"} · " +
                        "match ${String.format("%.2f", hit.score)}",
                    style = AppType.Label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = hit.text,
                    style = AppType.Body,
                    maxLines = 5,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onAsk) {
                Icon(
                    imageVector = AppIcons.Ask,
                    contentDescription = "Ask this document",
                    tint = AppColors.Icon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchResultsPreview() {
    RagClientTheme {
        SearchContent(
            state = SearchState(
                query = "retry policy",
                hasSearched = true,
                results = listOf(
                    SearchHit(
                        chunkId = "c1",
                        documentId = "d1",
                        pageNumber = 4,
                        text = "The ingestion worker retries three times with exponential backoff.",
                        score = 0.62f,
                        fileName = "architecture.pdf"
                    )
                )
            ),
            onIntent = {},
            onOpenResult = { _, _ -> },
            onAskDocument = {}
        )
    }
}
