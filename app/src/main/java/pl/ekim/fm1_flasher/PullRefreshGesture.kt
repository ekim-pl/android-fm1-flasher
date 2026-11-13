package pl.ekim.fm1_flasher

import kotlin.math.abs

class PullRefreshGesture(private val threshold: Float, private val slop: Float) {
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    var dragging = false
        private set
    var ratio = 0f
        private set

    fun start(x: Float, y: Float, atTop: Boolean, enabled: Boolean) {
        startX = x; startY = y; eligible = atTop && enabled; dragging = false; ratio = 0f
    }

    fun move(x: Float, y: Float) {
        if (!eligible) return
        val dy = y - startY
        if (!dragging && dy > slop && dy > abs(x - startX) * 1.25f) dragging = true
        ratio = if (dragging) (dy / (threshold * 2)).coerceIn(0f, 1.5f) else 0f
    }

    fun finish(cancelled: Boolean = false): Boolean {
        val refresh = eligible && dragging && ratio >= 1f && !cancelled
        eligible = false; dragging = false; ratio = 0f
        return refresh
    }
}

object CatalogRefreshPolicy {
    fun shouldRefresh(offline: Boolean, network: Boolean, fetchedAt: Long?, now: Long): Boolean =
        !offline && network && (fetchedAt == null || now < fetchedAt || now - fetchedAt >= CatalogRepository.DAY)
}
