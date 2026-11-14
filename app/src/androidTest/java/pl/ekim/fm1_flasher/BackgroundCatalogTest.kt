package pl.ekim.fm1_flasher

import android.test.InstrumentationTestCase
import androidx.work.NetworkType
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class BackgroundCatalogTest : InstrumentationTestCase() {
    fun testUniqueNetworkConstrainedWorkAndCancellationWithoutOptInNetwork() {
        val context = instrumentation.targetContext
        val database = CatalogDatabase.get(context)
        database.migrate(context.filesDir)
        val previous = database.setting("background")
        database.setting("background", "false") // Worker must exit without a request, even when a job is enqueued.
        try {
            CatalogSyncWorker.schedule(context, true).result.get(10, TimeUnit.SECONDS)
            CatalogSyncWorker.schedule(context, true).result.get(10, TimeUnit.SECONDS)
            val manager = WorkManager.getInstance(context)
            val active = manager.getWorkInfosForUniqueWork(CatalogSyncWorker.NAME).get(10, TimeUnit.SECONDS).filter { !it.state.isFinished }
            assertEquals(1, active.size)
            assertEquals(NetworkType.CONNECTED, active.single().constraints.requiredNetworkType)
            CatalogSyncWorker.schedule(context, false).result.get(10, TimeUnit.SECONDS)
            assertTrue(manager.getWorkInfosForUniqueWork(CatalogSyncWorker.NAME).get(10, TimeUnit.SECONDS).all { it.state.isFinished })
        } finally {
            database.setting("background", previous)
            CatalogSyncWorker.schedule(context, previous == "true" && !database.settings().offline).result.get(10, TimeUnit.SECONDS)
        }
    }
}
