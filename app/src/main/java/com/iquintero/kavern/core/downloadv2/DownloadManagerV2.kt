package com.iquintero.kavern.core.downloadv2

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.KavitaProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Download V2 orchestrator that enqueues jobs and schedules a worker with proper constraints.
 */
class DownloadManagerV2(
    private val appPreferences: AppPreferences,
    private val strategies: Map<String, DownloadStrategyV2>,
) {
    /**
     * Enqueue a request through the matching strategy and schedule the worker.
     * Returns the created job id or -1 if no strategy matches.
     */
    suspend fun enqueue(request: DownloadRequestV2): Long {
        val strategy = strategies[request.format] ?: return -1L
        val jobId = strategy.enqueue(request)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadManagerV2",
                "Enqueued job=$jobId type=${request.type} fmt=${request.format} series=${request.seriesId} " +
                    "volume=${request.volumeId} chapter=${request.chapterId} page=${request.pageIndex} count=${request.pageCount}",
            )
        }
        if (jobId > 0) enqueueWorker(appPreferences)
        return jobId
    }

    companion object {
        private const val WORK_NAME = "download_v2_worker"

        suspend fun enqueueWorker(prefs: AppPreferences) {
            val profile = prefs.profile
            val offlineMode = prefs.offlineModeFlow.first()
            if (profile == KavitaProfile.Guest || offlineMode) {
                LoggingManager.d("DownloadManagerV2", "Worker not scheduled (guest=${profile == KavitaProfile.Guest} offlineMode=$offlineMode)")
                return
            }
            val context = prefs.appContext
            val monitor = NetworkMonitor.getInstance(context, prefs)
            val constraints =
                monitor.buildConstraints(
                    allowMetered = prefs.downloadAllowMeteredFlow.first(),
                    requireBatteryNotLow = !prefs.downloadAllowLowBatteryFlow.first(),
                )
            val request =
                OneTimeWorkRequestBuilder<DownloadWorkerV2>()
                    .setInputData(workDataOf(KavitaProfile.WORK_INPUT to profile.id))
                    .setConstraints(constraints)
                    .build()
            val workManager = WorkManager.getInstance(context)
            val name = "$WORK_NAME-${profile.id}"
            // A worker can be exiting after its last queue read, so a new request must get its own turn.
            // But a request waiting out a retry backoff would hold every appended request back for hours,
            // so that one is replaced instead.
            val policy =
                if (isWaitingOutBackoff(workManager, name)) {
                    ExistingWorkPolicy.REPLACE
                } else {
                    ExistingWorkPolicy.APPEND_OR_REPLACE
                }
            workManager.enqueueUniqueWork(name, policy, request)
            // Do not wait for the system scheduler: it can hold the work back even when all constraints hold.
            DownloadQueueRunner.launch(context, profile.id)
            if (LoggingManager.isDebugEnabled()) {
                val network = constraints.requiredNetworkType
                val state = describeWork(workManager, name)
                LoggingManager.d("DownloadManagerV2", "Worker scheduled policy=$policy network=$network work=[$state]")
            }
        }

        private suspend fun describeWork(
            workManager: WorkManager,
            name: String,
        ): String =
            withContext(Dispatchers.IO) {
                runCatching { workManager.getWorkInfosForUniqueWork(name).get() }
                    .getOrDefault(emptyList())
                    .joinToString { "${it.state} attempts=${it.runAttemptCount}" }
            }

        private suspend fun isWaitingOutBackoff(
            workManager: WorkManager,
            name: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching { workManager.getWorkInfosForUniqueWork(name).get() }
                    .getOrDefault(emptyList())
                    .any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
            }
    }
}
