package pl.ekim.fm1_flasher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MidiLoggerPanel(context: Context, private val monitor: () -> MidiMonitor?,
    private val allowed: () -> Boolean, private val permission: (MonitorPort, () -> Unit) -> Unit,
    private val toggle: () -> Unit, private val export: () -> Unit) : LinearLayout(context) {
    private var initialized = false
    private val ui = Ui(context)
    private val handler = Handler(Looper.getMainLooper())
    private var rows = emptyList<MidiLogEntry>()
    private var revision = -1L
    private var paused = false
    private var source = ""
    private var channel = 0
    private var type = ""
    private var hideTiming = false
    private val status = ui.text("")
    private val select = ui.button(context.getString(R.string.logger_ports)) { choosePorts() }
    private val start = ui.button(context.getString(R.string.logger_start)) {
        val m = monitor() ?: return@button
        if (m.running || allowed()) runCatching { toggle() }.onFailure {
            Toast.makeText(context, it.message ?: context.getString(R.string.logger_stopped), Toast.LENGTH_LONG).show()
        }
        refresh(true)
    }
    private val list = ListView(context).apply { divider = null }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].sequence
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            ((convertView as? TextView) ?: ui.text("", 12).apply {
                typeface = Typeface.MONOSPACE; setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            }).apply { text = rows[position].line() }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!isShown) return
            refresh(); handler.postDelayed(this, 100)
        }
    }
    init {
        orientation = VERTICAL; setPadding(ui.dp(12), ui.dp(8), ui.dp(12), 0)
        addView(ui.text(context.getString(R.string.logger_help), 12, Ui.MUTED))
        fun actions(vararg buttons: View) { addView(ui.row().apply {
            buttons.forEach { addView(it, LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(4) }) }
        }) }
        actions(select, start, ui.button(context.getString(R.string.logger_export)) { export() })
        actions(ui.button(context.getString(R.string.logger_filters)) { filters() },
            ui.button(context.getString(R.string.logger_pause)) { paused = !paused; isSelected = paused; refresh(true) },
            ui.button(context.getString(R.string.logger_clear)) { monitor()?.log?.clear(); refresh(true) })
        addView(status)
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, position, _ ->
            val entry = rows[position]
            AlertDialog.Builder(context).setTitle(entry.source).setMessage(entry.line(true))
                .setPositiveButton(android.R.string.ok, null).show(); true
        }
        addView(list, LayoutParams(-1, 0, 1f))
        initialized = true
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View construction can dispatch this before our fields are initialized.
        if (initialized) {
            handler.removeCallbacks(tick)
            if (isShown) handler.post(tick)
        }
    }
    override fun onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow() }
    private fun refresh(force: Boolean = false) {
        if (visibility != View.VISIBLE && !force) return
        val m = monitor() ?: return
        start.isEnabled = !m.draining && (allowed() || m.running)
        select.isEnabled = allowed() && !m.running
        start.text = context.getString(if (m.running) R.string.logger_stop else R.string.logger_start)
        val snapshot = m.log.snapshotIfChanged(if (force) -1 else revision) ?: return
        status.text = context.getString(R.string.logger_status, snapshot.total, snapshot.evicted, snapshot.lostBytes) +
            "\n" + context.getString(if (m.running) R.string.logger_running else R.string.logger_stopped) +
            " · OUT ${m.activeCount}/${m.selected.size}" +
            (if (paused) " · " + context.getString(R.string.logger_paused) else "")
        if (!force && snapshot.revision == revision) return
        revision = snapshot.revision
        val anchor = list.firstVisiblePosition
        val top = list.getChildAt(0)?.top ?: 0
        val anchorSequence = rows.getOrNull(anchor)?.sequence
        rows = snapshot.entries.filter { (source.isEmpty() || it.source == source) &&
            (channel == 0 || it.channel == channel) && (type.isEmpty() || it.type == type) &&
            (!hideTiming || it.type !in listOf("Clock", "Active Sensing")) }
        adapter.notifyDataSetChanged()
        if (!paused && rows.isNotEmpty()) list.setSelection(rows.lastIndex)
        else list.setSelectionFromTop(rows.indexOfFirst { it.sequence == anchorSequence }.coerceAtLeast(0), top)
    }
    private fun choosePorts() {
        val m = monitor() ?: return
        val ports = m.ports()
        if (ports.isEmpty()) { Toast.makeText(context, context.getString(R.string.logger_no_ports), Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(context).setTitle(R.string.logger_ports)
            .setMultiChoiceItems(ports.map { it.label }.toTypedArray(), ports.map { it.key in m.selected }.toBooleanArray()) { dialog, index, checked ->
                val p = ports[index]
                if (!checked) m.selected.remove(p.key)
                else permission(p) {
                    m.selected.add(p.key)
                    (dialog as AlertDialog).listView.setItemChecked(index, true)
                }
                if (checked && p.key !in m.selected) (dialog as AlertDialog).listView.setItemChecked(index, false)
            }.setPositiveButton(android.R.string.ok, null).show()
    }
    private fun filters() {
        val all = context.getString(R.string.logger_all)
        val box = ui.column()
        val sources = listOf("") + (monitor()?.log?.snapshot()?.entries?.map { it.source }?.distinct() ?: emptyList())
        val types = listOf("", "Note On", "Note Off", "Poly Pressure", "CC", "Program Change", "Channel Pressure", "Pitch Bend", "SysEx", "Clock", "Active Sensing", "Start", "Continue", "Stop", "MTC", "Song Position", "Song Select", "Tune Request", "Reset", "Unknown", "Event")
        val src = ui.spinner(sources.map { it.ifEmpty { all } }).apply { setSelection(sources.indexOf(source).coerceAtLeast(0)) }
        val ch = ui.spinner(listOf(all) + (1..16).map { "ch=$it" }).apply { setSelection(channel) }
        val kind = ui.spinner(types.map { it.ifEmpty { all } }).apply { setSelection(types.indexOf(type).coerceAtLeast(0)) }
        val timing = CheckBox(context).apply { text = context.getString(R.string.logger_hide_timing); isChecked = hideTiming }
        box.addView(timing)
        box.addView(ui.text(context.getString(R.string.logger_source))); box.addView(src)
        box.addView(ui.text(context.getString(R.string.logger_channel))); box.addView(ch)
        box.addView(ui.text(context.getString(R.string.logger_type))); box.addView(kind)
        AlertDialog.Builder(context).setTitle(R.string.logger_filters).setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                hideTiming = timing.isChecked
                source = sources[src.selectedItemPosition]; channel = ch.selectedItemPosition; type = types[kind.selectedItemPosition]; refresh(true)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
