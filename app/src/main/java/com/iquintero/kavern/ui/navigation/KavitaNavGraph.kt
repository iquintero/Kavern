package com.iquintero.kavern.ui.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.forKavita
import com.iquintero.kavern.core.network.guardApiKey
import com.iquintero.kavern.core.startup.StartupManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.AppTheme
import com.iquintero.kavern.data.repository.ComicPageFallback
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.PageKeyEvents
import com.iquintero.kavern.ui.download.DownloadQueueScreen
import com.iquintero.kavern.ui.download.DownloadQueueViewModel
import com.iquintero.kavern.ui.download.DownloadQueueViewModelFactory
import com.iquintero.kavern.ui.history.HistoryScreen
import com.iquintero.kavern.ui.home.HomeScreen
import com.iquintero.kavern.ui.library.LibraryV2Screen
import com.iquintero.kavern.ui.navigation.MainScreen
import com.iquintero.kavern.ui.reader.model.ReaderReturn
import com.iquintero.kavern.ui.reader.screen.ReaderKeyEvents
import com.iquintero.kavern.ui.reader.screen.ReaderScreen
import com.iquintero.kavern.ui.seriesdetail.SeriesDetailScreenV2
import com.iquintero.kavern.ui.settings.SettingsScreen
import com.iquintero.kavern.ui.theme.KavernTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Kavita's screens and the routes between them. Kept exactly as they were when the app only spoke to Kavita. */
@Suppress("LongMethod", "LongParameterList")
internal fun NavGraphBuilder.kavitaGraph(
    navController: NavHostController,
    appPreferences: AppPreferences,
    config: AppConfig,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: com.iquintero.kavern.domain.repository.ReadingListRepository,
    personRepository: com.iquintero.kavern.domain.repository.PersonRepository,
    readerRepository: com.iquintero.kavern.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
) {
    composable(MainScreen.Home.route) {
        HomeScreen(
            seriesRepository = seriesRepository,
            cacheManager = cacheManager,
            appPreferences = appPreferences,
            onOpenSeries = { seriesId -> navController.navigate("series/$seriesId") },
            onOpenHistory = { navController.navigate(MainScreen.History.route) },
            onOpenSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) },
        )
    }
    composable(
        route =
            "${MainScreen.LibraryV2.route}?collectionId={collectionId}&collectionName={collectionName}" +
                "&genreId={genreId}&tagId={tagId}&genreName={genreName}&tagName={tagName}",
        arguments =
            listOf(
                navArgument("collectionId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("collectionName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("genreId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("tagId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("genreName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("tagName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) {
        val collectionIdArg = it.arguments?.getInt("collectionId")?.takeIf { id -> id >= 0 }
        val collectionNameArg = it.arguments?.getString("collectionName")?.takeIf { name -> name.isNotBlank() }
        val genreIdArg = it.arguments?.getInt("genreId")?.takeIf { id -> id >= 0 }
        val tagIdArg = it.arguments?.getInt("tagId")?.takeIf { id -> id >= 0 }
        val genreNameArg = it.arguments?.getString("genreName")?.takeIf { name -> name.isNotBlank() }
        val tagNameArg = it.arguments?.getString("tagName")?.takeIf { name -> name.isNotBlank() }
        LibraryV2Screen(
            libraryRepository = libraryRepository,
            seriesRepository = seriesRepository,
            collectionsRepository = collectionsRepository,
            readingListRepository = readingListRepository,
            personRepository = personRepository,
            cacheManager = cacheManager,
            appPreferences = appPreferences,
            onOpenSeries = { seriesId ->
                navController.navigate("series/$seriesId")
            },
            onOpenSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) },
            initialCollectionId = collectionIdArg,
            initialCollectionName = collectionNameArg,
            initialGenreId = genreIdArg,
            initialGenreName = genreNameArg,
            initialTagId = tagIdArg,
            initialTagName = tagNameArg,
        )
    }
    composable(MainScreen.History.route) {
        HistoryScreen(
            appPreferences = appPreferences,
            onBack = { navController.popBackStack() },
        )
    }
    composable(MainScreen.Downloads.route) {
        val vm: DownloadQueueViewModel =
            viewModel(factory = DownloadQueueViewModelFactory.create(appPreferences, cacheManager))
        DownloadQueueScreen(
            viewModel = vm,
            onOpenSeries = { seriesId -> navController.navigate("series/$seriesId") },
        )
    }
    composable(MainScreen.SETTINGS_ROUTE) {
        SettingsScreen(
            appPreferences = appPreferences,
            authRepository = authRepository,
            cacheManager = cacheManager,
            onOpenAbout = { navController.navigate("settings/about") },
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = "series/{seriesId}",
        arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
    ) { entry ->
        val seriesId = entry.arguments?.getInt("seriesId") ?: return@composable
        val readerReturn =
            entry.savedStateHandle
                .getStateFlow<ReaderReturn?>(
                    "reader_return",
                    null,
                ).collectAsState(initial = null)
        val refreshSignal =
            entry.savedStateHandle
                .getStateFlow<Boolean>(
                    "series_refresh",
                    false,
                ).collectAsState(initial = false)
        SeriesDetailScreenV2(
            seriesId = seriesId,
            appPreferences = appPreferences,
            collectionsRepository = collectionsRepository,
            readerRepository = readerRepository,
            cacheManager = cacheManager,
            onOpenReader = { chapterId, page, sid, vid, fmt ->
                navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: -1}")
            },
            onOpenVolume = { volumeId ->
                navController.navigate("volume/$volumeId")
            },
            onOpenSeries = { id ->
                navController.navigate("series/$id")
            },
            onOpenBrowseGenre = { id, name ->
                val encoded = Uri.encode(name)
                navController.navigate("${MainScreen.LibraryV2.route}?genreId=$id&genreName=$encoded")
            },
            onOpenBrowseTag = { id, name ->
                val encoded = Uri.encode(name)
                navController.navigate("${MainScreen.LibraryV2.route}?tagId=$id&tagName=$encoded")
            },
            onOpenCollection = { id, name ->
                val encoded = Uri.encode(name)
                navController.navigate("${MainScreen.LibraryV2.route}?collectionId=$id&collectionName=$encoded")
            },
            readerReturn = readerReturn.value,
            onConsumeReaderReturn = { entry.savedStateHandle["reader_return"] = null },
            refreshSignal = refreshSignal.value,
            onConsumeRefreshSignal = { entry.savedStateHandle["series_refresh"] = false },
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = "volume/{volumeId}",
        arguments = listOf(navArgument("volumeId") { type = NavType.IntType }),
    ) { entry ->
        val volumeId = entry.arguments?.getInt("volumeId") ?: return@composable
        val readerReturn =
            entry.savedStateHandle
                .getStateFlow<ReaderReturn?>(
                    "reader_return",
                    null,
                ).collectAsState(initial = null)
        com.iquintero.kavern.ui.seriesdetail.VolumeDetailScreenV2(
            volumeId = volumeId,
            appPreferences = appPreferences,
            readerRepository = readerRepository,
            readerReturn = readerReturn.value,
            onConsumeReaderReturn = {
                entry.savedStateHandle["reader_return"] = null
                navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
            },
            onOpenReader = { chapterId, page, sid, vid, fmt ->
                navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: 0}")
            },
            onBack = {
                navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
                navController.popBackStack()
            },
        )
    }
    composable("settings/about") {
        com.iquintero.kavern.ui.settings.screens.SettingsAboutScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = "reader/{chapterId}?page={page}&sid={sid}&vid={vid}&fmt={fmt}",
        arguments =
            listOf(
                navArgument("chapterId") { type = NavType.IntType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("sid") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("vid") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("fmt") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { entry ->
        val chId = entry.arguments?.getInt("chapterId") ?: return@composable
        val page = entry.arguments?.getInt("page")
        val sid = entry.arguments?.getInt("sid")?.takeIf { it != 0 }
        val vid = entry.arguments?.getInt("vid")?.takeIf { it != 0 }
        val fmt = entry.arguments?.getInt("fmt")?.takeIf { it != -1 }
        ReaderScreen(
            chapterId = chId,
            initialPage = page,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = sid,
            volumeId = vid,
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
            formatId = fmt,
            onBack = { _, pIdx, _, vIdBack ->
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    "reader_return",
                    ReaderReturn(
                        volumeId = vIdBack ?: 0,
                        page = pIdx,
                    ),
                )
                navController.popBackStack()
            },
            onNavigateToChapter = { targetChapter, targetPage, targetSid, targetVid ->
                val nextSid = targetSid ?: sid ?: 0
                val nextVid = targetVid ?: vid ?: 0
                val fmtValue = fmt ?: -1
                navController.navigate("reader/$targetChapter?page=${targetPage ?: 0}&sid=$nextSid&vid=$nextVid&fmt=$fmtValue") {
                    popUpTo("reader/$chId?page=${page ?: 0}&sid=${sid ?: 0}&vid=${vid ?: 0}&fmt=$fmtValue") { inclusive = true }
                }
            },
        )
    }
}
