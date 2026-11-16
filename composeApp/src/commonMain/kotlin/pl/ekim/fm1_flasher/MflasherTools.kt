package pl.ekim.fm1_flasher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun MflasherTools(state: AppSnapshot, language: String, send: (AppAction) -> Unit,
                                      confirm: (AppAction, AppText) -> Unit) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    var operation by remember { mutableStateOf(FlashOperation.MBOOT) }
    var selectedSector by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("tools_title"), style = MaterialTheme.typography.titleLarge)
        Text(t("tools_connect"))
        state.diagnostics?.let { info ->
            Text(t("tools_live"), style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(diagnosticsText(info, language)) }
        }
        OutlinedButton({ send(AppAction(AppActionKind.TOOLS_MIDI_INFO)) }, enabled = !state.busy && state.target != null) { Text(t("tools_midi_info")) }
        OutlinedButton({ send(AppAction(AppActionKind.TOOLS_MIDI_BACKUP)) }, enabled = !state.busy && state.target != null) { Text(t("tools_midi_backup")) }
        Button({ send(AppAction(AppActionKind.TOOLS_READ)) }, enabled = !state.busy) { Text(t("tools_read")) }
        OutlinedButton({ send(AppAction(AppActionKind.TOOLS_IMPORT)) }, enabled = !state.busy) { Text(t("tools_import")) }
        Text(t("tools_import_help"), style = MaterialTheme.typography.bodySmall)
        state.backups.forEach { backup ->
            Row {
                TextButton({ send(AppAction(AppActionKind.TOOLS_INSPECT, backup.id)) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("${t("tools_inspect")} · ${backup.label}") }
                TextButton({ send(AppAction(AppActionKind.RECOVERY_EXPORT, backup.id)) }, enabled = !state.busy) { Text(t("logger_export")) }
            }
        }
        state.tools?.let { view ->
            Text(t("tools_snapshot", view.backupId), style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(diagnosticsText(view.info, language) + "\nSHA-256: ${view.hash}") }
            Text(t("tools_health", t(if (view.analysis.appValid) "tools_valid" else "tools_unknown_bad"),
                t(if (view.analysis.bootValid) "tools_valid" else "tools_unknown_bad")))
            view.analysis.bootloader?.let { Text(it) }
            if (!view.analysis.layoutValid) Text(t("tools_layout_unknown"))
            Text(t("tools_map_help"))
            val shown = view.target ?: view.analysis
            val regionNames = shown.regions.map { it.name }.distinct()
            val colors = listOf(Color(0xFF526BB0), Color(0xFF2A8C79), Color(0xFFB07C40), Color(0xFF8A6299), Color(0xFF887754))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(16) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(16) { col ->
                            val address = (row * 16 + col) * FlashLayout.SECTOR
                            val region = shown.regions.first { address in it.start until it.end }
                            val changed = address in view.changed
                            Box(Modifier.weight(1f).height(18.dp).background(if (changed) Color(0xFFE85639) else colors[regionNames.indexOf(region.name) % colors.size])
                                .semantics { contentDescription = "${FlashLayout.hex(address.toLong())}: ${t(region.name)}${if (changed) " · ${t("tools_changed")}" else ""}" }
                                .clickable { selectedSector = address })
                        }
                    }
                }
            }
            selectedSector?.let { address -> Text("${FlashLayout.hex(address.toLong())}: ${t(shown.regions.first { address in it.start until it.end }.name)}") }
            shown.regions.forEach { region -> Text("${t(region.name)}: ${FlashLayout.hex(region.start.toLong())}–${FlashLayout.hex((region.end - 1).toLong())} (${region.end - region.start} B)") }
            view.target?.let { target ->
                Text(t("tools_current_layout"), style = MaterialTheme.typography.titleSmall)
                view.analysis.regions.forEach { r -> Text("${t(r.name)}: ${FlashLayout.hex(r.start.toLong())}–${FlashLayout.hex((r.end - 1).toLong())}", style = MaterialTheme.typography.bodySmall) }
            }
            view.operation?.let { op ->
                Text(t("tools_preview", t("tools_op_${op.name.lowercase()}"), view.changed.size))
                Text(view.changed.joinToString { FlashLayout.hex(it.toLong()) }.ifEmpty { t("tools_no_changes") })
                if (view.layoutChanges) Text(t("tools_layout_warning"))
                Text(t("tools_write_gated"))
                TextButton({ send(AppAction(AppActionKind.RECOVERY_EXPORT, view.backupId)) }, enabled = !state.busy) { Text(t("tools_export_before")) }
                Button({ confirm(AppAction(AppActionKind.TOOLS_WRITE, view.token.orEmpty()), msg("tools_confirm")) },
                    enabled = !state.busy && view.qualified && view.exported && view.token != null) { Text(t("tools_write")) }
            }
        }
        Choice(t("tools_operation"), t("tools_op_${operation.name.lowercase()}"), FlashOperation.entries.map { it.name to t("tools_op_${it.name.lowercase()}") },
            { operation = FlashOperation.valueOf(it) }, enabled = !state.busy)
        Text(t("tools_preview_help"))
        Button({ send(AppAction(AppActionKind.TOOLS_PREPARE, value = operation.name)) }, enabled = !state.busy) { Text(t("tools_prepare")) }
    }
}

private fun diagnosticsText(info: DeviceDiagnostics, language: String): String {
    fun t(key: String) = resolveCoreText(msg(key), language)
    val key = if (info.chipKey < 0) t("tools_unknown_bad") else FlashLayout.hex(info.chipKey.toLong(), 4)
    return "${t("tools_transport")}: ${info.transport}\n${t("tools_flash_id")}: ${FlashLayout.hex(info.flashId)}\n" +
        "${t("tools_chip_key")}: $key\n${t("tools_capacity")}: ${info.capacity} B\nUID: ${info.uid ?: "—"}\n${t("tools_loader")}: ${info.loader ?: "—"}"
}
