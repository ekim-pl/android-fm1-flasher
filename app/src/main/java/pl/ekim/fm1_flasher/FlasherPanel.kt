package pl.ekim.fm1_flasher

import android.content.Context
import android.view.View
import android.widget.*

class FlasherPanel(context: Context, connectAction: (UsbMidi) -> Unit, resumeAction: (UsbMidi) -> Unit,
    recoveryAction: () -> Unit, detectAction: () -> Unit, chipAction: (FlashChip) -> Unit, firmwareAction: (String) -> Unit, flashAction: () -> Unit, logsAction: () -> Unit) : LinearLayout(context) {
    private val ui = Ui(context)
    private val deviceSpinner = ui.spinner(listOf(s(R.string.select_port)))
    private val chipSpinner = ui.spinner(FlashChip.entries.map { s(it.label) })
    private val reconnectSpinner = ui.spinner(listOf(s(R.string.select_reconnect_port)))
    private val firmwareSpinner = ui.spinner(listOf(s(R.string.no_downloaded_firmware)))
    private val targetText = ui.text("", 13, Ui.MUTED)
    private val compatibility = ui.text("", 12, Ui.MUTED)
    private val status = ui.text("", 13, Ui.MUTED)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val reconnectCard = ui.card()
    private val reconnectScroll: ScrollView
    private var devices = emptyList<UsbMidi>()
    private var candidates = emptyList<UsbMidi>()
    private var downloads = emptyList<LocalPackage>()
    private var state = UiState()
    private val connect = ui.button(s(if (ui.twoColumns) R.string.connect_short else R.string.connect_selected)) { devices.getOrNull(deviceSpinner.selectedItemPosition - 1)?.let(connectAction) }
    private val resume = ui.button(s(R.string.resume_port), true) { candidates.getOrNull(reconnectSpinner.selectedItemPosition - 1)?.let(resumeAction) }
    private val detect = ui.button(s(R.string.detect_chip), action = detectAction)
    private val recovery = ui.button(s(R.string.recovery_title), action = recoveryAction)
    private val flash = ui.button(s(R.string.install_selected), true, flashAction)

    init {
        orientation = if (ui.twoColumns) HORIZONTAL else VERTICAL
        clipToPadding = false
        reconnectCard.addView(ui.label(s(R.string.reconnect_heading))); reconnectCard.addView(ui.gap(if (ui.twoColumns) 8 else 10))
        reconnectCard.addView(ui.text(s(R.string.reconnect_help), 13, Ui.MUTED))
        reconnectCard.addView(ui.gap(if (ui.twoColumns) 8 else 10)); reconnectCard.addView(reconnectSpinner); reconnectCard.addView(resume)
        reconnectCard.visibility = View.GONE
        val target = ui.card()
        target.addView(ui.label(s(R.string.select_device_heading))); target.addView(ui.gap(if (ui.twoColumns) 8 else 12))
        deviceSpinner.contentDescription = s(R.string.select_device_accessibility)
        if (ui.twoColumns) {
            val connection = ui.row().apply {
                addView(deviceSpinner, LayoutParams(0, ui.dp(48), 1f))
                addView(connect, LayoutParams(ui.dp(96), ui.dp(48)).apply { marginStart = ui.dp(8) })
            }
            target.addView(connection)
        } else { target.addView(deviceSpinner); target.addView(connect) }
        target.addView(ui.gap(if (ui.twoColumns) 8 else 10)); target.addView(targetText)
        target.addView(ui.gap(if (ui.twoColumns) 8 else 18))
        chipSpinner.contentDescription = s(R.string.flash_chip_accessibility)
        chipSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (FlashChip.entries[position] != state.chip) chipAction(FlashChip.entries[position])
            }
        }
        if (ui.twoColumns) {
            target.addView(ui.row().apply {
                addView(ui.text(s(R.string.flash_chip_label), 12, Ui.MUTED), LayoutParams(ui.dp(108), -2).apply { marginEnd = ui.dp(8) })
                addView(chipSpinner, LayoutParams(0, ui.dp(48), 1f))
            })
        } else {
            target.addView(ui.text(s(R.string.flash_chip_label), 12, Ui.MUTED)); target.addView(ui.gap(6)); target.addView(chipSpinner)
        }
        target.addView(ui.gap(8))
        target.addView(ui.text(s(R.string.flash_chip_help), 12, Ui.MUTED))
        target.addView(detect)
        target.addView(recovery)
        val packageCard = ui.card()
        if (ui.twoColumns) {
            packageCard.addView(ui.row().apply {
                addView(ui.label(s(R.string.local_package_heading)), LayoutParams(0, -2, 1f))
                addView(ui.button(s(R.string.log_short), action = logsAction), LayoutParams(-2, ui.dp(48)).apply { marginStart = ui.dp(8) })
            })
        } else { packageCard.addView(ui.label(s(R.string.local_package_heading))); packageCard.addView(ui.gap(12)) }
        firmwareSpinner.contentDescription = s(R.string.select_downloaded_firmware)
        firmwareSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = downloads.getOrNull(position - 1)?.entry
                if (selected == null) {
                    val current = downloads.indexOfFirst { it.entry.id == state.ready?.id } + 1
                    if (current > 0) firmwareSpinner.setSelection(current)
                    return
                }
                if (!state.busy && selected.id != state.ready?.id) firmwareAction(selected.id)
            }
        }
        packageCard.addView(firmwareSpinner, LayoutParams(-1, ui.dp(56)))
        packageCard.addView(ui.gap(8)); packageCard.addView(compatibility); packageCard.addView(flash)
        packageCard.addView(ui.gap(if (ui.twoColumns) 6 else 12)); packageCard.addView(progress, LayoutParams(-1, ui.dp(8)))
        packageCard.addView(ui.gap(if (ui.twoColumns) 6 else 12)); packageCard.addView(status)
        if (!ui.twoColumns) packageCard.addView(ui.button(s(R.string.connection_log), action = logsAction))
        if (ui.twoColumns) {
            setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(8))
            val deviceScroll = ScrollView(context).apply { isFillViewport = true; addView(target, FrameLayout.LayoutParams(-1, -2)) }
            val actions = ui.column().apply { addView(reconnectCard); addView(packageCard) }
            reconnectScroll = ScrollView(context).apply { isFillViewport = true; addView(actions) }
            addView(deviceScroll, LayoutParams(0, -1, 1f).apply { marginEnd = ui.dp(12) })
            addView(reconnectScroll, LayoutParams(0, -1, 1f))
        } else {
            val content = ui.column().apply {
                setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(24))
                addView(ui.label(s(R.string.flasher_eyebrow)))
                addView(ui.gap(12)); addView(ui.text(s(R.string.flasher_heading), 29, Ui.TEXT, true)); addView(ui.gap(16))
                addView(reconnectCard); addView(target); addView(packageCard)
            }
            reconnectScroll = ScrollView(context).apply { isFillViewport = true; addView(content) }
            addView(reconnectScroll, LayoutParams(-1, -1))
        }
    }

    fun render(next: UiState) {
        val wasAwaiting = state.awaitingPort
        val previousTarget = state.target?.endpoint
        state = next
        if (devices.map { it.endpoint } != next.devices.map { it.endpoint } || previousTarget != next.target?.endpoint) {
            val selected = next.target?.endpoint ?: devices.getOrNull(deviceSpinner.selectedItemPosition - 1)?.endpoint
            devices = next.devices
            deviceSpinner.adapter = ui.spinner(listOf(s(R.string.select_port)) + devices.map { it.label }).adapter
            deviceSpinner.setSelection(devices.indexOfFirst { it.endpoint == selected }.let { if (it < 0) 0 else it + 1 })
        }
        recovery.visibility = if (next.experimental) View.VISIBLE else View.GONE
        detect.visibility = recovery.visibility
        detect.isEnabled = !next.busy
        recovery.isEnabled = !next.busy
        deviceSpinner.isEnabled = !next.busy
        connect.isEnabled = !next.busy && devices.isNotEmpty()
        chipSpinner.isEnabled = !next.busy && next.target != null
        if (chipSpinner.selectedItemPosition != next.chip.ordinal) chipSpinner.setSelection(next.chip.ordinal)
        targetText.text = next.target?.let { s(R.string.connected_identity, it.label, next.identity?.toString() ?: s(R.string.identity_waiting)) }
            ?: s(R.string.connect_otg, next.devices.size)
        if (downloads != next.downloads) {
            downloads = next.downloads
            val placeholder = s(if (downloads.isEmpty()) R.string.no_downloaded_firmware else R.string.select_downloaded_firmware)
            firmwareSpinner.adapter = ui.spinner(listOf(placeholder) + downloads.map { "${context.entryTitle(it.entry)} · ${it.entry.version}" }).adapter
        }
        val firmwarePosition = downloads.indexOfFirst { it.entry.id == next.ready?.id } + 1
        if (firmwareSpinner.selectedItemPosition != firmwarePosition) firmwareSpinner.setSelection(firmwarePosition)
        firmwareSpinner.isEnabled = !next.busy && downloads.isNotEmpty()
        compatibility.text = next.ready?.let {
            if (next.chip.allows(it)) s(R.string.saved_ready)
            else s(R.string.chip_not_supported)
        } ?: s(R.string.choose_downloaded_help)
        compatibility.setTextColor(if (next.ready != null && !next.chip.allows(next.ready)) Ui.WARNING else Ui.MUTED)
        flash.isEnabled = !next.busy && next.target != null && next.identity != null && next.ready != null &&
            next.chip.allows(next.ready) && next.devices.any { it.endpoint == next.target.endpoint } &&
            next.downloads.any { it.entry.id == next.ready.id && it.entry.sha256 == next.ready.sha256 }
        status.text = context.resolve(next.status)
        progress.isIndeterminate = next.busy && next.progress < 0
        if (next.progress >= 0) progress.progress = next.progress
        progress.visibility = if (next.busy || next.progress >= 0) View.VISIBLE else View.GONE
        reconnectCard.visibility = if (next.awaitingPort) View.VISIBLE else View.GONE
        if (candidates.map { it.endpoint } != next.reconnectCandidates.map { it.endpoint }) {
            candidates = next.reconnectCandidates
            reconnectSpinner.adapter = ui.spinner(listOf(s(R.string.select_reconnect_port)) + candidates.map { it.label }).adapter
        }
        resume.isEnabled = next.awaitingPort && candidates.isNotEmpty()
        if (next.awaitingPort && !wasAwaiting) post { reconnectScroll.smoothScrollTo(0, 0) }
    }
}
