package pl.ekim.fm1_flasher

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

internal fun createAnalyticsSink(context: Context): AnalyticsSink = FirebaseAnalyticsSink(context)
private class FirebaseAnalyticsSink(private val context: Context) : AnalyticsSink {
    override val available = true
    private var sdk: FirebaseAnalytics? = null
    override fun configure(enabled: Boolean, reset: Boolean) {
        if (enabled && sdk == null) {
            checkNotNull(FirebaseApp.initializeApp(context))
            sdk = FirebaseAnalytics.getInstance(context)
        }
        sdk?.let {
            it.setAnalyticsCollectionEnabled(false)
            it.setConsent(mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED))
            if (reset) it.resetAnalyticsData()
            if (enabled) it.setAnalyticsCollectionEnabled(true)
        }
    }
    override fun record(name: String, parameters: Map<String, String>) {
        sdk?.logEvent(name, Bundle().apply { parameters.forEach { (key, value) ->
            if (key in listOf("duration_ms", "schema_version")) putLong(key, value.toLong()) else putString(key, value)
        } })
    }
}
