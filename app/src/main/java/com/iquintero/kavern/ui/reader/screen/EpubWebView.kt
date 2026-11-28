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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import com.iquintero.kavern.ui.theme.LocalEinkMode
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

internal const val PAGED_TAP_ZONE = 0.3f

/** Touches this close to a screen edge are left to the system back gesture. */
private const val MIN_GESTURE_EDGE_DP = 24
private const val PAGED_DRAG_COMMIT = 0.25f
internal const val PAGED_LEAVE_MS = 120L
internal const val PAGED_LOAD_TIMEOUT_MS = 3000L
private const val PAGED_ENTER_FALLBACK_MS = 3000L

/** Tells the paged script how tall the WebView really is, and logs what the browser thinks, to compare the two. */
private fun reportPagedHeight(
    view: WebView,
    density: Float,
) {
    if (view.height <= 0) return
    val height = view.height / density
    view.evaluateJavascript("(window.__ik && window.__ik.setViewHeight($height));", null)
    val log = { label: String ->
        view.evaluateJavascript("(window.__ik ? window.__ik.diag() : null);") {
            LoggingManager.d("PagedReader", "$label webView=${view.width}x${view.height}px density=$density heightCss=$height js=$it")
        }
    }
    log("start")
    view.postDelayed({ log("after 3s") }, PAGED_DIAG_DELAY_MS)
}

private const val PAGED_DIAG_DELAY_MS = 3000L
private const val PAGED_HEIGHT_SETTLE_MS = 100L
private const val PAGED_ANCHOR_JS = "(window.__ik ? window.__ik.anchorToken() : null);"

