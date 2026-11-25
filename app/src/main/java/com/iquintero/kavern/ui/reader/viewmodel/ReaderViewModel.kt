package com.iquintero.kavern.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.reader.BaseReader
import com.iquintero.kavern.domain.reader.ReaderLoadResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val content: String? = null,
    val fromOffline: Boolean = false,
    val offlineAssetPaths: Set<String> = emptySet(),
    val imageUrl: String? = null,
    val imageUrls: Map<Int, String> = emptyMap(),
    val webtoonChapters: List<WebtoonChapterUiState> = emptyList(),
    val activeChapterId: Int? = null,
    val previousImageUrl: String? = null,
    val nextImageUrl: String? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val timeLeft: ReaderTimeLeft? = null,
    val bookInfo: ReaderBookInfo? = null,
    val bookScrollId: String? = null,
    val isPdf: Boolean = false,
    val pdfPath: String? = null,
    val toc: List<ReaderTocItem> = emptyList(),
    /** EPUB paged mode: open the loaded page on its last screen (used when paging backwards). */
    val openAtEnd: Boolean = false,
)

data class WebtoonChapterUiState(
    val chapterId: Int,
    val seriesId: Int?,
    val volumeId: Int?,
    val libraryId: Int?,
    val title: String?,
    val chapterNumber: String?,
    val bookTitle: String?,
    val pageCount: Int,
    val imageUrls: Map<Int, String> = emptyMap(),
    val isLoadingNextChapter: Boolean = false,
    val hasNextChapter: Boolean? = null,
)

/** How long after a page is shown the anchors it reports are still taken to be the page settling. */
private const val SETTLE_MS = 3_000L

/** How long opening a book waits for server metadata before using what is stored on the device. */
private const val METADATA_WAIT_MS = 1_500L

