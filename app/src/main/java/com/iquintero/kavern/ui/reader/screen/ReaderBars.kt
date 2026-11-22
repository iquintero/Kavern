package com.iquintero.kavern.ui.reader.screen

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iquintero.kavern.R
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.ImageReaderMode
import com.iquintero.kavern.core.storage.ReaderPrefs
import com.iquintero.kavern.core.storage.ReaderThemeMode
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.ui.reader.model.ReaderFontOption
import com.iquintero.kavern.ui.reader.model.fontFaceCss
import com.iquintero.kavern.ui.reader.model.readerFontOptions
import com.iquintero.kavern.ui.reader.model.readerThemeOptions
import com.iquintero.kavern.ui.reader.renderer.BaseReader
import com.iquintero.kavern.ui.reader.renderer.PdfReader
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderCallbacks
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderParams
import com.iquintero.kavern.ui.reader.viewmodel.BaseReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ImageReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.PdfReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ReaderUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun TocDialog(
    items: List<ReaderTocItem>,
    currentPage: Int,
    onSelect: (ReaderTocItem) -> Unit,
    onDismiss: () -> Unit,
) {
    // Best guess for "where am I": the first entry of the furthest book page that is not past the current one.
    val currentIndex =
        remember(items, currentPage) {
            val page = items.filter { it.page <= currentPage }.maxOfOrNull { it.page }
            items.indexOfFirst { it.page == page }.takeIf { it >= 0 }
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex ?: 0).coerceAtLeast(0))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_contents)) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(start = (item.depth.coerceAtMost(4) * 16).dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_close))
            }
        },
    )
}

@Composable
internal fun ReaderTopBar(
    title: String,
    pageText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenToc: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pageText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOpenToc != null) {
            IconButton(onClick = onOpenToc) {
                Icon(Icons.Filled.Toc, contentDescription = stringResource(R.string.reader_contents))
            }
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    isLoading: Boolean,
    enableChapterNav: Boolean,
    pageIndex: Int,
    pageCount: Int,
    timeLeft: String?,
    enableSettings: Boolean,
    progress: Float,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
    ) {
        timeLeft?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster: prev chapter, jump top, prev page
            IconButton(onClick = onPrevChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.reader_prev_chapter))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onScrollTop, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_start),
                        modifier = Modifier.rotate(90f),
                    )
                }
                IconButton(onClick = onPrevPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.reader_prev_page))
                }
            }
            // Center: settings
            IconButton(onClick = onOpenSettings, enabled = enableSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_reader_settings))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNextPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.reader_next_page))
                }
                IconButton(onClick = onScrollBottom, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_end),
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }
            IconButton(onClick = onNextChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.reader_next_chapter))
            }
        }
    }
}

data class ReaderBottomBarState(
    val isLoading: Boolean,
    val enableChapterNav: Boolean,
    val pageIndex: Int,
    val pageCount: Int,
    val timeLeft: String?,
    val enableSettings: Boolean,
    val progress: Float = 0f,
)

data class ReaderBottomBarCallbacks(
    val onScrollTop: () -> Unit,
    val onScrollBottom: () -> Unit,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPrevChapter: () -> Unit,
    val onNextChapter: () -> Unit,
)
