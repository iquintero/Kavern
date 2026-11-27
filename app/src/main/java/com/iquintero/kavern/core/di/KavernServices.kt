package com.iquintero.kavern.core.di

import android.content.Context
import com.iquintero.kavern.core.network.grimmory.GrimmoryServers
import com.iquintero.kavern.core.network.grimmory.isGrimmory
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.repository.ReaderRepositoryImpl
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.grimmory.GrimmoryReaderRepository

/**
 * Very small service locator to provide dependencies for background workers
 * (e.g., ProgressSyncWorker) where we can't easily pass objects directly.
 */
data class KavernServices(
    val readerRepository: ReaderRepository,
) {
    companion object {
        fun get(
            context: Context,
            appPreferences: AppPreferences,
        ): KavernServices {
            val database = KavernDatabase.getInstance(context, appPreferences.profile)
            val config = appPreferences.sessionConfig
            val readerRepository =
                if (config != null && config.isGrimmory) {
                    GrimmoryReaderRepository(context, appPreferences, database.readerDao(), database.downloadV2Dao(), GrimmoryServers.serverFor(config))
                } else {
                    ReaderRepositoryImpl(
                        context,
                        appPreferences,
                        database.readerDao(),
                        database.downloadV2Dao(),
                    )
                }
            return KavernServices(readerRepository = readerRepository)
        }
    }
}
