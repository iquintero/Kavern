package com.iquintero.kavern.core.downloadv2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iquintero.kavern.core.storage.KavitaProfile

/**
 * WorkManager entry point for the download queue. It lets downloads continue while the app is closed.
 */
class DownloadWorkerV2(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        DownloadQueueRunner.run(applicationContext, inputData.getString(KavitaProfile.WORK_INPUT))
        return Result.success()
    }
}
