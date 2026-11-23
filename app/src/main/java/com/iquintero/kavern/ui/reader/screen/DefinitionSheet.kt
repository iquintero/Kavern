package com.iquintero.kavern.ui.reader.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iquintero.kavern.R
import com.iquintero.kavern.core.dictionary.DefinitionResult
import com.iquintero.kavern.core.dictionary.DictionaryEntry
import com.iquintero.kavern.core.dictionary.WiktionaryClient
import com.iquintero.kavern.ui.common.LoadingIndicator
import java.util.Locale

/** Bottom sheet that looks [request] up on Wiktionary and shows its definitions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefinitionSheet(
    request: LookupRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { WiktionaryClient() }
    val word = remember(request) { WiktionaryClient.cleanWord(request.text) }
    val result by produceState<DefinitionResult?>(initialValue = null, request) {
        value = client.define(request.text, listOfNotNull(request.language, Locale.getDefault().language))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        KeepSystemBarsHidden()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = word.ifEmpty { request.text.trim() },
                style = MaterialTheme.typography.headlineSmall,
            )
            when (val current = result) {
                null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.dictionary_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is DefinitionResult.Found -> current.entries.forEach { EntryBlock(it) }
                is DefinitionResult.NotFound -> {
                    Text(stringResource(R.string.dictionary_not_found), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { searchWeb(context, word.ifEmpty { request.text }) }) {
                        Text(stringResource(R.string.dictionary_search_web))
                    }
                }
                is DefinitionResult.Failed -> {
                    Text(stringResource(R.string.dictionary_failed), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { searchWeb(context, word.ifEmpty { request.text }) }) {
                        Text(stringResource(R.string.dictionary_search_web))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.dictionary_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (word.isNotEmpty()) {
                    TextButton(onClick = { openUrl(context, WiktionaryClient.pageUrl(word)) }) {
                        Text(stringResource(R.string.dictionary_open_wiktionary))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EntryBlock(entry: DictionaryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = listOf(entry.partOfSpeech, entry.language).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        entry.definitions.forEachIndexed { index, definition ->
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "${index + 1}. ${definition.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                )
                definition.examples.forEach { example ->
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun searchWeb(
    context: Context,
    query: String,
) {
    val url =
        Uri
            .Builder()
            .scheme("https")
            .authority("duckduckgo.com")
            .appendQueryParameter("q", "define $query")
            .build()
    openUrl(context, url.toString())
}

private fun openUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The reader hides the status and navigation bars. A sheet is its own window, which shows them again, and when it
 * closes the reader hides them again: the page jumps down and then back while the text re-wraps. Hiding the bars in
 * the sheet's window too leaves them as they were, so the page underneath never moves.
 */
@Composable
private fun KeepSystemBarsHidden() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}
