package com.iquintero.kavern.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
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
}
