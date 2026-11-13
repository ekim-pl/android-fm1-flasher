package pl.fm1.flasher

import android.content.Context
import android.view.View
import android.widget.*

class FlasherPanel(context: Context, connectAction: (UsbMidi) -> Unit, resumeAction: (UsbMidi) -> Unit,
    chipAction: (FlashChip) -> Unit, flashAction: () -> Unit, logsAction: () -> Unit) : ScrollView(context) {
    private val ui = Ui(context)
    private val deviceSpinner = ui.spinner(listOf("Wybierz port USB MIDI…"))
    private val chipSpinner = ui.spinner(FlashChip.entries.map { it.label })
    private val reconnectSpinner = ui.spinner(listOf("Wskaż port tego samego FM‑1…"))
    private val targetText = ui.text("", 13, Ui.MUTED)
    private val readyText = ui.text("", 17, Ui.TEXT, true)
    private val compatibility = ui.text("", 12, Ui.MUTED)
    private val status = ui.text("", 13, Ui.MUTED)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val reconnectCard = ui.card()
    private var devices = emptyList<UsbMidi>()
    private var candidates = emptyList<UsbMidi>()
    private var state = UiState()
    private val connect = ui.button("Połącz z wybranym portem") { devices.getOrNull(deviceSpinner.selectedItemPosition - 1)?.let(connectAction) }
    private val resume = ui.button("Potwierdź port i kontynuuj", true) { candidates.getOrNull(reconnectSpinner.selectedItemPosition - 1)?.let(resumeAction) }
    private val flash = ui.button("Zainstaluj na wybranym FM‑1", true, flashAction)

    init {
        isFillViewport = true; clipToPadding = false
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(24)) }
        content.addView(ui.label("USB MIDI  /  JEDEN WYBRANY INSTRUMENT"))
        content.addView(ui.gap(12)); content.addView(ui.text("Twój FM‑1.\nPod pełną kontrolą.", 29, Ui.TEXT, true))
        content.addView(ui.gap(16))
        reconnectCard.addView(ui.label("POTWIERDŹ PORT PO RESTARCIE")); reconnectCard.addView(ui.gap(10))
        reconnectCard.addView(ui.text("Wskaż ten sam instrument. Pozostałe porty huba są wykluczone z ponownego połączenia.", 13, Ui.MUTED))
        reconnectCard.addView(ui.gap(10)); reconnectCard.addView(reconnectSpinner); reconnectCard.addView(resume)
        reconnectCard.visibility = View.GONE; content.addView(reconnectCard)
        val target = ui.card()
        target.addView(ui.label("01  /  WYBIERZ URZĄDZENIE")); target.addView(ui.gap(12))
        deviceSpinner.contentDescription = "Wybierz jedno urządzenie USB MIDI"
        target.addView(deviceSpinner); target.addView(connect); target.addView(ui.gap(10)); target.addView(targetText)
        target.addView(ui.gap(18)); target.addView(ui.text("Kość flash instrumentu", 12, Ui.MUTED)); target.addView(ui.gap(6))
        chipSpinner.contentDescription = "Typ kości flash wybranego FM‑1"
        chipSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (FlashChip.entries[position] != state.chip) chipAction(FlashChip.entries[position])
            }
        }
        target.addView(chipSpinner); target.addView(ui.gap(8))
        target.addView(ui.text("Nie znasz typu pamięci? Pozostaw pierwszy wybór. Dopuszczamy wtedy tylko paczki z obsługą nowej kości.", 12, Ui.MUTED))
        content.addView(target)
        val packageCard = ui.card()
        packageCard.addView(ui.label("02  /  LOKALNA PACZKA")); packageCard.addView(ui.gap(12)); packageCard.addView(readyText)
        packageCard.addView(ui.gap(8)); packageCard.addView(compatibility); packageCard.addView(flash)
        packageCard.addView(ui.gap(12)); packageCard.addView(progress, LinearLayout.LayoutParams(-1, ui.dp(8)))
        packageCard.addView(ui.gap(12)); packageCard.addView(status)
        packageCard.addView(ui.button("Dziennik połączenia", action = logsAction))
        content.addView(packageCard)
        content.addView(ui.text("Wersja eksperymentalna · zapis na fizycznym FM‑1 wymaga weryfikacji. Przed aktualizacją zrób kopię danych instrumentu.", 12, Ui.MUTED))
        addView(content)
    }

    fun render(next: UiState) {
        val wasAwaiting = state.awaitingPort
        state = next
        if (devices.map { it.endpoint } != next.devices.map { it.endpoint }) {
            val selected = devices.getOrNull(deviceSpinner.selectedItemPosition - 1)?.endpoint ?: next.target?.endpoint
            devices = next.devices
            deviceSpinner.adapter = ui.spinner(listOf("Wybierz port USB MIDI…") + devices.map { it.label }).adapter
            deviceSpinner.setSelection(devices.indexOfFirst { it.endpoint == selected }.let { if (it < 0) 0 else it + 1 })
        }
        deviceSpinner.isEnabled = !next.busy
        connect.isEnabled = !next.busy && devices.isNotEmpty()
        chipSpinner.isEnabled = !next.busy && next.target != null
        if (chipSpinner.selectedItemPosition != next.chip.ordinal) chipSpinner.setSelection(next.chip.ordinal)
        targetText.text = next.target?.let { "${it.label}\nTożsamość: ${next.identity ?: "oczekiwanie"}" }
            ?: "Podłącz hub OTG i wskaż port. Dostępne porty: ${next.devices.size}."
        readyText.text = next.ready?.let { "${it.name}\n${it.version}" } ?: "Nie wybrano paczki"
        compatibility.text = next.ready?.let {
            if (next.chip.allows(it)) "Zapisana na telefonie · gotowa offline"
            else "Ta lokalna wersja nie ma potwierdzonej obsługi wybranej kości flash."
        } ?: "Wybierz pobrany firmware w bibliotece i naciśnij „Użyj do flashowania”."
        compatibility.setTextColor(if (next.ready != null && !next.chip.allows(next.ready)) Ui.WARNING else Ui.MUTED)
        flash.isEnabled = !next.busy && next.target != null && next.identity != null && next.ready != null &&
            next.chip.allows(next.ready) && next.devices.any { it.endpoint == next.target.endpoint } &&
            next.downloads.any { it.entry.id == next.ready.id && it.entry.sha256 == next.ready.sha256 }
        status.text = next.status
        progress.isIndeterminate = next.busy && next.progress < 0
        if (next.progress >= 0) progress.progress = next.progress
        progress.visibility = if (next.busy || next.progress >= 0) View.VISIBLE else View.GONE
        reconnectCard.visibility = if (next.awaitingPort) View.VISIBLE else View.GONE
        if (candidates.map { it.endpoint } != next.reconnectCandidates.map { it.endpoint }) {
            candidates = next.reconnectCandidates
            reconnectSpinner.adapter = ui.spinner(listOf("Wskaż port tego samego FM‑1…") + candidates.map { it.label }).adapter
        }
        resume.isEnabled = next.awaitingPort && candidates.isNotEmpty()
        if (next.awaitingPort && !wasAwaiting) post { smoothScrollTo(0, 0) }
    }
}
