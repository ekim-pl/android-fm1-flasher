package pl.fm1.flasher

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

    @Test fun autoRefreshNeedsNetworkOnlineModeAndAnExpiredOrMissingCache() {
        val day = CatalogRepository.DAY
        assertFalse(CatalogRefreshPolicy.shouldRefresh(true, true, null, day))
        assertFalse(CatalogRefreshPolicy.shouldRefresh(false, false, null, day))
        assertFalse(CatalogRefreshPolicy.shouldRefresh(false, true, 1000, day + 999))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(false, true, 1000, day + 1000))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(false, true, null, day))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(false, true, day, 1000))
    }
}
