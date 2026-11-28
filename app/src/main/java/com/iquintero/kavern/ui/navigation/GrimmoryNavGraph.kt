package com.iquintero.kavern.ui.navigation

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.grimmory.GrimmoryBookScreen
import com.iquintero.kavern.ui.grimmory.GrimmoryDownloadsScreen
import com.iquintero.kavern.ui.grimmory.GrimmoryHistoryScreen
import com.iquintero.kavern.ui.grimmory.GrimmoryHomeScreen
import com.iquintero.kavern.ui.grimmory.GrimmoryLibraryScreen
import com.iquintero.kavern.ui.grimmory.GrimmorySeriesScreen
import com.iquintero.kavern.ui.reader.screen.ReaderScreen
import com.iquintero.kavern.ui.settings.SettingsScreen
import com.iquintero.kavern.ui.settings.screens.SettingsAboutScreen

private const val BOOK_ROUTE = "book"
private const val READER_ROUTE = "reader"
private const val SERIES_ROUTE = "series"

/** The format id of an EPUB, the only kind of book the reader opens from Grimmory so far. */
private const val EPUB_FORMAT = 3

/** Grimmory's screens and the routes between them. Books are the top level here; there is no series, volume or chapter. */
@Suppress("LongMethod")
internal fun NavGraphBuilder.grimmoryGraph(
    navController: NavHostController,
    appPreferences: AppPreferences,
    config: AppConfig,
    authRepository: AuthRepository,
    readerRepository: ReaderRepository,
    cacheManager: CacheManager,
) {
    val openBook = { id: Int -> navController.navigate("$BOOK_ROUTE/$id") }
    val openSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) }

    composable(MainScreen.Home.route) {
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmoryHomeScreen(
            books = books,
            downloads = downloads,
            onOpenBook = openBook,
            onOpenHistory = { navController.navigate(MainScreen.History.route) },
            onOpenSettings = openSettings,
        )
    }
    composable(MainScreen.LibraryV2.route) {
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmoryLibraryScreen(
            books = books,
            downloads = downloads,
            onOpenBook = openBook,
            onOpenSeries = { name -> navController.navigate("$SERIES_ROUTE/${Uri.encode(name)}") },
            onOpenSettings = openSettings,
        )
    }
    composable(MainScreen.History.route) {
        val books = remember(config) { GrimmoryBooks.of(config) }
        GrimmoryHistoryScreen(books = books, readerRepository = readerRepository, onOpenBook = openBook, onBack = { navController.popBackStack() })
    }
    composable(MainScreen.Downloads.route) {
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmoryDownloadsScreen(books = books, downloads = downloads, onOpenBook = openBook)
    }
    composable(
        route = "$SERIES_ROUTE/{name}",
        arguments = listOf(navArgument("name") { type = NavType.StringType }),
    ) { entry ->
        val name = entry.arguments?.getString("name") ?: return@composable
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmorySeriesScreen(name = name, books = books, downloads = downloads, onOpenBook = openBook, onBack = { navController.popBackStack() })
    }
    composable(
        route = "$BOOK_ROUTE/{bookId}",
        arguments = listOf(navArgument("bookId") { type = NavType.IntType }),
    ) { entry ->
        val bookId = entry.arguments?.getInt("bookId") ?: return@composable
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmoryBookScreen(
            bookId = bookId,
            books = books,
            downloads = downloads,
            onOpenReader = { id, part -> navController.navigate("$READER_ROUTE/$id?part=$part") },
            onOpenBook = openBook,
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = "$READER_ROUTE/{bookId}?part={part}",
        arguments =
            listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("part") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
    ) { entry ->
        val bookId = entry.arguments?.getInt("bookId") ?: return@composable
        val part = entry.arguments?.getInt("part") ?: 0
        ReaderScreen(
            chapterId = bookId,
            initialPage = part,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = bookId,
            volumeId = bookId,
            serverUrl = config.serverUrl,
            apiKey = config.apiKey,
            formatId = EPUB_FORMAT,
            onBack = { _, _, _, _ -> navController.popBackStack() },
            onNavigateToChapter = { target, targetPart, _, _ ->
                navController.navigate("$READER_ROUTE/$target?part=${targetPart ?: 0}") {
                    popUpTo("$READER_ROUTE/{bookId}?part={part}") { inclusive = true }
                }
            },
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
    composable("settings/about") { SettingsAboutScreen(onBack = { navController.popBackStack() }) }
}
