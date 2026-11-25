package com.iquintero.kavern.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.iquintero.kavern.core.di.KavernServices
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.KavitaProfile
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.repository.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Syncs locally cached reader progress to the server once network is available.
 * This is a lightweight job: it simply calls ReaderRepository.syncLocalProgress().
 */
class ProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KavitaProfile.WORK_INPUT) ?: return Result.success()
        val session = AppPreferences(applicationContext).activeSessionFlow.first()
        if (session.profile.id != profileId || !session.config.isConfigured) return Result.success()
        val monitor = NetworkMonitor.getInstance(applicationContext)
        val status = monitor.status.value
        if (!status.isOnlineAllowed) {
            return if (status.offlineMode) Result.success() else Result.retry()
        }
        val services = KavernServices.get(applicationContext, AppPreferences(applicationContext, session))
        val repo: ReaderRepository = services.readerRepository
        return runCatchingCancellable {
            if (repo.syncLocalProgress()) Result.success() else Result.retry()
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "kavern_progress_sync"

        /**
         * A failed attempt (no connection, or a server that cannot be reached from here) is retried after 10, 20,
         * 30 minutes and so on. Android's default doubles the wait up to five hours, which is too long to wait for
         * a home server once back on its network.
         */
        private const val RETRY_STEP_MINUTES = 10L

        /**
         * Queues a sync of the reading positions stored on the device.
         *
         * It is queued even without a connection: Android then runs it by itself once one exists, even if the app has
         * been closed in the meantime. It only skips queueing in the app's own offline mode, which is switched off by
         * the user, and the app queues again when that happens.
         *
         * A [retryNow] request replaces work that is waiting out a retry delay, for moments when the server is
         * likely to be reachable again: the app coming to the front or the network changing.
         */
        fun enqueue(
            context: Context,
            profile: KavitaProfile,
            followUp: Boolean = false,
            retryNow: Boolean = false,
        ) {
            if (profile == KavitaProfile.Guest) return
            val monitor = NetworkMonitor.getInstance(context)
            if (monitor.status.value.offlineMode) return
            val request =
                OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                    .setInputData(workDataOf(KavitaProfile.WORK_INPUT to profile.id))
                    .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_STEP_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(
                        monitor.buildConstraints(
                            allowMetered = true,
                        ),
                    ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    "$UNIQUE_WORK_NAME-${profile.id}",
                    when {
                        retryNow -> ExistingWorkPolicy.REPLACE
                        followUp -> ExistingWorkPolicy.APPEND_OR_REPLACE
                        else -> ExistingWorkPolicy.KEEP
                    },
                    request,
                )
        }
    }
}
