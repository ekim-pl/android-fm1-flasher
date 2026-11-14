package pl.ekim.fm1_flasher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SourceSettings(state: AppSnapshot, language: String, backend: AppBackend, close: () -> Unit) {
    fun text(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    fun send(kind: AppActionKind, id: String = "", value: String = "") = backend.perform(AppAction(kind, id, value))
    var editor by remember { mutableStateOf<CatalogSource?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CatalogSource?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text(text("catalog_sources")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text("sources_help"))
            if (state.backgroundAvailable) {
                Row {
                    Checkbox(state.backgroundSync, { send(AppActionKind.BACKGROUND_SYNC, it.toString()) }, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = text("sources_background") })
                    Text(text("sources_background"), Modifier.padding(top = 12.dp))
                }
                Text(text("sources_background_help"), style = MaterialTheme.typography.bodySmall)
            }
            state.catalog?.sources.orEmpty().filter { !it.deleted }.forEach { source -> key(source.id) {
                HorizontalDivider()
                Row {
                    Checkbox(source.enabled, { send(AppActionKind.SOURCE_ENABLE, source.id, it.toString()) }, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = source.name })
                    Text(source.name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                }
                Text(source.url, style = MaterialTheme.typography.bodySmall)
                Text(text("source_summary", source.entryCount, if (source.checkedAt == 0L) text("source_never") else formatCatalogTime(source.checkedAt)))
                Text(text("source_next", if (source.nextAttemptAt == 0L) text("source_never") else formatCatalogTime(source.nextAttemptAt)), style = MaterialTheme.typography.bodySmall)
                val hosts = state.catalog?.knownEntries.orEmpty().filter { it.sourceId == source.id }.flatMap { listOfNotNull(it.directUrl, it.cardUrl) }.map { it.substringAfter("://").substringBefore('/') }.distinct().sorted()
                Text(text("source_domains", hosts.joinToString(", ").ifEmpty { "—" }), style = MaterialTheme.typography.bodySmall)
                source.error?.let { Text(text("source_error_retained", source.name, it), color = MaterialTheme.colorScheme.error) }
                TextButton({ send(AppActionKind.SOURCE_CHECK, source.id) }, enabled = !state.busy && !state.offline) { Text(text("source_check")) }
                TextButton({ editor = source }, enabled = !state.busy) { Text(text("source_edit")) }
                if (!source.builtin) TextButton({ removing = source }, enabled = !state.busy) { Text(text("source_remove")) }
            } }
            TextButton({ adding = true }, enabled = !state.busy) { Text(text("source_add")) }
            TextButton({ send(AppActionKind.SOURCE_RESTORE) }, enabled = !state.busy) { Text(text("sources_restore")) }
            Text(text("source_unsigned"), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(close) { Text(text("close")) } })
    if (adding || editor != null) {
        val source = editor
        var name by remember(source?.id) { mutableStateOf(source?.name.orEmpty()) }
        var url by remember(source?.id) { mutableStateOf(source?.url.orEmpty()) }
        var hours by remember(source?.id) { mutableStateOf((source?.intervalHours ?: 24).toString()) }
        var packages by remember(source?.id) { mutableStateOf(source?.packageScopes?.joinToString("\n").orEmpty()) }
        var artwork by remember(source?.id) { mutableStateOf(source?.artworkScopes?.joinToString("\n").orEmpty()) }
        fun scopes(value: String) = value.lines().map(String::trim).filter(String::isNotEmpty).toSet()
        AlertDialog(onDismissRequest = { adding = false; editor = null }, title = { Text(text("source_edit")) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text("source_review"))
                OutlinedTextField(name, { name = it }, label = { Text(text("source_name")) }, enabled = source?.builtin != true, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(text("source_url")) }, enabled = source?.builtin != true, singleLine = true)
                OutlinedTextField(hours, { hours = it }, label = { Text(text("source_hours")) }, singleLine = true)
                Text(text("source_scopes_help"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(packages, { packages = it }, label = { Text(text("source_package_scopes")) })
                OutlinedTextField(artwork, { artwork = it }, label = { Text(text("source_artwork_scopes")) })
                state.sourceError?.let { Text(resolveCoreText(it, language), color = MaterialTheme.colorScheme.error) }
                if (state.savedSourceId != null && state.sourceError == null) Text(text("source_saved"))
            }
        }, confirmButton = {
            TextButton({ backend.perform(AppAction(AppActionKind.SOURCE_SAVE, source?.id.orEmpty(), source = SourceConfiguration(name, url, hours.toIntOrNull() ?: 0, scopes(packages), scopes(artwork)))) }, enabled = !state.busy) { Text(text("source_save")) }
        }, dismissButton = { TextButton({ adding = false; editor = null }) { Text(text("close")) } })
    }
    removing?.let { source -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(text("source_remove")) }, text = { Text("${source.name}\n\n${text("source_remove_help")}") },
        confirmButton = { TextButton({ send(AppActionKind.SOURCE_REMOVE, source.id); removing = null }, enabled = !state.busy) { Text(text("source_remove")) } },
        dismissButton = { TextButton({ removing = null }) { Text(text("cancel")) } }) }
}
