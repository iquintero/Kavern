package com.iquintero.kavern.ui.reader.viewmodel

import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import com.iquintero.kavern.domain.reader.BaseReader
import com.iquintero.kavern.domain.reader.ReaderLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A reader whose answers and failures are set by each test, and which records what the view model asked of it. */
private class FakeReader(
    override val format: Format = Format.Epub,
    var pages: Int? = 10,
) : BaseReader {
    val loadedPages = mutableListOf<Int>()
    val savedProgress = mutableListOf<Pair<ReaderProgress, Int?>>()
    val localProgress = mutableListOf<ReaderProgress>()
    var failPages = mutableSetOf<Int>()

    override suspend fun loadInitial(
        chapterId: Int,
        initialPage: Int,
    ) = loadPage(chapterId, initialPage)

    override suspend fun loadPage(
        chapterId: Int,
        pageIndex: Int,
    ): ReaderLoadResult {
        loadedPages += pageIndex
        if (pageIndex in failPages) error("page $pageIndex unavailable")
        return ReaderLoadResult(content = "<p>page $pageIndex</p>")
    }

    override suspend fun getBookInfo(chapterId: Int) = ReaderBookInfo(pages = pages)

    var serverProgress: ReaderProgress? = null

    override suspend fun getProgress(chapterId: Int): ReaderProgress? = serverProgress

    override suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        savedProgress += progress to totalPages
    }

    override suspend fun saveProgressLocally(progress: ReaderProgress) {
        localProgress += progress
    }

    override suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft? = null

    override suspend fun isPageDownloaded(
        chapterId: Int,
        pageIndex: Int,
    ) = false

    override suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = null

    override suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = null
}

