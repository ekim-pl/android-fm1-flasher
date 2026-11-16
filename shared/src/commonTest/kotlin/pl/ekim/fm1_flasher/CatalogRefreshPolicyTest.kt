package pl.ekim.fm1_flasher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogRefreshPolicyTest {
    @Test fun manualExperimentalBypassesIntervalButHonorsRetry() {
        val now = 100 * CatalogSource.HOUR
        val source = CatalogSource.awesome.copy(attemptedAt = now, nextAttemptAt = now + 8 * CatalogSource.HOUR)
        assertFalse(source.due(now + 1))
        assertTrue(source.due(now + 1, manualExperimental = true))
        assertFalse(source.copy(serverRetryAt = now + CatalogSource.HOUR).due(now + 1, manualExperimental = true))
        assertFalse(source.due(now - 1, manualExperimental = true))
        assertTrue(source.due(now + 8 * CatalogSource.HOUR))
    }

    @Test fun hubAlwaysHasTwentyFourHourMinimum() {
        val now = 100 * CatalogSource.HOUR
        val hub = CatalogSource.hub.copy(intervalHours = 1, attemptedAt = now, nextAttemptAt = now + CatalogSource.HOUR)
        assertFalse(hub.due(now + CatalogSource.HOUR, manualExperimental = true))
        assertTrue(hub.due(now + 24 * CatalogSource.HOUR, manualExperimental = true))
        val alias = hub.copy(id = "custom", provider = CatalogProviderType.JSON_URL)
        assertFalse(alias.due(now + CatalogSource.HOUR, manualExperimental = true))
    }
}