@Suppress("VariableNaming", "ktlint:standard:backing-property-naming")
abstract class BaseReaderViewModel(
    protected val chapterId: Int,
    protected val initialPage: Int,
    protected val reader: BaseReader,
    protected val seriesId: Int?,
    protected val volumeId: Int?,
    private val anonymous: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    protected val _state = MutableStateFlow(ReaderUiState(pageIndex = initialPage))
    val state: StateFlow<ReaderUiState> = _state

    /** The last position the server was told about (or, until something is sent, the one the book was opened at). */
    private var sentPosition: ReadPosition? = null
    private var lastSendAtMs = clock()
    private var pendingSend: Job? = null

    /** Until this time the anchors reported for a freshly shown page are the page settling, not reading. */
    private var settleUntilMs = 0L

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, timeLeft = null) }
            val progressRequest = async { runCatchingCancellable { reader.getProgress(chapterId) }.getOrNull() }
            val infoRequest = async { runCatchingCancellable { reader.getBookInfo(chapterId) }.getOrNull() }
            val savedProgress: ReaderProgress?
            val info: ReaderBookInfo?
            if (reader.format == Format.Epub) {
                // The server may be slow or unreachable (e.g. a home server on mobile data). Give it a moment,
                // then open from what is stored on this device instead of leaving the page blank.
                val deadline = System.currentTimeMillis() + METADATA_WAIT_MS
                val remaining = { (deadline - System.currentTimeMillis()).coerceAtLeast(0L) }
                savedProgress =
                    withTimeoutOrNull(remaining()) { progressRequest.await() }
                        ?: runCatchingCancellable { reader.getLocalProgress(chapterId) }.getOrNull()
                info =
                    withTimeoutOrNull(remaining()) { infoRequest.await() }
                        ?: runCatchingCancellable { reader.getCachedBookInfo(chapterId) }.getOrNull()
                launch {
                    // Whatever the server answers later still refines the page count and title.
                    infoRequest.await()?.let { late ->
                        _state.update { it.copy(bookInfo = late, pageCount = late.pages ?: it.pageCount) }
                    }
                }
            } else {
                savedProgress = progressRequest.await()
                info = infoRequest.await()
            }
            val totalPages = info?.pages ?: 0
            val pageToOpen =
                if (reader.format == Format.Image && totalPages > 0) {
                    initialPage.coerceIn(0, totalPages - 1)
                } else {
                    initialPage
                }
            // Opening a book is not progress: only a position that differs from this one is worth telling the server.
            if (sentPosition == null) {
                sentPosition =
                    if (savedProgress != null && savedProgress.page == pageToOpen) {
                        ReadPosition(chapterId, savedProgress.page, savedProgress.bookScrollId)
                    } else {
                        ReadPosition(chapterId, pageToOpen)
                    }
            }
            _state.update {
                it.copy(
                    isPdf = reader.format == Format.Pdf,
                    bookInfo = info,
                    pageCount = totalPages,
                    bookScrollId = savedProgress?.bookScrollId ?: it.bookScrollId,
                )
            }
            loadInitialContent(pageToOpen)
            loadTimeLeft()
            loadToc()
        }
    }

    private fun loadToc() {
        if (reader.format != Format.Epub) return
        viewModelScope.launch {
            val toc = runCatchingCancellable { reader.getToc(chapterId) }.getOrDefault(emptyList())
            _state.update { it.copy(toc = toc) }
        }
    }

    private val prefetched = mutableMapOf<Int, ReaderLoadResult>()
    private val prefetching = mutableSetOf<Int>()

    /** Fetch a neighbouring EPUB page ahead of time so turning to it does not wait for the network. */
    fun prefetchPage(index: Int) {
        if (reader.format != Format.Epub || index < 0) return
        if (prefetched.containsKey(index) || !prefetching.add(index)) return
        viewModelScope.launch {
            val result = runCatchingCancellable { reader.loadPage(chapterId, index) }.getOrNull()
            prefetching.remove(index)
            if (result != null) {
                prefetched.keys.retainAll { kotlin.math.abs(it - index) <= 1 }
                prefetched[index] = result
            }
        }
    }

    private var targetScrollId: String? = null
    private var targetOpenAtEnd = false

    /** Loads [index] and, once it is shown, positions the reader at [scrollId] (or at the end of the page). */
    fun loadPage(
        index: Int,
        scrollId: String?,
        openAtEnd: Boolean = false,
    ) {
        targetScrollId = scrollId
        targetOpenAtEnd = openAtEnd
        loadPage(index)
    }

    fun consumeOpenAtEnd() {
        _state.update { it.copy(openAtEnd = false) }
    }

    open fun loadPage(index: Int) {
        val scrollId = targetScrollId
        val openAtEnd = targetOpenAtEnd
        targetScrollId = null
        targetOpenAtEnd = false
        viewModelScope.launch {
            val previous = _state.value
            _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { prefetched.remove(index) ?: reader.loadPage(chapterId, index) }
                .onSuccess { result ->
                    applyLoadResult(result, index)
                    // A new page must not inherit the previous page's scroll position.
                    _state.update { it.copy(bookScrollId = scrollId, openAtEnd = openAtEnd) }
                    updateProgress(index)
                    settleUntilMs = clock() + SETTLE_MS
                    if (_state.value.bookInfo?.pages == null) {
                        val info = runCatchingCancellable { reader.getBookInfo(chapterId) }.getOrNull()
                        if (info != null) {
                            _state.update { st -> st.copy(bookInfo = info, pageCount = info.pages ?: st.pageCount) }
                        }
                    }
                    onPageLoaded(index)
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error loading page",
                            content = it.content ?: previous.content,
                            pageIndex = previous.pageIndex,
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    protected open fun updateProgress(
        pageIndex: Int,
        bookScrollId: String? = null,
        totalPagesOverride: Int? = null,
    ) {
        if (anonymous) return
        val position = ReadPosition(_state.value.activeChapterId ?: chapterId, pageIndex, bookScrollId)
        if (bookScrollId != null && clock() < settleUntilMs) {
            val sent = sentPosition
            if (sent != null && sent.chapterId == position.chapterId && sent.page == position.page) {
                // A page re-flows when it opens and reports the nearest screen start as an anchor, sometimes more
                // than once, which is not the reader moving. Take it as where the reader is, so opening a book
                // and leaving it sends nothing.
                sentPosition = position
                cancelPendingSend()
                _state.update { it.copy(bookScrollId = bookScrollId) }
                return
            }
        }
        val decision =
            ProgressSendPolicy.decide(
                sent = sentPosition,
                next = position,
                sinceLastSendMs = clock() - lastSendAtMs,
                pageTurnsAreChapters = reader.format == Format.Epub,
            )
        when (decision) {
            ProgressDecision.Ignore -> Unit
            ProgressDecision.DropPending -> cancelPendingSend()
            ProgressDecision.SendNow -> sendProgress(position, totalPagesOverride)
            is ProgressDecision.SendAfter -> {
                cancelPendingSend()
                pendingSend =
                    viewModelScope.launch {
                        delay(decision.delayMs)
                        pendingSend = null
                        sendProgress(position, totalPagesOverride)
                    }
            }
        }
        _state.update { it.copy(bookScrollId = bookScrollId ?: it.bookScrollId) }
    }

    private fun cancelPendingSend() {
        pendingSend?.cancel()
        pendingSend = null
    }

    private fun sendProgress(
        position: ReadPosition,
        totalPagesOverride: Int?,
    ) {
        cancelPendingSend()
        sentPosition = position
        lastSendAtMs = clock()
        viewModelScope.launch {
            val info = _state.value.bookInfo
            val progress =
                ReaderProgress(
                    chapterId = position.chapterId,
                    page = position.page,
                    bookScrollId = position.scrollId,
                    seriesId = seriesId,
                    volumeId = info?.volumeId ?: volumeId,
                    libraryId = info?.libraryId,
                )
            val totalPages = totalPagesOverride ?: info?.pages
            runCatchingCancellable { reader.setProgress(progress, totalPages = totalPages) }
            loadTimeLeft()
        }
    }

    /** The screen is back after being away, and its page may settle again like a freshly opened one. */
    fun onScreenResumed() {
        settleUntilMs = clock() + SETTLE_MS
    }

    /**
     * Sends a position that is still waiting for the reader to pause, e.g. because the app is going to the background
     * and may not come back before the system ends it.
     */
    fun flushPendingProgress() {
        if (pendingSend == null) return
        val state = _state.value
        sendProgress(
            ReadPosition(state.activeChapterId ?: chapterId, state.pageIndex, state.bookScrollId),
            state.pageCount.takeIf { it > 0 },
        )
    }

    private fun loadTimeLeft() {
        viewModelScope.launch {
            if (seriesId == null) return@launch
            val currentChapterId = _state.value.activeChapterId ?: chapterId
            val tl = runCatching<ReaderTimeLeft?> { reader.getTimeLeft(seriesId, currentChapterId) }.getOrNull()
            _state.update { it.copy(timeLeft = tl) }
        }
    }

    private fun loadInitialContent(pageIndex: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatchingCancellable { reader.loadInitial(chapterId, pageIndex) }
                .onSuccess { result ->
                    applyLoadResult(result, pageIndex)
                    if (reader.format != Format.Pdf) {
                        updateProgress(pageIndex)
                    }
                    settleUntilMs = clock() + SETTLE_MS
                    onPageLoaded(pageIndex)
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Error loading reader") }
                }
        }
    }

    suspend fun getNextChapter(): ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = _state.value.activeChapterId ?: chapterId
        val nextNav = runCatchingCancellable { reader.getNextChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val nextId = nextNav.chapterId ?: return null
        // fetch fresh book info for next chapter to capture correct volume
        val nextInfo = runCatchingCancellable { reader.getBookInfo(nextId) }.getOrNull()
        if (nextInfo != null) {
            val pages = nextInfo.pages ?: 0
            _state.update { it.copy(bookInfo = nextInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return ReaderChapterNav(
            seriesId = nextInfo?.seriesId ?: nextNav.seriesId ?: sid,
            volumeId = nextInfo?.volumeId ?: nextNav.volumeId ?: vid,
            chapterId = nextId,
            pagesRead = nextNav.pagesRead ?: 0,
        )
    }

    suspend fun getPreviousChapter(): ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = _state.value.activeChapterId ?: chapterId
        val prevNav = runCatchingCancellable { reader.getPreviousChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val prevId = prevNav.chapterId ?: return null
        val prevInfo = runCatchingCancellable { reader.getBookInfo(prevId) }.getOrNull()
        if (prevInfo != null) {
            val pages = prevInfo.pages ?: 0
            _state.update { it.copy(bookInfo = prevInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return ReaderChapterNav(
            seriesId = prevInfo?.seriesId ?: prevNav.seriesId ?: sid,
            volumeId = prevInfo?.volumeId ?: prevNav.volumeId ?: vid,
            chapterId = prevId,
            pagesRead = prevNav.pagesRead ?: 0,
        )
    }

    fun markProgressAtCurrentPage(bookScrollId: String? = null) {
        updateProgress(_state.value.pageIndex, bookScrollId)
    }

    open suspend fun saveCurrentProgress() {
        persistCurrentProgressLocally(_state.value.pageIndex)
    }

    protected suspend fun persistCurrentProgressLocally(pageIndex: Int) {
        if (anonymous) return
        val state = _state.value
        val info = state.bookInfo
        cancelPendingSend()
        val position = ReadPosition(state.activeChapterId ?: chapterId, pageIndex, state.bookScrollId)
        // Leaving a book exactly where the server already has it is not progress.
        if (position == sentPosition) return
        sentPosition = position
        val progress =
            ReaderProgress(
                chapterId = state.activeChapterId ?: chapterId,
                page = pageIndex,
                bookScrollId = state.bookScrollId,
                seriesId = info?.seriesId ?: seriesId,
                volumeId = info?.volumeId ?: volumeId,
                libraryId = info?.libraryId,
                lastModifiedUtcMillis = System.currentTimeMillis(),
            )
        if (com.iquintero.kavern.core.logging.LoggingManager
                .isDebugEnabled()
        ) {
            com.iquintero.kavern.core.logging.LoggingManager.d(
                "KavernProgress",
                "Save on exit chapter=${progress.chapterId} page=${progress.page} series=${progress.seriesId}",
            )
        }
        reader.saveProgressLocally(progress)
    }

    fun markChapterRead() {
        // Marking read is an explicit request, so it goes out even when the server already has this position.
        sentPosition = null
        val total = _state.value.pageCount
        val lastIndex = if (total > 0) total - 1 else _state.value.pageIndex
        updateProgress(lastIndex, totalPagesOverride = total.takeIf { it > 0 })
    }

    suspend fun getBookInfoFor(chapterId: Int): ReaderBookInfo? = runCatchingCancellable { reader.getBookInfo(chapterId) }.getOrNull()

    open suspend fun isPageDownloaded(pageIndex: Int): Boolean {
        val currentChapterId = _state.value.activeChapterId ?: chapterId
        return runCatchingCancellable { reader.isPageDownloaded(currentChapterId, pageIndex) }.getOrDefault(false)
    }

    protected open fun onPageLoaded(pageIndex: Int) = Unit

    protected fun applyLoadResult(
        result: ReaderLoadResult,
        pageIndex: Int,
    ) {
        _state.update {
            val imageUrls =
                result.imageUrl?.let { url ->
                    it.imageUrls + (pageIndex to url)
                } ?: it.imageUrls
            it.copy(
                content = result.content ?: it.content,
                fromOffline = result.fromOffline,
                offlineAssetPaths = result.offlineAssetPaths,
                pageIndex = pageIndex,
                isLoading = false,
                error = null,
                pdfPath = result.pdfPath ?: it.pdfPath,
                imageUrl = result.imageUrl ?: it.imageUrl,
                imageUrls = imageUrls,
                previousImageUrl = if (result.imageUrl != null) null else it.previousImageUrl,
                nextImageUrl = if (result.imageUrl != null) null else it.nextImageUrl,
            )
        }
    }
}