private class TestReaderViewModel(
    reader: BaseReader,
    initialPage: Int = 0,
    anonymous: Boolean = false,
    clock: () -> Long = System::currentTimeMillis,
) : BaseReaderViewModel(
        chapterId = 7,
        initialPage = initialPage,
        reader = reader,
        seriesId = null,
        volumeId = null,
        anonymous = anonymous,
        clock = clock,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening a book shows the first page and the page count but sends nothing`() =
        runTest {
            val reader = FakeReader()
            val state = TestReaderViewModel(reader, initialPage = 2).state.value
            assertEquals("<p>page 2</p>", state.content)
            assertEquals(2, state.pageIndex)
            assertEquals(10, state.pageCount)
            assertEquals(false, state.isLoading)
            assertTrue(reader.savedProgress.isEmpty())
        }

    @Test
    fun `opening a book where the server has it and leaving sends and saves nothing`() =
        runTest {
            val reader = FakeReader().apply { serverProgress = ReaderProgress(chapterId = 7, page = 2, bookScrollId = "id(\"x\")") }
            val vm = TestReaderViewModel(reader, initialPage = 2)
            vm.markProgressAtCurrentPage("id(\"x\")")
            vm.saveCurrentProgress()
            assertTrue(reader.savedProgress.isEmpty())
            assertTrue(reader.localProgress.isEmpty())
        }

    @Test
    fun `the anchors a page reports as it settles on opening are not reading`() =
        runTest {
            val reader = FakeReader().apply { serverProgress = ReaderProgress(chapterId = 7, page = 0, bookScrollId = "idx:18") }
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            vm.markProgressAtCurrentPage("idx:19")
            advanceTimeBy(500)
            vm.markProgressAtCurrentPage("idx:12")
            vm.saveCurrentProgress()
            advanceTimeBy(60_000)
            assertTrue(reader.savedProgress.isEmpty())
            assertTrue(reader.localProgress.isEmpty())
        }

    @Test
    fun `a spot reached after the page has settled is reading`() =
        runTest {
            val reader = FakeReader().apply { serverProgress = ReaderProgress(chapterId = 7, page = 0, bookScrollId = "idx:18") }
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            vm.markProgressAtCurrentPage("idx:19")
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("idx:20")
            advanceTimeBy(20_001)
            assertEquals(
                "idx:20",
                reader.savedProgress
                    .single()
                    .first.bookScrollId,
            )
        }

    @Test
    fun `a spot inside the page is sent after twenty quiet seconds and not before`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("id(\"a\")")
            advanceTimeBy(19_000)
            assertTrue(reader.savedProgress.isEmpty())
            advanceTimeBy(1_001)
            assertEquals(
                "id(\"a\")",
                reader.savedProgress
                    .single()
                    .first.bookScrollId,
            )
        }

    @Test
    fun `scrolling on keeps pushing the send back but never past two minutes`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            advanceTimeBy(5_000)
            repeat(9) { step ->
                vm.markProgressAtCurrentPage("id(\"s$step\")")
                advanceTimeBy(15_000)
            }
            // 135 s of steady reading with a step every 15 s: nothing waited for 20 s, yet one send went out at the cap.
            assertEquals(1, reader.savedProgress.size)
            assertEquals(
                "id(\"s7\")",
                reader.savedProgress
                    .single()
                    .first.bookScrollId,
            )
        }

    @Test
    fun `going back to the sent spot cancels the send that was waiting`() =
        runTest {
            val reader = FakeReader().apply { serverProgress = ReaderProgress(chapterId = 7, page = 0, bookScrollId = "id(\"a\")") }
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("id(\"b\")")
            vm.markProgressAtCurrentPage("id(\"a\")")
            advanceTimeBy(60_000)
            assertTrue(reader.savedProgress.isEmpty())
        }

    @Test
    fun `leaving the reader with a spot still waiting saves it and nothing is sent twice`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("id(\"a\")")
            vm.saveCurrentProgress()
            advanceTimeBy(60_000)
            assertEquals("id(\"a\")", reader.localProgress.single().bookScrollId)
            assertTrue(reader.savedProgress.isEmpty())
        }

    @Test
    fun `going to the background sends what was waiting`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("id(\"a\")")
            vm.flushPendingProgress()
            assertEquals(1, reader.savedProgress.size)
            advanceTimeBy(60_000)
            assertEquals(1, reader.savedProgress.size)
        }

    @Test
    fun `comic pages are sent after a pause, not one by one`() =
        runTest {
            val reader = FakeReader(format = Format.Image)
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            vm.loadPage(1)
            vm.loadPage(2)
            vm.loadPage(3)
            assertTrue(reader.savedProgress.isEmpty())
            advanceTimeBy(20_001)
            assertEquals(
                3,
                reader.savedProgress
                    .single()
                    .first.page,
            )
        }

    @Test
    fun `turning to a page that fails keeps the page being read and reports the error`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader)
            reader.failPages += 1
            vm.loadPage(1)
            val state = vm.state.value
            assertEquals("<p>page 0</p>", state.content)
            assertEquals(0, state.pageIndex)
            assertEquals("page 1 unavailable", state.error)
            assertEquals(false, state.isLoading)
            vm.clearError()
            assertNull(vm.state.value.error)
        }

    @Test
    fun `a successful page turn records progress for that page`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader)
            vm.loadPage(3)
            assertEquals(3, vm.state.value.pageIndex)
            assertEquals(
                3,
                reader.savedProgress
                    .last()
                    .first.page,
            )
        }

    @Test
    fun `a prefetched page is used instead of loading it again`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader)
            vm.prefetchPage(1)
            vm.prefetchPage(1)
            assertEquals(1, reader.loadedPages.count { it == 1 })
            vm.loadPage(1)
            assertEquals(1, reader.loadedPages.count { it == 1 })
            assertEquals("<p>page 1</p>", vm.state.value.content)
        }

    @Test
    fun `prefetch ignores negative pages and non epub books`() =
        runTest {
            val epub = FakeReader()
            TestReaderViewModel(epub).prefetchPage(-1)
            assertEquals(listOf(0), epub.loadedPages)
            val image = FakeReader(format = Format.Image)
            TestReaderViewModel(image).prefetchPage(1)
            assertEquals(listOf(0), image.loadedPages)
        }

    @Test
    fun `anonymous reading never records progress`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, anonymous = true)
            vm.loadPage(2)
            vm.saveCurrentProgress()
            assertTrue(reader.savedProgress.isEmpty())
            assertTrue(reader.localProgress.isEmpty())
        }

    @Test
    fun `an image book opens inside its page count`() =
        runTest {
            val reader = FakeReader(format = Format.Image, pages = 5)
            val vm = TestReaderViewModel(reader, initialPage = 99)
            assertEquals(listOf(4), reader.loadedPages)
            assertEquals(4, vm.state.value.pageIndex)
        }

    @Test
    fun `marking a chapter read sends the last page with the page total`() =
        runTest {
            val reader = FakeReader(pages = 12)
            val vm = TestReaderViewModel(reader)
            vm.markChapterRead()
            val (progress, total) = reader.savedProgress.last()
            assertEquals(11, progress.page)
            assertEquals(12, total)
        }

    @Test
    fun `leaving the reader saves the current page locally`() =
        runTest {
            val reader = FakeReader()
            val vm = TestReaderViewModel(reader, clock = { currentTime })
            vm.loadPage(4)
            advanceTimeBy(5_000)
            vm.markProgressAtCurrentPage("id(\"z\")")
            vm.saveCurrentProgress()
            val saved = reader.localProgress.single()
            assertEquals(4, saved.page)
            assertEquals("id(\"z\")", saved.bookScrollId)
            assertNotNull(saved.lastModifiedUtcMillis)
        }
}
