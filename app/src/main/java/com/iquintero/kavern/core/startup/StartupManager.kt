package com.iquintero.kavern.core.startup

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import androidx.core.content.ContextCompat
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.cache.CacheManagerImpl
import com.iquintero.kavern.core.di.KavernServices
import com.iquintero.kavern.core.downloadv2.DownloadManagerV2
import com.iquintero.kavern.core.downloadv2.DownloadPaths
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.notification.AppNotificationManager
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.sync.ProgressSyncWorker
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.repository.AuthRepositoryImpl
import com.iquintero.kavern.data.repository.CollectionsRepositoryImpl
import com.iquintero.kavern.data.repository.LibraryRepositoryImpl
import com.iquintero.kavern.data.repository.PersonRepositoryImpl
import com.iquintero.kavern.data.repository.ReadingListRepositoryImpl
import com.iquintero.kavern.data.repository.SeriesRepositoryImpl
import com.iquintero.kavern.domain.repository.AuthRepository
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/**
 * Central place to build and expose app-wide dependencies and to trigger startup jobs.
 */
object StartupManager {
    data class Components(
        val preferences: AppPreferences,
        val cacheManager: CacheManager,
        val libraryRepository: LibraryRepository,
        val seriesRepository: SeriesRepository,
        val collectionsRepository: CollectionsRepository,
        val readingListRepository: ReadingListRepository,
        val personRepository: PersonRepository,
        val authRepository: AuthRepository,
        val readerRepository: ReaderRepository,
    )

    @Volatile
    private var cachedPreferences: AppPreferences? = null
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initialise singletons used across the app. Safe to call multiple times; returns cached instance.
     */
    @Suppress("MaxLineLength")
    @Synchronized
    fun init(context: Context): AppPreferences {
        cachedPreferences?.let { return it }

        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)
        LoggingManager.init(appContext)
        // Migrate legacy API key storage into encrypted prefs.
        runCatchingCancellable { kotlinx.coroutines.runBlocking { preferences.migrateSensitiveIfNeeded() } }
        AppNotificationManager.init(appContext)
        val networkMonitor = NetworkMonitor.getInstance(appContext, preferences)
        val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        LoggingManager.setErrorsEnabled(true)
        // Log uncaught exceptions to file/logcat for post-mortem debugging.
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            LoggingManager.e("Uncaught", "Thread=${thread.name}", ex)
            prevHandler?.uncaughtException(thread, ex)
        }
        preferences.configFlow
            .onEach { config ->
                val host =
                    kotlin
                        .runCatching {
                            val raw = config.serverUrl
                            val parsed = java.net.URI(if (raw.startsWith("http")) raw else "https://$raw")
                            parsed.host ?: raw
                        }.getOrDefault("")
                LoggingManager.updateHostMask(host)
            }.launchIn(logScope)
        preferences.verboseLoggingFlow
            .onEach { enabled ->
                LoggingManager.setEnabled(enabled || (isDebuggable && Debug.isDebuggerConnected()))
            }.launchIn(logScope)
        combine(networkMonitor.status, preferences.activeSessionFlow, ::Pair)
            .onEach { (status, session) ->
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "NetworkMonitor",
                        "online=${status.isOnline} allowed=${status.isOnlineAllowed} type=${status.connectionType} metered=${status.isMetered} roaming=${status.isRoaming} offlineMode=${status.offlineMode}",
                    )
                }
                if (status.isOnlineAllowed && session.config.isConfigured) {
                    ProgressSyncWorker.enqueue(appContext, session.profile)
                    if (KavernDatabase.getInstance(appContext, session.profile).downloadV2Dao().hasUnfinishedJobs()) {
                        DownloadManagerV2.enqueueWorker(AppPreferences(appContext, session))
                    }
                }
            }.launchIn(logScope)
        appScope.launch(Dispatchers.IO) {
            val tempDir = DownloadPaths.pdfTempDir(appContext)
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "PdfReader",
                        "Cleared temp PDF cache on startup: ${tempDir.absolutePath}",
                    )
                }
            }
        }

        cachedPreferences = preferences
        return preferences
    }

    fun components(preferences: AppPreferences): Components {
        val context = preferences.appContext
        val profile = preferences.profile
        val database = KavernDatabase.getInstance(context, profile)
        val cacheManager =
            CacheManagerImpl(
                preferences,
                database.libraryV2Dao(),
                database.seriesDetailV2Dao(),
                File(context.filesDir, profile.thumbnailFolder),
                context.getDatabasePath(profile.databaseName),
            )
        return Components(
            preferences = preferences,
            cacheManager = cacheManager,
            libraryRepository = LibraryRepositoryImpl(context, preferences),
            seriesRepository = SeriesRepositoryImpl(context, preferences, cacheManager),
            collectionsRepository = CollectionsRepositoryImpl(context, preferences),
            readingListRepository = ReadingListRepositoryImpl(context, preferences),
            personRepository = PersonRepositoryImpl(context, preferences),
            authRepository = AuthRepositoryImpl(preferences),
            readerRepository = KavernServices.get(context, preferences).readerRepository,
        )
    }

    /**
     * Hook for foreground transitions to reschedule important one-off jobs.
     */
    fun onResume(context: Context) {
        val appContext = context.applicationContext
        val preferences = init(appContext)
        appScope.launch {
            val session = preferences.activeSessionFlow.first()
            if (session.config.isConfigured) ProgressSyncWorker.enqueue(appContext, session.profile)
        }
    }
}
