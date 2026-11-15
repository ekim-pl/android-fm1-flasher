package pl.ekim.fm1_flasher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CatalogSyncWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!CatalogOperations.lock.tryLock()) return Result.retry()
        try {
            val database = CatalogDatabase.get(applicationContext)
            database.migrate(applicationContext.filesDir)
            if (isStopped || database.setting("background") != "true" || database.settings().offline) return Result.success()
            val analytics = AndroidAnalytics.get(applicationContext)
            analytics.setOffline(database.settings().offline)
            val measurement = analytics.begin(AnalyticsOperation.CATALOG, AnalyticsInitiator.BACKGROUND)
            try {
                val catalog = CatalogRepository(database).load()
                measurement.complete(catalog.fresh, if (catalog.fresh) AnalyticsError.NONE else AnalyticsError.OTHER)
            } catch (failure: Exception) { measurement.complete(false, analyticsError(failure)); throw failure }
            return Result.success()
        } catch (_: Exception) { return Result.retry() }
        finally { CatalogOperations.lock.unlock() }
    }

    companion object {
        internal const val NAME = "firmware-catalogs"
        fun schedule(context: Context, enabled: Boolean): androidx.work.Operation {
            val manager = WorkManager.getInstance(context)
            if (!enabled) return manager.cancelUniqueWork(NAME)
            val request = PeriodicWorkRequest.Builder(CatalogSyncWorker::class.java, 24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            return manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
