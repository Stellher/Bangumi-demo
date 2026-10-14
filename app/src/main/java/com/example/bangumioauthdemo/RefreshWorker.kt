package com.example.bangumioauthdemo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Best-effort background refresh: Android may defer WorkManager on battery restrictions,
 * force-stop or while the device is off. Foreground resume and API requests also refresh.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        OAuthSessionManager.initialize(applicationContext)
        if (OAuthSessionManager.session.value == null) return Result.success()
        return try {
            val token = OAuthSessionManager.ensureValidToken()
            when (OAuthSessionManager.refreshState.value.kind) {
                "NETWORK_ERROR", "STORAGE_ERROR" -> Result.retry()
                "REAUTH_REQUIRED" -> Result.success()
                else -> if (token != null) Result.success() else Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object RefreshScheduler {
    private const val UNIQUE_WORK = "bangumi_periodic_oauth_refresh_v3"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag("bangumi_oauth_refresh")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK)
    }
}
