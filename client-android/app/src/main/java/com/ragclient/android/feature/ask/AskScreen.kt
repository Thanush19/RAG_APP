package com.ragclient.android.feature.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Answer-first canvas (client plan 5): large serif answer block,
 * source pills underneath, single growing prompt row. The bespoke
 * art direction lands in M4; M0 ships the working structure.
 */
@Composable
fun AskScreen(
    modifier: Modifier = Modifier,
    viewModel: AskViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.answerSoFar.isNotBlank() || state.isGenerating) {
                Text(
                    text = state.answerSoFar,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (state.sources.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sources.forEachIndexed { index, source ->
                        ElevatedSuggestionChip(
                            onClick = { },
                            label = { Text("S${index + 1} · p${source.pageNumber ?: "?"}") }
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        PromptRow(
            value = state.question,
            isGenerating = state.isGenerating,
            onValueChange = { viewModel.onIntent(AskIntent.QuestionChanged(it)) },
            onSubmit = { viewModel.onIntent(AskIntent.Submit(state.question)) },
            onStop = { viewModel.onIntent(AskIntent.Stop) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PromptRow(
    value: String,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            placeholder = { Text("Ask your documents…") },
            maxLines = 4
        )
        if (isGenerating) {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = "Stop")
            }
        } else {
            IconButton(onClick = onSubmit) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
            }
        }
    }
}

