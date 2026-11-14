package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class PullRefreshTest {
    @Test fun pullCanOnlyStartAtTheTopOfTheList() {
        val gesture = PullRefreshGesture(100f, 5f)
        gesture.start(0f, 0f, atTop = false, enabled = true)
        gesture.move(0f, 500f)
        assertFalse(gesture.dragging)
        assertFalse(gesture.finish())
    }

    @Test fun upwardAndHorizontalScrollingNeverRefresh() {
        val gesture = PullRefreshGesture(100f, 5f)
        gesture.start(0f, 300f, true, true)
        gesture.move(0f, 0f)
        assertFalse(gesture.dragging)
        assertFalse(gesture.finish())
        gesture.start(0f, 0f, true, true)
        gesture.move(400f, 200f)
        assertFalse(gesture.finish())
    }

    @Test fun thresholdAndReleaseAreRequiredAndFireOnlyOnce() {
        val gesture = PullRefreshGesture(100f, 5f)
        gesture.start(0f, 0f, true, true)
        gesture.move(0f, 199f)
        assertTrue(gesture.dragging)
        assertFalse(gesture.finish())
        gesture.start(0f, 0f, true, true)
        gesture.move(0f, 200f)
        assertEquals(1f, gesture.ratio, 0f)
        assertTrue(gesture.finish())
        assertFalse(gesture.finish())
    }

    @Test fun cancellationAndReturningTheFingerCancelRefresh() {
        val gesture = PullRefreshGesture(100f, 5f)
        gesture.start(0f, 0f, true, true); gesture.move(0f, 300f)
        assertFalse(gesture.finish(cancelled = true))
        gesture.start(0f, 0f, true, true); gesture.move(0f, 300f); gesture.move(0f, 50f)
        assertFalse(gesture.finish())
        assertEquals(0f, gesture.ratio, 0f)
    }

    @Test fun disabledRefreshDoesNotCaptureTouch() {
        val gesture = PullRefreshGesture(100f, 5f)
        gesture.start(0f, 0f, true, false); gesture.move(0f, 500f)
        assertFalse(gesture.dragging); assertFalse(gesture.finish())
    }

    @Test fun autoRefreshUsesEachEnabledSourcesAttemptBudget() {
        val now = CatalogRepository.DAY * 10
        val hub = CatalogSource.hub.copy(attemptedAt = now, nextAttemptAt = now + CatalogRepository.DAY)
        val sources = listOf(hub, CatalogSource.awesome)
        assertFalse(CatalogRefreshPolicy.shouldRefresh(true, true, sources, now))
        assertFalse(CatalogRefreshPolicy.shouldRefresh(false, false, sources, now))
        assertFalse(CatalogRefreshPolicy.shouldRefresh(false, true, sources, now + 1))
        assertFalse(CatalogRefreshPolicy.shouldRefresh(false, true, sources, now - 1))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(false, true, sources, now + CatalogRepository.DAY))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(false, true, listOf(hub, CatalogSource.awesome.copy(enabled = true)), now))
    }
}
