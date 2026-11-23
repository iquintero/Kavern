package com.iquintero.kavern.ui.download

import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.data.local.db.dao.DownloadV2Dao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Android's Log is not available in unit tests, so keep the view model's warnings from reaching it.
        LoggingManager.setErrorsEnabled(false)
    }

    @After
    fun tearDown() {
        LoggingManager.setErrorsEnabled(true)
        Dispatchers.resetMain()
    }

    // The page only observes lists here, so every Flow the DAO returns can simply be empty.
    private val dao: DownloadV2Dao =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DownloadV2Dao::class.java)) { _, method, _ ->
            if (Flow::class.java.isAssignableFrom(method.returnType)) emptyFlow<Any>() else null
        } as DownloadV2Dao

    private val cache: CacheManager =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CacheManager::class.java)) { _, _, _ -> null } as CacheManager

    private val config = AppConfig(serverUrl = "http://server", apiKey = "key", imageApiKey = "image", userId = 1)

    private fun viewModel(deleteDownload: suspend (Long) -> Boolean) =
        DownloadQueueViewModel(
            downloadDao = dao,
            cacheManager = cache,
            deleteDownload = deleteDownload,
            enqueueWorker = {},
            configFlow = flowOf(config),
        )

    @Test
    fun removingABookDeletesEveryItemOfIt() =
        runTest {
            val deleted = mutableListOf<Long>()
            viewModel { id ->
                deleted += id
                true
            }.deleteDownloads(listOf(4L, 5L, 6L))
            assertEquals(listOf(4L, 5L, 6L), deleted)
        }

    @Test
    fun oneItemThatCannotBeDeletedDoesNotStopTheRest() =
        runTest {
            val attempted = mutableListOf<Long>()
            viewModel { id ->
                attempted += id
                when (id) {
                    2L -> error("disk error")
                    3L -> false
                    else -> true
                }
            }.deleteDownloads(listOf(1L, 2L, 3L, 4L))
            assertEquals(listOf(1L, 2L, 3L, 4L), attempted)
        }

    @Test
    fun exposesTheServerConfigForCoverImages() =
        runTest {
            assertEquals(config, viewModel { true }.config.first { it.serverUrl.isNotBlank() })
        }
}
