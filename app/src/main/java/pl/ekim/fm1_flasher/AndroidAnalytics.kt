package pl.ekim.fm1_flasher

import android.content.Context

/** One controller across the UI, foreground service and WorkManager. */
internal object AndroidAnalytics {
    @Volatile private var instance: AnalyticsController? = null
    fun get(context: Context): AnalyticsController = instance ?: synchronized(this) {
        instance ?: AnalyticsController(createAnalyticsSink(context.applicationContext), AndroidStorage(context.filesDir))
            .also { instance = it }
    }
}
