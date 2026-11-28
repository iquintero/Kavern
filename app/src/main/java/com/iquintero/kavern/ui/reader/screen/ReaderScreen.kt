package com.iquintero.kavern.ui.reader.screen

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.grimmory.isGrimmory
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.ReaderPrefs
import com.iquintero.kavern.core.storage.ReaderThemeMode
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.ui.reader.model.readerFontOptions
import com.iquintero.kavern.ui.reader.renderer.BaseReader
import com.iquintero.kavern.ui.reader.renderer.PdfReader
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderCallbacks
import com.iquintero.kavern.ui.reader.renderer.ReaderRenderParams
import com.iquintero.kavern.ui.reader.viewmodel.BaseReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ImageReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.PdfReaderViewModel
import com.iquintero.kavern.ui.reader.viewmodel.ReaderUiState
import com.iquintero.kavern.ui.theme.LocalEinkMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun ReaderScreen(
    chapterId: Int,
    initialPage: Int?,
    readerRepository: ReaderRepository,
    appPreferences: AppPreferences,
    seriesId: Int?,
    volumeId: Int?,
    serverUrl: String?,
    apiKey: String? = null,
    formatId: Int? = null,
    onBack: (chapterId: Int, page: Int, seriesId: Int?, volumeId: Int?) -> Unit = { _, _, _, _ -> },
    onNavigateToChapter: (Int, Int?, Int?, Int?) -> Unit = { _, _, _, _ -> },
) {
    val format = Format.fromId(formatId)
    if (format == Format.Pdf) {
        PdfReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    } else if (format == Format.Image || format == Format.Archive) {
        ImageReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    } else {
        EpubReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun BaseReaderScreen(
    chapterId: Int,
    initialPage: Int?,
    readerViewModel: BaseReaderViewModel,
    appPreferences: AppPreferences,
    seriesId: Int?,
    volumeId: Int?,
    serverUrl: String?,
    apiKey: String? = null,
    renderer: BaseReader,
    onBack: (chapterId: Int, page: Int, seriesId: Int?, volumeId: Int?) -> Unit = { _, _, _, _ -> },
    onNavigateToChapter: (Int, Int?, Int?, Int?) -> Unit = { _, _, _, _ -> },
    topBarContent: (@Composable (String, String, () -> Unit) -> Unit)? = null,
    bottomBarContent: (@Composable (ReaderBottomBarState, ReaderBottomBarCallbacks) -> Unit)? = null,
    settingsContent: (@Composable (ReaderSettingsState, ReaderSettingsCallbacks) -> Unit)? = null,
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    val uiState by readerViewModel.state.collectAsState()
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    var isLeavingReader by remember { mutableStateOf(false) }

    val hostView = LocalView.current
    val hostWindow = (hostView.context as? Activity)?.window
    val leaveReader: () -> Unit = {
        if (!isLeavingReader) {
            isLeavingReader = true
            scope.launch {
                // Bringing the system bars back resizes the window and would visibly shift the page while the
                // screen transitions away. Cover the page first, then restore them before navigating.
                withFrameNanos { }
                // Record where the reader is before the bars come back: that resizes the page, and the paged-mode
                // script then re-flows the text and can land on an earlier page.
                runCatchingCancellable { readerViewModel.saveCurrentProgress() }
                val latestState = readerViewModel.state.value
                val barsWereHidden =
                    ViewCompat.getRootWindowInsets(hostView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == false
                hostWindow?.let { window ->
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
                // The screen we return to is padded by the bars, so let them finish sliding in before it appears.
                if (barsWereHidden) awaitInsetsAnimationEnd(hostView)
                withFrameNanos { }
                onBack(
                    latestState.activeChapterId ?: chapterId,
                    latestState.pageIndex,
                    latestState.bookInfo?.seriesId ?: seriesId,
                    latestState.bookInfo?.volumeId ?: volumeId,
                )
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            readerViewModel.clearError()
        }
    }

    BackHandler {
        leaveReader()
    }
    var pendingScrollY by remember { mutableStateOf<Int?>(null) }
    var pendingScrollId by remember { mutableStateOf<String?>(null) }
    val readerPrefs by appPreferences.readerPrefsFlow.collectAsState(initial = ReaderPrefs())
    val config by appPreferences.configFlow.collectAsState(initial = null)
    val fontOptions = remember { readerFontOptions }

    var fontSize by remember { mutableStateOf(18f) }
    var lineHeight by remember { mutableStateOf(1.4f) }
    var padding by remember { mutableStateOf(5.dp) }
    var paddingVertical by remember { mutableStateOf(0.dp) }
    var fontFamilyId by remember { mutableStateOf(ReaderPrefs.DEFAULT_FONT_FAMILY) }
    var textAlign by remember { mutableStateOf(TextAlign.Justify) }
    var themeMode by remember { mutableStateOf(ReaderThemeMode.Light) }
    var showNextChapterDialog by remember { mutableStateOf(false) }
    var pendingNextChapter by remember { mutableStateOf<com.iquintero.kavern.domain.model.ReaderChapterNav?>(null) }
    val activity = LocalView.current.context as? Activity
    var showOverlay by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    // Back closes the settings first; it used to leave the book with the panel still open.
    BackHandler(enabled = showSettingsPanel) { showSettingsPanel = false }
    var showToc by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf<LookupRequest?>(null) }
    // Last measured overlay bar heights; kept while the bars are hidden so paged layout stays stable.
    var topBarPx by remember { mutableStateOf(0) }
    var bottomBarPx by remember { mutableStateOf(0) }
    var pagedPage by remember { mutableStateOf(0) }
    var pagedTotal by remember { mutableStateOf(0) }
    // Direction of an in-progress turn to a neighbouring book page (1 forward, -1 back, 0 none).
    var enterDirection by remember { mutableStateOf(0) }
    var selectedSettingsTab by remember { mutableStateOf(ReaderSettingsTab.Font) }
    val selectedFont = fontOptions.firstOrNull { it.id == fontFamilyId } ?: readerFontOptions.first()
    val isImageReader = renderer == com.iquintero.kavern.ui.reader.renderer.ImageReader
    val isPdfReader = renderer == PdfReader
    val supportsReaderSettings = renderer.supportsTextSettings || isImageReader || isPdfReader
    val pagedEnabled = readerPrefs.epubPaged && renderer === com.iquintero.kavern.ui.reader.renderer.EpubReader
    val networkStatus by remember { NetworkMonitor.getInstance(context, appPreferences) }.status.collectAsState()
    val serverUnreachable by ServerReachability.unreachable.collectAsState()
    val withoutServer = !networkStatus.isOnlineAllowed || serverUnreachable
    LaunchedEffect(uiState.pageIndex, pagedEnabled) {
        pagedPage = 0
        pagedTotal = 0
    }

    LaunchedEffect(readerPrefs) {
        fontSize = readerPrefs.fontSize
        lineHeight = readerPrefs.lineHeight
        padding = readerPrefs.paddingDp.dp
        paddingVertical = readerPrefs.paddingVerticalDp.dp
        textAlign = readerPrefs.textAlign
        fontFamilyId = readerPrefs.fontFamily
        themeMode = readerPrefs.readerTheme
    }
    LaunchedEffect(uiState.bookScrollId) {
        pendingScrollId = uiState.bookScrollId
    }

    DisposableEffect(activity) {
        var controller: WindowInsetsControllerCompat? = null
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller =
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
        }
        onDispose {
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Keep the screen awake while the reader is open, so it doesn't dim mid-page and need a tap to wake.
    val view = LocalView.current
    DisposableEffect(view, readerPrefs.keepScreenOn) {
        view.keepScreenOn = readerPrefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val goPrevPageWith: (Boolean) -> Unit = goPrevPage@{ openAtEnd ->
        if (uiState.pageIndex <= 0) {
            scope.launch {
                val nav = readerViewModel.getPreviousChapter()
                val chId = nav?.chapterId
                if (chId != null && chId >= 0) {
                    val info = readerViewModel.getBookInfoFor(chId)
                    val targetPage =
                        info
                            ?.pages
                            ?.takeIf { it > 0 }
                            ?.let { it - 1 }
                            ?: nav.pagesRead
                            ?: 0
                    onNavigateToChapter(
                        chId,
                        targetPage,
                        nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                        nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                    )
                } else {
                    Toast
                        .makeText(
                            context,
                            context.resources.getString(R.string.reader_no_previous_chapter),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
            return@goPrevPage
        }
        val prev = (uiState.pageIndex - 1).coerceAtLeast(0)
        if (uiState.isPdf) {
            (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(prev, uiState.pageCount)
        } else {
            scope.launch {
                if (!NetworkUtils.isOnline(context) && !readerViewModel.isPageDownloaded(prev)) {
                    Toast.makeText(context, context.getString(R.string.reader_page_not_downloaded), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                readerViewModel.loadPage(prev, null, openAtEnd)
            }
        }
    }
    val goPrevPage: () -> Unit = { goPrevPageWith(false) }
    val goNextPage: () -> Unit = goNextPage@{
        if (uiState.pageCount > 0 && uiState.pageIndex >= uiState.pageCount - 1) {
            scope.launch {
                val nav = readerViewModel.getNextChapter()
                val chId = nav?.chapterId
                if (chId != null && chId >= 0) {
                    onNavigateToChapter(
                        chId,
                        0,
                        nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                        nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                    )
                } else {
                    Toast.makeText(context, context.resources.getString(R.string.reader_no_next_chapter), Toast.LENGTH_SHORT).show()
                }
            }
            return@goNextPage
        }
        val next =
            if (uiState.pageCount > 0) {
                (uiState.pageIndex + 1).coerceAtMost((uiState.pageCount - 1).coerceAtLeast(0))
            } else {
                uiState.pageIndex + 1 // When page count is unknown (offline), allow attempting the next page.
            }
        if (uiState.isPdf) {
            (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(next, uiState.pageCount)
        } else {
            scope.launch {
                if (!NetworkUtils.isOnline(context) && !readerViewModel.isPageDownloaded(next)) {
                    Toast.makeText(context, context.getString(R.string.reader_page_not_downloaded), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                readerViewModel.loadPage(next)
            }
        }
    }

    // Paged mode: turn a screen inside the current book page, and only load another page at its edges.
    val pagedTurn: (Boolean) -> Unit = turn@{ forward ->
        val web = webViewRef.value
        if (web == null || uiState.isLoading) {
            web?.evaluateJavascript("(window.__ik && window.__ik.settle());", null)
            return@turn
        }
        val call = if (forward) "next" else "prev"
        web.evaluateJavascript("(window.__ik ? (window.__ik.$call() ? 1 : 0) : -1);") { result ->
            when (result) {
                "1", "-1" -> Unit
                else -> {
                    val neighbourPage =
                        if (forward) {
                            !(uiState.pageCount > 0 && uiState.pageIndex >= uiState.pageCount - 1)
                        } else {
                            uiState.pageIndex > 0
                        }
                    if (neighbourPage) {
                        // Slide this page away, load the neighbour (usually already prefetched), and let it slide in.
                        val direction = if (forward) 1 else -1
                        val startPage = uiState.pageIndex
                        enterDirection = direction
                        web.evaluateJavascript("(window.__ik && window.__ik.leave($direction));", null)
                        scope.launch {
                            delay(PAGED_LEAVE_MS)
                            if (forward) goNextPage() else goPrevPageWith(true)
                            delay(PAGED_LOAD_TIMEOUT_MS)
                            if (uiState.pageIndex == startPage) {
                                // The page did not change (offline, error): bring the current one back.
                                enterDirection = 0
                                web.evaluateJavascript("(window.__ik && window.__ik.recover());", null)
                            }
                        }
                    } else {
                        web.evaluateJavascript("(window.__ik && window.__ik.settle());", null)
                        if (forward) goNextPage() else goPrevPageWith(true)
                    }
                }
            }
        }
    }
    // Hardware page buttons (and, if enabled, the volume keys) turn pages while the reader is open.
    val volumeKeysTurnPages by rememberUpdatedState(readerPrefs.volumeKeysTurnPages)
    val volumeKeysReversed by rememberUpdatedState(readerPrefs.volumeKeysReversed)
    val turnPageByKey by rememberUpdatedState<(Boolean) -> Unit> { forward ->
        if (pagedEnabled) {
            pagedTurn(forward)
        } else if (forward) {
            goNextPage()
        } else {
            goPrevPage()
        }
    }
    // Progress waiting for a pause in reading is sent when the app leaves the screen, which may be its last chance.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, readerViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> readerViewModel.flushPendingProgress()
                    Lifecycle.Event.ON_START -> readerViewModel.onScreenResumed()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        ReaderKeyEvents.onKey = onKey@{ keyCode, isNewPress ->
            // With the settings open the keys must not turn pages behind them.
            if (showSettingsPanel) return@onKey false
            val forward = pageTurnForKey(keyCode, volumeKeysTurnPages, volumeKeysReversed) ?: return@onKey false
            if (isNewPress) turnPageByKey(forward)
            true
        }
        onDispose { ReaderKeyEvents.onKey = null }
    }
    val openTocItem: (ReaderTocItem) -> Unit = { item ->
        showToc = false
        val token = item.anchor?.let { "id(\"$it\")" }
        if (item.page == uiState.pageIndex) {
            val web = webViewRef.value
            val script =
                when {
                    pagedEnabled -> if (token != null) pagedGoToTokenJs(token) else "(window.__ik && window.__ik.go(0));"
                    token != null -> epubScrollToElementJs(token)
                    else -> "window.scrollTo(0, 0);"
                }
            web?.evaluateJavascript(script, null)
        } else {
            scope.launch {
                if (!NetworkUtils.isOnline(context) && !readerViewModel.isPageDownloaded(item.page)) {
                    Toast.makeText(context, context.getString(R.string.reader_page_not_downloaded), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                readerViewModel.loadPage(item.page, token)
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Keep container transparent; page colors come from reader theme CSS.
                .background(Color.Transparent),
    ) {
        renderer.Content(
            params =
                ReaderRenderParams(
                    uiState = uiState,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    config = config,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    padding = padding,
                    paddingVertical = paddingVertical,
                    fontOption = selectedFont,
                    textAlign = textAlign,
                    themeMode = themeMode,
                    pendingScrollY = pendingScrollY,
                    pendingScrollId = pendingScrollId,
                    imageReaderMode = readerPrefs.imageReaderMode,
                    imagePrefetchPages = readerPrefs.imagePrefetchPages,
                    pdfRightToLeft = readerPrefs.pdfRightToLeft,
                    epubPaged = pagedEnabled,
                    pageAnimations = !readerPrefs.einkMode,
                    openAtEnd = uiState.openAtEnd,
                    insetTopPx = topBarPx,
                    insetBottomPx = bottomBarPx,
                    enterDirection = enterDirection,
                    hasPrevPage = uiState.pageIndex > 0,
                    hasNextPage = !(uiState.pageCount > 0 && uiState.pageIndex >= uiState.pageCount - 1),
                ),
            callbacks =
                ReaderRenderCallbacks(
                    onToggleOverlay = {
                        if (showSettingsPanel) {
                            showSettingsPanel = false
                        } else {
                            showOverlay = !showOverlay
                        }
                    },
                    onSwipeNext = { if (pagedEnabled) pagedTurn(true) else goNextPage() },
                    onSwipePrev = { if (pagedEnabled) pagedTurn(false) else goPrevPage() },
                    onImagePageVisible = { visibleChapterId, pageIndex, prefetchPages ->
                        (readerViewModel as? ImageReaderViewModel)?.onWebtoonPageVisible(
                            chapterId = visibleChapterId,
                            pageIndex = pageIndex,
                            prefetchPages = prefetchPages,
                        )
                    },
                    onWebtoonChapterBoundaryVisible = { currentChapterId, prefetchPages ->
                        (readerViewModel as? ImageReaderViewModel)?.onWebtoonChapterBoundaryVisible(
                            chapterId = currentChapterId,
                            prefetchPages = prefetchPages,
                        )
                    },
                    onConsumePendingScroll = { pendingScrollY = null },
                    onConsumeScrollId = { pendingScrollId = null },
                    onWebViewReady = { webViewRef.value = it },
                    onScrollIdle = { scrollId ->
                        // Ignore the re-flow caused by the system bars returning while the reader is being left.
                        if (!isLeavingReader) readerViewModel.markProgressAtCurrentPage(scrollId)
                    },
                    onPdfPageChanged = { idx, count ->
                        (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(idx, count)
                    },
                    onEpubPageChanged = { current, total ->
                        pagedPage = current
                        pagedTotal = total
                        // Fetch the neighbouring book page while the reader is still on the near edge of this one.
                        if (total > 0) {
                            val hasNext = !(uiState.pageCount > 0 && uiState.pageIndex >= uiState.pageCount - 1)
                            if (current >= total - 2 && hasNext) readerViewModel.prefetchPage(uiState.pageIndex + 1)
                            if (current <= 1 && uiState.pageIndex > 0) readerViewModel.prefetchPage(uiState.pageIndex - 1)
                        }
                    },
                    onConsumeEnter = { enterDirection = 0 },
                    onConsumeOpenAtEnd = { readerViewModel.consumeOpenAtEnd() },
                    onLookupWord = { text, language -> lookup = LookupRequest(text, language) },
                ),
        )

        if (isLeavingReader) {
            val (coverColor, _) = themeColorsFor(themeMode)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(runCatching { Color(android.graphics.Color.parseColor(coverColor)) }.getOrDefault(Color.Black)),
            )
        }
        if (showOverlay && !isLeavingReader) {
            val barTitle = uiState.bookInfo?.title ?: stringResource(R.string.reader_title)
            // Kavita's "pages" of an EPUB are the book's documents (normally one per chapter), so name them
            // that way; the screen-sized pages of paged mode are the real pages.
            val isEpub = renderer === com.iquintero.kavern.ui.reader.renderer.EpubReader
            val unitLabel = stringResource(if (isEpub) R.string.reader_chapter_label else R.string.general_page)
            val basePageText =
                pageText(uiState.pageIndex, uiState.pageCount, uiState.bookInfo?.pageTitle, unitLabel)
            val barPageText =
                if (config?.isGrimmory == true) {
                    // A Grimmory book has no pages or chapters to count, only how far along it is.
                    val within = if (pagedEnabled && pagedTotal > 0) (pagedPage + 1f) / pagedTotal else 0f
                    val percent = if (uiState.pageCount > 0) ((uiState.pageIndex + within) * 100 / uiState.pageCount).toInt() else 0
                    listOfNotNull(uiState.bookInfo?.pageTitle?.takeIf { it.isNotBlank() }, "$percent%").joinToString(" · ")
                } else if (pagedEnabled && pagedTotal > 0) {
                    "$basePageText · ${stringResource(R.string.general_page)} ${pagedPage + 1}/$pagedTotal"
                } else {
                    basePageText
                }
            if (topBarContent != null) {
                topBarContent(barTitle, barPageText, leaveReader)
            } else {
                ReaderTopBar(
                    title = barTitle,
                    pageText = barPageText,
                    onBack = leaveReader,
                    onOpenToc = if (uiState.toc.isNotEmpty()) ({ showToc = true }) else null,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .onSizeChanged { if (it.height > 0) topBarPx = it.height },
                )
            }

            val bottomBarState =
                ReaderBottomBarState(
                    isLoading = uiState.isLoading,
                    enableChapterNav = uiState.bookInfo != null && !uiState.isLoading,
                    pageIndex = uiState.pageIndex,
                    pageCount = uiState.pageCount,
                    timeLeft = uiState.timeLeftText(context),
                    enableSettings = supportsReaderSettings,
                    progress =
                        if (uiState.pageCount > 0) {
                            val within = if (pagedEnabled && pagedTotal > 0) (pagedPage + 1f) / pagedTotal else 1f
                            (uiState.pageIndex + within) / uiState.pageCount
                        } else {
                            0f
                        },
                )
            val bottomBarCallbacks =
                ReaderBottomBarCallbacks(
                    onScrollTop = {
                        if (renderer == com.iquintero.kavern.ui.reader.renderer.ImageReader) {
                            if (uiState.pageCount > 0) {
                                readerViewModel.loadPage(0)
                            } else {
                                readerViewModel.loadPage(0)
                            }
                        } else if (uiState.isPdf) {
                            if (uiState.pageCount > 0) {
                                (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(0, uiState.pageCount)
                            }
                        } else {
                            webViewRef.value?.let { web ->
                                web.post {
                                    if (pagedEnabled) {
                                        web.evaluateJavascript("(window.__ik && window.__ik.go(0));", null)
                                    } else {
                                        web.scrollTo(0, 0)
                                    }
                                }
                            }
                        }
                    },
                    onScrollBottom = {
                        if (renderer == com.iquintero.kavern.ui.reader.renderer.ImageReader) {
                            if (uiState.pageCount > 0) {
                                readerViewModel.loadPage((uiState.pageCount - 1).coerceAtLeast(0))
                            }
                        } else if (uiState.isPdf) {
                            if (uiState.pageCount > 0) {
                                (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(uiState.pageCount - 1, uiState.pageCount)
                            }
                        } else {
                            webViewRef.value?.let { web ->
                                web.post {
                                    if (pagedEnabled) {
                                        web.evaluateJavascript("(window.__ik && window.__ik.last());", null)
                                    } else {
                                        val bottom = ((web.contentHeight * web.scale) - web.height).toInt().coerceAtLeast(0)
                                        web.scrollTo(0, bottom)
                                    }
                                }
                            }
                        }
                    },
                    onPrevPage = { if (pagedEnabled) pagedTurn(false) else goPrevPage() },
                    onNextPage = { if (pagedEnabled) pagedTurn(true) else goNextPage() },
                    onOpenSettings = { if (supportsReaderSettings) showSettingsPanel = !showSettingsPanel },
                    onPrevChapter = {
                        scope.launch {
                            val nav = readerViewModel.getPreviousChapter()
                            val chId = nav?.chapterId
                            if (chId != null && chId >= 0) {
                                onNavigateToChapter(
                                    chId,
                                    nav.pagesRead ?: 0,
                                    nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                    nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                                )
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.resources.getString(R.string.reader_no_previous_chapter),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    },
                    onNextChapter = {
                        scope.launch {
                            val nav = readerViewModel.getNextChapter()
                            val chId = nav?.chapterId
                            if (chId != null && chId >= 0) {
                                if (uiState.pageCount > 0 && uiState.pageIndex < uiState.pageCount - 1) {
                                    pendingNextChapter = nav
                                    showNextChapterDialog = true
                                } else {
                                    onNavigateToChapter(
                                        chId,
                                        nav.pagesRead ?: 0,
                                        nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                        nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                                    )
                                }
                            } else {
                                Toast.makeText(context, context.resources.getString(R.string.reader_no_next_chapter), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            if (bottomBarContent != null) {
                bottomBarContent(bottomBarState, bottomBarCallbacks)
            } else {
                ReaderBottomBar(
                    isLoading = bottomBarState.isLoading,
                    enableChapterNav = bottomBarState.enableChapterNav,
                    pageIndex = bottomBarState.pageIndex,
                    pageCount = bottomBarState.pageCount,
                    timeLeft = bottomBarState.timeLeft,
                    progress = bottomBarState.progress,
                    onScrollTop = bottomBarCallbacks.onScrollTop,
                    onScrollBottom = bottomBarCallbacks.onScrollBottom,
                    onPrevPage = bottomBarCallbacks.onPrevPage,
                    onNextPage = bottomBarCallbacks.onNextPage,
                    onOpenSettings = bottomBarCallbacks.onOpenSettings,
                    onPrevChapter = bottomBarCallbacks.onPrevChapter,
                    onNextChapter = bottomBarCallbacks.onNextChapter,
                    enableSettings = bottomBarState.enableSettings,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { if (it.height > 0) bottomBarPx = it.height },
                )
            }

            val einkOverlays = LocalEinkMode.current
            // Just below the top bar, not over the line of text it holds.
            val offlineNoticeTop = if (topBarPx > 0) with(LocalDensity.current) { topBarPx.toDp() } + 6.dp else 40.dp
            AnimatedVisibility(
                // Shown while the server cannot be used, not merely because the page came from a download.
                visible = withoutServer,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = offlineNoticeTop),
                enter = if (einkOverlays) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (einkOverlays) ExitTransition.None else fadeOut() + shrinkVertically(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.reader_offline_indicator), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary)
                }
            }

            if (supportsReaderSettings && showSettingsPanel) {
                // Catches taps outside the settings so they close it instead of turning the page underneath.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                                indication = null,
                                onClickLabel = stringResource(R.string.general_close),
                            ) { showSettingsPanel = false },
                )
            }

            AnimatedVisibility(
                visible = supportsReaderSettings && showSettingsPanel,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp), // raise above bottom bar
                enter = if (einkOverlays) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (einkOverlays) ExitTransition.None else fadeOut() + shrinkVertically(),
            ) {
                val settingsState =
                    ReaderSettingsState(
                        selectedTab = selectedSettingsTab,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        padding = padding,
                        paddingVertical = paddingVertical,
                        themeMode = themeMode,
                        textAlign = textAlign,
                        fontOptions = fontOptions,
                        fontFamilyId = selectedFont.id,
                        imageReaderMode = readerPrefs.imageReaderMode,
                        epubPaged = readerPrefs.epubPaged,
                    )
                val settingsCallbacks =
                    ReaderSettingsCallbacks(
                        onSelectTab = { selectedSettingsTab = it },
                        onFontSizeChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(8f, 36f)
                            fontSize = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(fontSize = new) } }
                        },
                        onLineHeightChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(0.8f, 2.5f)
                            lineHeight = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(lineHeight = new) } }
                        },
                        onPaddingChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(0.dp, 32.dp)
                            padding = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(paddingDp = new.value) } }
                        },
                        onPaddingVerticalChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(ReaderPrefs.MIN_VERTICAL_PADDING_DP.dp, ReaderPrefs.MAX_VERTICAL_PADDING_DP.dp)
                            paddingVertical = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(paddingVerticalDp = new.value) } }
                        },
                        onThemeChange = { mode ->
                            themeMode = mode
                            scope.launch { appPreferences.updateReaderPrefs { copy(readerTheme = mode) } }
                        },
                        onTextAlignChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            textAlign = it
                            scope.launch { appPreferences.updateReaderPrefs { copy(textAlign = it) } }
                        },
                        onFontFamilyChange = { option ->
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            fontFamilyId = option.id
                            scope.launch {
                                appPreferences.updateReaderPrefs {
                                    copy(
                                        fontFamily = option.id,
                                        useSerif = option.isSerif,
                                    )
                                }
                            }
                        },
                        onImageReaderModeChange = { mode ->
                            scope.launch { appPreferences.updateReaderPrefs { copy(imageReaderMode = mode) } }
                        },
                        onEpubPagedChange = { enabled ->
                            scope.launch { appPreferences.updateReaderPrefs { copy(epubPaged = enabled) } }
                        },
                    )
                if (settingsContent != null) {
                    settingsContent(settingsState, settingsCallbacks)
                } else if (isImageReader) {
                    ReaderSettingsPanel(onClose = { showSettingsPanel = false }) {
                        ImageReadingModeSettings(
                            mode = settingsState.imageReaderMode,
                            onModeChange = settingsCallbacks.onImageReaderModeChange,
                        )
                    }
                } else if (isPdfReader) {
                    ReaderSettingsPanel(onClose = { showSettingsPanel = false }) {
                        PdfReadingDirectionSettings(
                            rightToLeft = readerPrefs.pdfRightToLeft,
                            onChange = { rightToLeft ->
                                scope.launch { appPreferences.updateReaderPrefs { copy(pdfRightToLeft = rightToLeft) } }
                            },
                        )
                    }
                } else {
                    SettingsPanel(
                        selectedTab = settingsState.selectedTab,
                        onSelectTab = settingsCallbacks.onSelectTab,
                        fontSize = settingsState.fontSize,
                        onFontSizeChange = settingsCallbacks.onFontSizeChange,
                        lineHeight = settingsState.lineHeight,
                        onLineHeightChange = settingsCallbacks.onLineHeightChange,
                        padding = settingsState.padding,
                        onPaddingChange = settingsCallbacks.onPaddingChange,
                        paddingVertical = settingsState.paddingVertical,
                        onPaddingVerticalChange = settingsCallbacks.onPaddingVerticalChange,
                        themeMode = settingsState.themeMode,
                        onThemeChange = settingsCallbacks.onThemeChange,
                        textAlign = settingsState.textAlign,
                        onTextAlignChange = settingsCallbacks.onTextAlignChange,
                        fontOptions = settingsState.fontOptions,
                        fontFamilyId = settingsState.fontFamilyId,
                        onFontFamilyChange = settingsCallbacks.onFontFamilyChange,
                        epubPaged = settingsState.epubPaged,
                        onEpubPagedChange = settingsCallbacks.onEpubPagedChange,
                        onClose = { showSettingsPanel = false },
                    )
                }
            }

            overlayExtras()
        }

        lookup?.let { request -> DefinitionSheet(request = request, onDismiss = { lookup = null }) }

        if (showToc) {
            TocDialog(
                items = uiState.toc,
                currentPage = uiState.pageIndex,
                onSelect = openTocItem,
                onDismiss = { showToc = false },
            )
        }

        if (showNextChapterDialog) {
            val nav = pendingNextChapter
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showNextChapterDialog = false
                    pendingNextChapter = null
                },
                title = { Text(stringResource(R.string.reader_mark_chapter_read_title)) },
                text = { Text(stringResource(R.string.reader_mark_chapter_read_body)) },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            val next = nav
                            showNextChapterDialog = false
                            pendingNextChapter = null
                            if (next == null) return@Button
                            readerViewModel.markChapterRead()
                            onNavigateToChapter(
                                next.chapterId ?: return@Button,
                                0,
                                next.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                next.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.reader_mark_chapter_read_confirm))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val next = nav
                            showNextChapterDialog = false
                            pendingNextChapter = null
                            if (next == null) return@TextButton
                            onNavigateToChapter(
                                next.chapterId ?: return@TextButton,
                                next.pagesRead ?: 0,
                                next.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                next.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.reader_mark_chapter_read_skip))
                    }
                },
            )
        }
    }
}

private fun ReaderUiState.timeLeftText(context: Context): String? = formatTimeLeft(context.resources.getString(R.string.reader_time_left), timeLeft?.avgHours)

internal fun formatTimeLeft(
    label: String,
    avgHours: Number?,
    locale: Locale = Locale.getDefault(),
): String? = avgHours?.let { "$label: ${String.format(locale, "%.1f", it.toDouble())} h" }

internal fun pageText(
    idx: Int,
    count: Int,
    pageTitle: String?,
    unitLabel: String,
): String {
    val base = if (count > 0) "$unitLabel ${idx + 1} / $count" else ""
    return if (!pageTitle.isNullOrBlank()) {
        listOf(base, pageTitle).filter { it.isNotBlank() }.joinToString(" – ")
    } else {
        base
    }
}

/** Suspends until the running system-bar animation on [view] ends, or [INSETS_SETTLE_TIMEOUT_MS] passes. */
private suspend fun awaitInsetsAnimationEnd(view: View) {
    withTimeoutOrNull(INSETS_SETTLE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            ViewCompat.setWindowInsetsAnimationCallback(
                view,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>,
                    ) = insets

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
            continuation.invokeOnCancellation { ViewCompat.setWindowInsetsAnimationCallback(view, null) }
        }
    }
    ViewCompat.setWindowInsetsAnimationCallback(view, null)
}

private const val INSETS_SETTLE_TIMEOUT_MS = 600L
