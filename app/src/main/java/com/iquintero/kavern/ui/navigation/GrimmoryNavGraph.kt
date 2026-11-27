package com.iquintero.kavern.ui.navigation

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.grimmory.GrimmoryHomeScreen

/** Grimmory's screens and the routes between them. Books are the top level here, not series. */
@Suppress("LongParameterList")
internal fun NavGraphBuilder.grimmoryGraph(
    navController: NavHostController,
    appPreferences: AppPreferences,
    config: AppConfig,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: ReadingListRepository,
    personRepository: PersonRepository,
    readerRepository: ReaderRepository,
    cacheManager: CacheManager,
) {
    // Still the Kavita-shaped screens while the Grimmory ones are built, one at a time.
    kavitaGraph(
        navController,
        appPreferences,
        config,
        libraryRepository,
        authRepository,
        seriesRepository,
        collectionsRepository,
        readingListRepository,
        personRepository,
        readerRepository,
        cacheManager,
    )
    // The routes below replace the Kavita-shaped ones of the same name as the Grimmory screens are written.
    composable(MainScreen.Home.route) {
        val books = remember(config) { GrimmoryBooks.of(config) }
        val downloads = remember(appPreferences) { GrimmoryDownloads(appPreferences) }
        GrimmoryHomeScreen(
            books = books,
            downloads = downloads,
            onOpenBook = { id -> navController.navigate("series/$id") },
            onOpenHistory = { navController.navigate(MainScreen.History.route) },
            onOpenSettings = { navController.navigate(MainScreen.SETTINGS_ROUTE) },
        )
    }
}
