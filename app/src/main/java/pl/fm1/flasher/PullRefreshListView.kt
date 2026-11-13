package pl.fm1.flasher

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ListView

class PullRefreshListView(context: Context) : ListView(context) {
    private val gesture = PullRefreshGesture(72 * resources.displayMetrics.density, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private var cancelledNativeTouch = false
    private var refreshing = false
    var onRefresh: (() -> Unit)? = null
    var onPull: ((Float, Boolean) -> Unit)? = null
    var refreshEnabled = true
        set(value) {
            field = value
            if (!value && gesture.dragging) { gesture.finish(true); if (!refreshing) onPull?.invoke(0f, false) }
        }

    init { overScrollMode = OVER_SCROLL_NEVER }

    fun setRefreshing(value: Boolean) {
        if (refreshing == value) return
        refreshing = value
        onPull?.invoke(if (value) 1f else 0f, value)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelledNativeTouch = false
                gesture.start(event.rawX, event.rawY, !canScrollList(-1), refreshEnabled && !refreshing)
            }
            MotionEvent.ACTION_MOVE -> {
                gesture.move(event.rawX, event.rawY)
                if (gesture.dragging) { parent?.requestDisallowInterceptTouchEvent(true); return true }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelledNativeTouch = false
            gesture.start(event.rawX, event.rawY, !canScrollList(-1), refreshEnabled && !refreshing)
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            gesture.move(event.rawX, event.rawY)
            if (gesture.dragging) {
                if (!cancelledNativeTouch) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.onTouchEvent(cancel); cancel.recycle(); cancelledNativeTouch = true
                }
                onPull?.invoke(gesture.ratio, false)
                return true
            }
        }
        if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN) && gesture.dragging) {
            val refresh = gesture.finish(event.actionMasked != MotionEvent.ACTION_UP)
            parent?.requestDisallowInterceptTouchEvent(false)
            if (refresh) { setRefreshing(true); onRefresh?.invoke() } else onPull?.invoke(0f, false)
            return true
        }
        return super.onTouchEvent(event)
    }
}
