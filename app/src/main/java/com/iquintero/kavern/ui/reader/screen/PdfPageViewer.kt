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
internal fun PdfPageViewer(
    pdfPath: String,
    currentPage: Int,
    rightToLeft: Boolean,
    onTap: () -> Unit,
    onPageChanged: (Int, Int) -> Unit,
) {
    val file = remember(pdfPath) { File(pdfPath) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx = remember(configuration.screenWidthDp) { with(density) { configuration.screenWidthDp.dp.roundToPx() } }
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val onTapLatest by rememberUpdatedState(onTap)

    var pageIndex by remember { mutableStateOf(currentPage.coerceAtLeast(0)) }
    var pageCount by remember { mutableStateOf(0) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val rendererHolder = remember { mutableStateOf<Pair<PdfRenderer, ParcelFileDescriptor>?>(null) }

    LaunchedEffect(pdfPath) {
        rendererHolder.value?.let { (renderer, fd) ->
            renderer.close()
            fd.close()
        }
        if (file.exists()) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            rendererHolder.value = renderer to fd
            pageCount = renderer.pageCount
            pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(currentPage) {
        pageIndex = currentPage.coerceAtLeast(0).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    DisposableEffect(rendererHolder.value) {
        val current = rendererHolder.value
        onDispose {
            current?.let { (renderer, fd) ->
                renderer.close()
                fd.close()
            }
        }
    }

    LaunchedEffect(pageIndex, rendererHolder.value) {
        val holder = rendererHolder.value ?: return@LaunchedEffect
        val (renderer, _) = holder
        if (renderer.pageCount == 0) return@LaunchedEffect
        val idx = pageIndex.coerceIn(0, renderer.pageCount - 1)
        withContext(Dispatchers.IO) {
            renderer.openPage(idx).use { page ->
                val scale = max(1f, targetWidthPx.toFloat() / page.width.toFloat())
                val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap =
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                imageBitmap = bitmap.asImageBitmap()
            }
        }
        onPageChanged(idx, renderer.pageCount)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTapLatest() }) }
                    .pointerInput(pageCount, rightToLeft, swipeThresholdPx) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (abs(totalDrag) > swipeThresholdPx) {
                                    val forward = (totalDrag > 0) == rightToLeft
                                    val offset = if (forward) 1 else -1
                                    pageIndex = (pageIndex + offset).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageBitmap != null -> {
                    val ratio = imageBitmap!!.width.toFloat() / imageBitmap!!.height.toFloat()
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio.coerceAtLeast(0.1f))
                                .background(Color.White),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (pageIndex > 0) {
                        pageIndex -= 1
                    }
                },
                enabled = pageIndex > 0,
            ) {
                Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.general_prev))
            }
            Text(
                text = "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = {
                    if (pageIndex < pageCount - 1) {
                        pageIndex += 1
                    }
                },
                enabled = pageIndex < pageCount - 1,
            ) {
                Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.general_next))
            }
        }
    }
}
