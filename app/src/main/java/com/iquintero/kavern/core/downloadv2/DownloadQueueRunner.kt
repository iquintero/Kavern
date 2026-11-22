package com.iquintero.kavern.core.downloadv2

import android.content.Context
import com.iquintero.kavern.core.downloadv2.strategies.ChapterImageArchiveDownloadStrategyV2
import com.iquintero.kavern.core.downloadv2.strategies.DownloadApiStrategyV2
import com.iquintero.kavern.core.downloadv2.strategies.EpubDownloadStrategyV2
import com.iquintero.kavern.core.downloadv2.strategies.PdfDownloadStrategyV2
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.dao.DownloadV2Dao
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Processes queued Download V2 jobs with concurrency limits and retry logic.
 *
 * It runs from the WorkManager worker and also directly inside the app process, so a download starts at once
 * while the app is open instead of waiting for the system scheduler. Runs are serialized, and a job is only
 * ever claimed once, so the two entry points cannot download the same job twice.
 */
object DownloadQueueRunner {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Start a run without waiting for it, e.g. right after a job was queued. */
    fun launch(
        context: Context,
        profileId: String,
    ) {
        scope.launch { run(context.applicationContext, profileId) }
    }

    /** Process pending jobs until the queue is empty. Waits for a run that is already in progress. */
    suspend fun run(
        context: Context,
        profileId: String?,
    ) = mutex.withLock { process(context, profileId) }

    private suspend fun process(
        context: Context,
        profileId: String?,
    ) {
        val activeSession = AppPreferences(context).activeSessionFlow
        val session = activeSession.first()
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadQueueRunner",
                "Queue run started matchesProfile=${session.profile.id == profileId} " +
                    "configured=${session.config.isConfigured}",
            )
        }
        if (profileId == null || session.profile.id != profileId || !session.config.isConfigured) return
        val db = KavernDatabase.getInstance(context, session.profile)
        val dao = db.downloadV2Dao()
        val appPreferences = AppPreferences(context, session)
        val networkMonitor = NetworkMonitor.getInstance(context, appPreferences)
        val maxConcurrent = appPreferences.downloadMaxConcurrentFlow.first().coerceAtLeast(1)
        val retryEnabled = appPreferences.downloadRetryEnabledFlow.first()
        val maxRetries = appPreferences.downloadRetryMaxAttemptsFlow.first().coerceAtLeast(1)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadQueueRunner",
                "Start (maxConcurrent=$maxConcurrent retry=$retryEnabled maxRetries=$maxRetries)",
            )
        }
        val semaphore = Semaphore(maxConcurrent)
        // Runs are serialized, so any RUNNING rows here belong to an interrupted run.
        dao.recoverInterruptedJobs()
        while (true) {
            val pending = dao.getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING)
            if (pending.isEmpty()) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d("DownloadQueueRunner", "No pending jobs")
                }
                break
            }
            if (appPreferences.offlineModeFlow.first()) return
            if (!networkMonitor.status.value.isOnlineAllowed) return
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadQueueRunner", "Processing pending=${pending.size}")
            }
            coroutineScope {
                pending.forEach { job ->
                    launch {
                        semaphore.withPermit {
                            if (activeSession.first() == session) {
                                runJob(context, job, dao, appPreferences, retryEnabled, maxRetries)
                            }
                        }
                    }
                }
            }
            if (activeSession.first() != session) return
        }
    }

    /**
     * Execute a single job using its strategy and apply retry rules if needed.
     */
    private suspend fun runJob(
        context: Context,
        job: DownloadJobV2Entity,
        dao: DownloadV2Dao,
        appPreferences: AppPreferences,
        retryEnabled: Boolean,
        maxRetries: Int,
    ) {
        val claimed =
            dao.transitionJob(
                job.id,
                listOf(DownloadJobV2Entity.STATUS_PENDING),
                DownloadJobV2Entity.STATUS_RUNNING,
            )
        if (claimed == 0) return

        val strategy = createStrategy(job.strategy, context, dao, appPreferences)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadQueueRunner",
                "Run job=${job.id} type=${job.type} series=${job.seriesId} volume=${job.volumeId} chapter=${job.chapterId}",
            )
        }
        val error =
            try {
                if (strategy == null) {
                    "No strategy: ${job.strategy}"
                } else {
                    withContext(Dispatchers.IO) { strategy.run(job.id) }
                    "Download ended before completion"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Download failed"
            }
        val updated = dao.getJob(job.id) ?: return
        if (updated.status == DownloadJobV2Entity.STATUS_RUNNING) {
            dao.updateRunningJob(
                updated.copy(
                    status = DownloadJobV2Entity.STATUS_FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = error,
                ),
            )
        }
        if (retryEnabled && strategy != null) dao.retryFailedJob(job.id, maxRetries)
    }
}

/** The download strategy that handles [format], or null when none does. */
private fun createStrategy(
    format: String?,
    context: Context,
    dao: DownloadV2Dao,
    appPreferences: AppPreferences,
): DownloadStrategyV2? {
    fun imageArchive(key: String) = ChapterImageArchiveDownloadStrategyV2(appContext = context, downloadDao = dao, appPreferences = appPreferences, key = key)
    return when (format) {
        EpubDownloadStrategyV2.FORMAT_EPUB ->
            EpubDownloadStrategyV2(appContext = context, downloadDao = dao, appPreferences = appPreferences)
        DownloadApiStrategyV2.FORMAT_DOWNLOAD ->
            DownloadApiStrategyV2(appContext = context, downloadDao = dao, appPreferences = appPreferences)
        PdfDownloadStrategyV2.FORMAT_PDF ->
            PdfDownloadStrategyV2(appContext = context, downloadDao = dao, appPreferences = appPreferences)
        ChapterImageArchiveDownloadStrategyV2.FORMAT_IMAGE -> imageArchive(ChapterImageArchiveDownloadStrategyV2.FORMAT_IMAGE)
        ChapterImageArchiveDownloadStrategyV2.FORMAT_ARCHIVE -> imageArchive(ChapterImageArchiveDownloadStrategyV2.FORMAT_ARCHIVE)
        else -> null
    }
}
