package com.iquintero.kavern.core.sync

import android.content.Context
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

        fun enqueue(
            context: Context,
            profile: KavitaProfile,
            followUp: Boolean = false,
        ) {
            if (profile == KavitaProfile.Guest) return
            val monitor = NetworkMonitor.getInstance(context)
            val status = monitor.status.value
            if (!status.isOnlineAllowed) return
            val request =
                OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                    .setInputData(workDataOf(KavitaProfile.WORK_INPUT to profile.id))
                    .setConstraints(
                        monitor.buildConstraints(
                            allowMetered = true,
                        ),
                    ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    "$UNIQUE_WORK_NAME-${profile.id}",
                    if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