@Suppress("LongParameterList")
@Composable
internal fun ReaderWebView(
    html: String,
    fontSize: Float,
    lineHeight: Float,
    paddingPx: Float,
    verticalPaddingPx: Float = 0f,
    fontOption: ReaderFontOption,
    textAlignCss: String,
    themeMode: ReaderThemeMode,
    config: AppConfig?,
    fromOffline: Boolean,
    offlineAssetPaths: Set<String>,
    onToggleOverlay: () -> Unit,
    pendingScrollY: Int?,
    onConsumePendingScroll: () -> Unit = {},
    onWebViewReady: (WebView) -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    pendingScrollId: String? = null,
    onConsumeScrollId: () -> Unit = {},
    onScrollIdle: (String?) -> Unit = {},
    paged: Boolean = false,
    animatePages: Boolean = true,
    openAtEnd: Boolean = false,
    onConsumeOpenAtEnd: () -> Unit = {},
    onPagedChanged: (Int, Int) -> Unit = { _, _ -> },
    insetTopPx: Int = 0,
    insetBottomPx: Int = 0,
    enterDirection: Int = 0,
    onConsumeEnter: () -> Unit = {},
    hasPrevPage: Boolean = false,
    hasNextPage: Boolean = false,
    onLookupWord: (String, String?) -> Unit = { _, _ -> },
) {
    if (html.isBlank()) return
    val context = LocalContext.current
    var loadedContent by remember { mutableStateOf<EpubWebContent?>(null) }
    val content =
        remember(context, html, config, fromOffline, offlineAssetPaths, paged) {
            EpubWebContent(context, html, config, fromOffline, offlineAssetPaths)
        }
    val currentContent by rememberUpdatedState(content)
    val currentScrollY by rememberUpdatedState(pendingScrollY)
    val consumeScroll by rememberUpdatedState(onConsumePendingScroll)
    val currentScrollId by rememberUpdatedState(pendingScrollId)
    val consumeScrollId by rememberUpdatedState(onConsumeScrollId)
    val currentPaged by rememberUpdatedState(paged)
    val currentAnimatePages by rememberUpdatedState(animatePages)
    val currentOpenAtEnd by rememberUpdatedState(openAtEnd)
    val consumeOpenAtEnd by rememberUpdatedState(onConsumeOpenAtEnd)
    val currentOnPagedChanged by rememberUpdatedState(onPagedChanged)
    val currentEnter by rememberUpdatedState(enterDirection)
    val consumeEnter by rememberUpdatedState(onConsumeEnter)
    val currentHasPrev by rememberUpdatedState(hasPrevPage)
    val currentHasNext by rememberUpdatedState(hasNextPage)
    val currentSwipeNext by rememberUpdatedState(onSwipeNext)
    val currentSwipePrev by rememberUpdatedState(onSwipePrev)
    val currentLookup by rememberUpdatedState(onLookupWord)
    val fontFaceCss = fontOption.fontFaceCss().replace("file:///android_asset/fonts/", READER_FONT_URL)
    val (bgColor, textColor) = themeColorsFor(themeMode)
    val cssBlock =
        readerCss(
            ReaderCssSettings(
                fontFaceCss = fontFaceCss,
                fontFamily = fontOption.cssFamily,
                fontSize = fontSize,
                lineHeight = lineHeight,
                paddingPx = paddingPx,
                verticalPaddingPx = verticalPaddingPx,
                textAlignCss = textAlignCss,
                backgroundColor = bgColor,
                textColor = textColor,
                paged = paged,
                solidSelection = LocalEinkMode.current,
            ),
        )
    val styleJs =
        """
        (function() {
            var tag = document.querySelector('head > style#reader-style');
            var css = ${org.json.JSONObject.quote(cssBlock)};
            if (!tag || tag.textContent === css) return;
            tag.textContent = css;
            if (window.__ik) window.__ik.relayout();
        })();
        """.trimIndent()
    val currentStyle by rememberUpdatedState(styleJs)
    val scrollHandler = remember { Handler(Looper.getMainLooper()) }
    var scrollRunnable: Runnable? by remember { mutableStateOf(null) }

    val density = LocalDensity.current
    val nextPageLabel = stringResource(R.string.reader_next_page)
    val previousPageLabel = stringResource(R.string.reader_prev_page)
    val pageBackground = runCatching { Color(android.graphics.Color.parseColor(bgColor)) }.getOrDefault(Color.Transparent)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(if (paged) Modifier.background(pageBackground) else Modifier),
    ) {
        AndroidView(
            // Paged mode keeps the page between the top and bottom bars so the bars never hide text.
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (paged) {
                            // Tap zones and swipes are WebView touch gestures, so give screen readers real actions.
                            Modifier
                                .semantics {
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction(nextPageLabel) {
                                                currentSwipeNext()
                                                true
                                            },
                                            CustomAccessibilityAction(previousPageLabel) {
                                                currentSwipePrev()
                                                true
                                            },
                                        )
                                }.padding(
                                    top = with(density) { (insetTopPx + verticalPaddingPx).toDp() },
                                    bottom = with(density) { (insetBottomPx + verticalPaddingPx).toDp() },
                                )
                        } else {
                            Modifier
                        },
                    ),
            factory = { context ->
                val swipeThreshold = 120
                val swipeVelocityThreshold = 200
                var tappedView: WebView? = null
                var dragging = false
                var flung = false
                // A tap that only dismisses a text selection, a long press that starts one, or a touch that starts in the
                // system back-gesture edge is not a page gesture.
                var selectingAtDown = false
                var longPressed = false
                var dragDx = 0f
                val density = context.resources.displayMetrics.density
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                val gestureDetector =
                    GestureDetector(
                        context,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onLongPress(e: MotionEvent) {
                                longPressed = true
                            }

                            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                if (selectingAtDown) return true
                                val width = tappedView?.width ?: 0
                                if (currentPaged && width > 0) {
                                    when {
                                        e.x < width * PAGED_TAP_ZONE -> currentSwipePrev()
                                        e.x > width * (1f - PAGED_TAP_ZONE) -> currentSwipeNext()
                                        else -> onToggleOverlay()
                                    }
                                } else {
                                    onToggleOverlay()
                                }
                                return true
                            }

                            override fun onFling(
                                e1: MotionEvent?,
                                e2: MotionEvent,
                                velocityX: Float,
                                velocityY: Float,
                            ): Boolean {
                                if (e1 == null || selectingAtDown || longPressed) return false
                                val diffX = e2.x - e1.x
                                val diffY = e2.y - e1.y
                                if (abs(diffX) > abs(diffY) &&
                                    abs(diffX) > swipeThreshold &&
                                    abs(velocityX) > swipeVelocityThreshold
                                ) {
                                    flung = true
                                    if (diffX < 0) currentSwipeNext() else currentSwipePrev()
                                    return true
                                }
                                return false
                            }

                            override fun onScroll(
                                e1: MotionEvent?,
                                e2: MotionEvent,
                                distanceX: Float,
                                distanceY: Float,
                            ): Boolean {
                                if (!currentPaged || e1 == null || selectingAtDown || longPressed) return false
                                val totalX = e2.x - e1.x
                                val totalY = e2.y - e1.y
                                if (!dragging && abs(totalX) > touchSlop * 2 && abs(totalX) > abs(totalY)) dragging = true
                                if (!dragging) return false
                                dragDx = totalX
                                if (currentAnimatePages) {
                                    tappedView?.evaluateJavascript(
                                        "(window.__ik && window.__ik.drag(${totalX / density}, $currentHasPrev, $currentHasNext));",
                                        null,
                                    )
                                }
                                return true
                            }
                        },
                    )
                SelectionWebView(context).apply {
                    tappedView = this
                    onDefine = { text, language -> currentLookup(text, language) }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onPage(
                                current: Int,
                                total: Int,
                            ) {
                                post { currentOnPagedChanged(current, total) }
                            }
                        },
                        PAGED_BRIDGE_NAME,
                    )
                    configureEpubWebView({ currentContent }) { view ->
                        view.evaluateJavascript(currentStyle, null)
                        if (currentPaged) {
                            // The view was hidden while loading so the new page can slide in without a flash.
                            val enter = currentEnter
                            view.evaluateJavascript(pagedStartJs(currentScrollId, currentOpenAtEnd, enter, currentAnimatePages)) { view.alpha = 1f }
                            reportPagedHeight(view, density)
                            consumeEnter()
                            consumeScrollId()
                            consumeOpenAtEnd()
                            currentScrollY?.let { consumeScroll() }
                            return@configureEpubWebView
                        }
                        view.alpha = 1f
                        currentScrollId?.let { id ->
                            view.evaluateJavascript(epubScrollToElementJs(id), null)
                            consumeScrollId()
                        }
                        currentScrollY?.let { y ->
                            view.post {
                                view.scrollTo(0, y)
                                view.postDelayed({ view.scrollTo(0, y) }, 50)
                            }
                            consumeScroll()
                        }
                    }
                    setOnTouchListener { view, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            val gestureEdge =
                                maxOf(
                                    ViewCompat
                                        .getRootWindowInsets(view)
                                        ?.getInsets(WindowInsetsCompat.Type.systemGestures())
                                        ?.let { maxOf(it.left, it.right) } ?: 0,
                                    (MIN_GESTURE_EDGE_DP * density).toInt(),
                                )
                            val inGestureEdge = event.x < gestureEdge || event.x > view.width - gestureEdge
                            selectingAtDown = (view as? SelectionWebView)?.isSelecting == true || inGestureEdge
                            longPressed = false
                        }
                        gestureDetector.onTouchEvent(event)
                        val ended = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
                        if (currentPaged && ended) {
                            if (dragging && !flung) {
                                if (event.actionMasked == MotionEvent.ACTION_UP && abs(dragDx) > view.width * PAGED_DRAG_COMMIT) {
                                    if (dragDx < 0) currentSwipeNext() else currentSwipePrev()
                                } else {
                                    evaluateJavascript("(window.__ik && window.__ik.settle());", null)
                                }
                            }
                            dragging = false
                            flung = false
                            dragDx = 0f
                        }
                        // Always let the WebView see the touches so text can be selected. Paged mode's page is not
                        // scrollable by touch (see ik.start), so it will not move under the finger.
                        false
                    }
                    var heightReport: Runnable? = null
                    addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
                        if (currentPaged && bottom - top != oldBottom - oldTop) {
                            // A window that keeps changing size (system bars, rotation) can report dozens of heights a second,
                            // each one a script run on the main thread. Only the height it settles on is passed on.
                            heightReport?.let { view.removeCallbacks(it) }
                            heightReport = Runnable { reportPagedHeight(view as WebView, density) }.also { view.postDelayed(it, PAGED_HEIGHT_SETTLE_MS) }
                        }
                    }
                    setOnScrollChangeListener { _, _, _, _, _ ->
                        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                        scrollRunnable =
                            Runnable {
                                val script = if (currentPaged) PAGED_ANCHOR_JS else EPUB_SCROLL_ANCHOR_JS
                                evaluateJavascript(script) { result ->
                                    val id = result?.let { org.json.JSONTokener(it).nextValue() as? String }
                                    onScrollIdle(id)
                                }
                            }
                        scrollHandler.postDelayed(scrollRunnable!!, 300)
                    }
                    onWebViewReady(this)
                }
            },
            update = { webView ->
                webView.setBackgroundColor(Color.Transparent.toArgb())
                // Paged mode moves the page itself; scrollbars would only flash during the slide.
                webView.isHorizontalScrollBarEnabled = !paged
                webView.isVerticalScrollBarEnabled = !paged
                if (loadedContent !== content) {
                    loadedContent = content
                    if (paged && enterDirection != 0) {
                        // Keep the incoming page invisible until it is positioned and ready to slide in.
                        webView.alpha = 0f
                        webView.postDelayed({ webView.alpha = 1f }, PAGED_ENTER_FALLBACK_MS)
                    }
                    content.load(webView, cssBlock)
                } else {
                    webView.evaluateJavascript(styleJs, null)
                }
                if (paged) {
                    // Paged mode keeps its own horizontal position; a vertical scroll offset would reset it.
                    pendingScrollY?.let { onConsumePendingScroll() }
                } else if (content.isDocumentUrl(webView.url) && webView.progress == 100) {
                    pendingScrollY?.let { y ->
                        webView.post {
                            webView.scrollTo(0, y)
                        }
                        webView.postDelayed({ webView.scrollTo(0, y) }, 50)
                        onConsumePendingScroll()
                    }
                }
            },
            onRelease = { webView -> releaseEpubWebView(webView, scrollHandler, scrollRunnable) },
        )
    }
}

/**
 * Frees a reader WebView. `AndroidView` only detaches its view when it leaves the screen, so without this the page,
 * the script bridge and the pending scroll callback would stay alive holding the activity.
 */
internal fun releaseEpubWebView(
    view: WebView,
    handler: Handler,
    pendingScroll: Runnable?,
) {
    pendingScroll?.let(handler::removeCallbacks)
    view.stopLoading()
    view.removeJavascriptInterface(PAGED_BRIDGE_NAME)
    view.setOnTouchListener(null)
    view.destroy()
}
