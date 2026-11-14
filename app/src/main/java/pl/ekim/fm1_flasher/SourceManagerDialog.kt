package pl.ekim.fm1_flasher

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

/** Uses service snapshots only; SQLite and network operations stay off the UI thread. */
class SourceManagerDialog(private val activity: Activity, private val service: () -> FlasherService?,
                          private val filter: (String?) -> Unit) {
    private val ui = Ui(activity)
    private val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12)) }
    private val dialog = AlertDialog.Builder(activity).setTitle(R.string.catalog_sources)
        .setView(ScrollView(activity).apply { addView(content) })
        .setNegativeButton(R.string.close, null).create()
    private var state = UiState()
    private val children = mutableSetOf<AlertDialog>()
    private fun present(child: AlertDialog) {
        children += child
        child.setOnDismissListener { children -= child }
        child.show()
    }
    private fun s(id: Int, vararg args: Any) = activity.s(id, *args)
    private fun date(stamp: Long) = if (stamp <= 0) s(R.string.source_never) else DateFormat.getDateTimeInstance().format(Date(stamp))

    fun show(state: UiState) { dialog.show(); render(state) }
    fun dismiss() { children.toList().forEach { it.dismiss() }; dialog.dismiss() }
    fun render(next: UiState) {
        state = next
        if (!dialog.isShowing) return
        content.removeAllViews()
        content.addView(ui.text(s(R.string.sources_help), 13, Ui.MUTED))
        val sources = next.catalog?.sources.orEmpty().filter { !it.deleted }
        sources.forEach { source ->
            content.addView(CheckBox(activity).apply {
                text = source.name; isChecked = source.enabled; isEnabled = !next.busy
                setOnCheckedChangeListener { _, checked -> service()?.setSourceEnabled(source.id, checked) }
            })
            content.addView(ui.text(java.net.URI(source.url).host, 12, Ui.MUTED))
            content.addView(ui.text(s(R.string.source_updated, source.updated.ifBlank { s(R.string.source_never) }), 12, Ui.MUTED))
            content.addView(ui.text(s(R.string.source_summary, source.entryCount, date(source.checkedAt)), 12, Ui.MUTED))
            if (source.error != null) content.addView(ui.text(s(R.string.source_error_retained, source.name, source.error), 12, Ui.WARNING))
            content.addView(ui.button(s(R.string.source_details)) { details(source.id) }.apply { contentDescription = source.name + " · " + s(R.string.source_details) })
        }
        content.addView(ui.button(s(R.string.source_add)) { edit(null) }.apply { isEnabled = !next.busy })
        content.addView(ui.button(s(R.string.sources_restore)) { service()?.restoreSources() }.apply { isEnabled = !next.busy })
        content.addView(ui.button(s(R.string.source_filter)) {
            AlertDialog.Builder(activity).setTitle(R.string.source_filter)
                .setItems((listOf(s(R.string.source_all)) + sources.map { it.name }).toTypedArray()) { _, which ->
                    filter(sources.getOrNull(which - 1)?.id); dialog.dismiss()
                }.create().also(::present)
        })
        content.addView(CheckBox(activity).apply {
            text = s(R.string.sources_background); isChecked = next.backgroundSync; isEnabled = !next.busy
            setOnCheckedChangeListener { _, checked -> service()?.setBackgroundSync(checked) }
        })
        content.addView(ui.text(s(R.string.sources_background_help), 12, Ui.MUTED))
    }

    private fun details(id: String) {
        val source = state.catalog?.sources?.find { it.id == id } ?: return
        val scopes = state.catalog?.knownEntries.orEmpty().filter { it.sourceId == id }.flatMap { listOfNotNull(it.directUrl, it.cardUrl) }
            .mapNotNull { runCatching { java.net.URI(it).host }.getOrNull() }.distinct().sorted().joinToString(", ")
        val text = listOf(source.url, s(R.string.source_base, source.baseUrl),
            s(R.string.source_summary, source.entryCount, date(source.checkedAt)),
            s(R.string.source_updated, source.updated.ifBlank { s(R.string.source_never) }),
            s(R.string.source_next, date(source.nextAttemptAt)), s(R.string.source_unsigned),
            s(R.string.source_domains, scopes), s(R.string.source_hours_value, source.intervalHours),
            s(R.string.source_policy, source.packageScopes.joinToString("\n"), source.artworkScopes.joinToString("\n")), source.license, source.error.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
        val view = ui.column().apply {
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12))
            addView(ui.text(text, 13, Ui.TEXT))
        }
        val detail = AlertDialog.Builder(activity).setTitle(source.name)
            .setView(ScrollView(activity).apply { addView(view) }).setNegativeButton(R.string.close, null).create()
        view.addView(ui.button(s(R.string.source_check)) { detail.dismiss(); service()?.checkSource(id) }
            .apply { isEnabled = !state.busy && !state.offline })
        view.addView(ui.button(s(R.string.source_edit)) { detail.dismiss(); edit(source) }.apply { isEnabled = !state.busy })
        if (!source.builtin) view.addView(ui.button(s(R.string.source_remove)) {
            detail.dismiss()
            AlertDialog.Builder(activity).setTitle(R.string.source_remove).setMessage(R.string.source_remove_help)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.source_remove) { _, _ -> service()?.removeSource(id) }.create().also(::present)
        }.apply { isEnabled = !state.busy })
        present(detail)
    }

    private fun edit(source: CatalogSource?) {
        val form = ui.column().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(12)) }
        fun field(label: Int, value: String, type: Int): EditText {
            val title = ui.text(s(label), 13, Ui.TEXT)
            val edit = EditText(activity).apply { setText(value); inputType = type; id = android.view.View.generateViewId() }
            title.labelFor = edit.id; form.addView(title); form.addView(edit, LinearLayout.LayoutParams(-1, -2)); return edit
        }
        val name = field(R.string.source_name, source?.name.orEmpty(), InputType.TYPE_CLASS_TEXT)
        val url = field(R.string.source_url, source?.url.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        name.isEnabled = source?.builtin != true; url.isEnabled = source?.builtin != true
        val hours = field(R.string.source_hours, (source?.intervalHours ?: 24).toString(), InputType.TYPE_CLASS_NUMBER)
        form.addView(ui.text(s(R.string.source_review), 13, Ui.MUTED))
        val referenced = state.catalog?.knownEntries.orEmpty().filter { it.sourceId == source?.id }
            .flatMap { listOfNotNull(it.directUrl, it.cardUrl) }.mapNotNull { runCatching { java.net.URI(it).host }.getOrNull() }
            .distinct().sorted().joinToString(", ")
        if (referenced.isNotEmpty()) form.addView(ui.text(s(R.string.source_domains, referenced), 13, Ui.MUTED))
        form.addView(ui.text(s(R.string.source_scopes_help), 13, Ui.MUTED))
        val packages = field(R.string.source_package_scopes, source?.packageScopes?.joinToString("\n").orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val artwork = field(R.string.source_artwork_scopes, source?.artworkScopes?.joinToString("\n").orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val error = ui.text("", 13, Ui.WARNING); form.addView(error)
        val editor = AlertDialog.Builder(activity).setTitle(if (source == null) R.string.source_add else R.string.source_edit)
            .setView(ScrollView(activity).apply { addView(form) }).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.source_save, null)
            .setNeutralButton(R.string.source_save_check, null).create()
        editor.setOnShowListener {
            fun save(check: Boolean) {
                if (state.busy || service() == null) return
                val interval = hours.text.toString().toIntOrNull()
                fun scopes(edit: EditText) = edit.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                try {
                    UrlScope.canonical(url.text.toString()); (scopes(packages) + scopes(artwork)).forEach(UrlScope::canonical)
                    require(interval != null && interval in 24..168 && name.text.isNotBlank())
                } catch (_: Exception) { error.text = s(R.string.source_invalid_config); return }
                editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                editor.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                service()?.configureSource(source?.id, name.text.toString(), url.text.toString(), interval!!, scopes(packages), scopes(artwork), check) { savedId, failure ->
                    if (activity.isDestroyed || activity.isFinishing) return@configureSource
                    if (failure == null) {
                        editor.dismiss()
                        if (check && savedId != null) {
                            state = service()?.state ?: state
                            details(savedId)
                        }
                    } else {
                        error.text = activity.resolve(failure)
                        editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        editor.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    }
                }
            }
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(false) }
            editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(true) }
        }
        present(editor)
    }
}
