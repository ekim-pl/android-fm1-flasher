package pl.ekim.fm1_flasher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun MflasherTools(state: AppSnapshot, language: String, send: (AppAction) -> Unit,
                                      confirm: (AppAction, AppText) -> Unit) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    var operation by rememberSaveable { mutableStateOf(FlashOperation.MBOOT) }
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val idle = !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("tools_title"), Modifier.semantics { heading() }, style = typography.titleLarge)
            Text(t("tools_rom_help"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(AppAction(AppActionKind.TOOLS_READ, value = "USER_DATA")) }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text(t("tools_backup_data"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ send(AppAction(AppActionKind.TOOLS_READ)) }, enabled = idle) { Text(t("tools_read")) }
            }
        }
        state.tools?.let { view ->
            val analysis = view.analysis
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("tools_snapshot", view.backupId.take(8)), Modifier.semantics { heading() }, style = typography.titleMedium)
                if (analysis == null) {
                    Text(t("tools_restore_data_help"), style = typography.bodyMedium)
                    if (view.operation == null) {
                        FilledTonalButton({ send(AppAction(AppActionKind.TOOLS_PREPARE, view.backupId, FlashOperation.RESTORE_DATA.name)) },
                            enabled = idle) { Text(t("tools_op_restore_data")) }
                    }
                } else {
                    BackupHealth(analysis, language)
                }
                view.operation?.let { op ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t("tools_preview", t("tools_op_${op.name.lowercase()}"), view.changed.size), style = typography.titleSmall)
                            if (view.layoutChanges) Text(t("tools_layout_warning"), style = typography.bodyMedium)
                            Text(t(if (view.token == null) "tools_write_unavailable" else if (!view.exported) "tools_export_required" else "tools_write_ready"),
                                style = typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ send(AppAction(AppActionKind.RECOVERY_EXPORT, view.backupId)) }, enabled = idle) {
                                    Text(t("tools_export_before"))
                                }
                                Button({ confirm(AppAction(AppActionKind.TOOLS_WRITE, view.token.orEmpty()), msg(if (op == FlashOperation.RESTORE_DATA) "tools_confirm_data" else "tools_confirm")) },
                                    enabled = idle && view.exported && view.token != null) { Text(t("tools_write")) }
                            }
                        }
                    }
                }
                BackupDetails(view, language)
            }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("tools_write_options"), Modifier.semantics { heading() }, style = typography.titleMedium)
            Choice(t("tools_operation"), t("tools_op_${operation.name.lowercase()}"),
                FlashOperation.entries.filter { it != FlashOperation.RESTORE_DATA }.map { it.name to t("tools_op_${it.name.lowercase()}") },
                { operation = FlashOperation.valueOf(it) }, enabled = idle)
            Text(t("tools_preview_help"), style = typography.bodySmall, color = colors.onSurfaceVariant)
            FilledTonalButton({ send(AppAction(AppActionKind.TOOLS_PREPARE, value = operation.name)) },
                enabled = idle && (operation !in listOf(FlashOperation.RESTORE, FlashOperation.RESTORE_BOOT) || state.tools?.analysis != null)) {
                Text(t("tools_prepare"))
            }
        }
    }
}

@Composable internal fun MidiBackupTools(state: AppSnapshot, language: String, send: (AppAction) -> Unit) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val idle = !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("tools_midi_heading"), style = typography.titleLarge)
        Text(t("tools_midi_help"), style = typography.bodySmall, color = colors.onSurfaceVariant)
        state.diagnostics?.takeIf { it.transport == "MIDI" }?.let { info ->
            Text(t("tools_live"), style = typography.titleSmall)
            SelectionContainer { Text(diagnosticsText(info, language), style = typography.bodySmall) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ send(AppAction(AppActionKind.TOOLS_MIDI_INFO)) }, enabled = idle && state.target != null) { Text(t("tools_midi_info")) }
            OutlinedButton({ send(AppAction(AppActionKind.TOOLS_MIDI_BACKUP)) }, enabled = idle && state.target != null) { Text(t("tools_midi_backup")) }
        }
        HorizontalDivider()
        TextButton({ send(AppAction(AppActionKind.TOOLS_IMPORT)) }, enabled = idle) { Text(t("tools_import")) }
        Text(t("tools_import_help"), style = typography.bodySmall, color = colors.onSurfaceVariant)
        if (state.backups.isNotEmpty()) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("tools_saved_backups"), Modifier.semantics { heading() }, style = typography.titleMedium)
                state.backups.forEach { backup ->
                    val selected = state.tools?.backupId == backup.id
                    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface) {
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                            Text(t(if (backup.dataOnly) "tools_user_data" else "tools_full_backup"), style = typography.titleSmall)
                            Text(backup.label.replace(backup.id, backup.id.take(8)), style = typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ send(AppAction(AppActionKind.TOOLS_INSPECT, backup.id)) }, enabled = idle) {
                                    Text(t("tools_inspect"))
                                }
                                TextButton({ send(AppAction(AppActionKind.RECOVERY_EXPORT, backup.id)) }, enabled = idle) {
                                    Text(t("logger_export"))
                                }
                            }
                        }
                    }
                }
            }
        }
        state.tools?.let { view ->
            Text(resolveCoreText(msg("tools_snapshot", view.backupId.take(8)), language), style = typography.titleMedium)
            val analysis = view.analysis
            if (analysis != null) BackupHealth(analysis, language)
            else Text(t("tools_restore_data_help"), style = typography.bodyMedium)
            BackupDetails(view, language)
        }
    }
}

@Composable private fun BackupHealth(analysis: FlashLayout.Analysis, language: String) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Text(t("tools_health", t(if (analysis.appValid) "tools_valid" else "tools_unknown_bad"),
        t(if (analysis.bootValid) "tools_valid" else "tools_unknown_bad")), style = typography.bodyMedium)
    analysis.bootloader?.let { Text(it, style = typography.bodySmall, color = colors.onSurfaceVariant) }
    if (!analysis.layoutValid) Text(t("tools_layout_unknown"), style = typography.bodySmall)
}

@Composable private fun BackupDetails(view: ToolsView, language: String) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    val typography = MaterialTheme.typography
    val analysis = view.analysis
    var details by rememberSaveable(view.backupId) { mutableStateOf(false) }
    TextButton({ details = !details }) {
        Text(t(if (details) "tools_hide_details" else if (analysis != null) "tools_show_map" else "technical_info"))
    }
    if (details) {
        SelectionContainer { Text(diagnosticsText(view.info, language) + "\nSHA-256: ${view.hash}", style = typography.bodySmall) }
        if (analysis != null) FlashMap(view, analysis, language)
        if (view.operation != null) {
            SelectionContainer { Text(view.changed.joinToString { FlashLayout.hex(it.toLong()) }.ifEmpty { t("tools_no_changes") },
                style = typography.bodySmall) }
        }
    }
}

@Composable private fun FlashMap(view: ToolsView, analysis: FlashLayout.Analysis, language: String) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    val layout = view.target ?: analysis
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val regionNames = layout.regions.map { it.name }.distinct()
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.inversePrimary, colors.outline)
    fun regionColor(name: String) = if (name == "tools_region_unknown") colors.surfaceContainerHighest
        else palette[regionNames.indexOf(name) % palette.size]
    val changed = remember(view.changed) { view.changed.toSet() }
    var selectedSector by rememberSaveable(view.backupId) { mutableStateOf<Int?>(null) }
    Text(t("tools_map_help"), style = typography.bodySmall, color = colors.onSurfaceVariant)
    Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(16) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(16) { col ->
                    val address = (row * 16 + col) * FlashLayout.SECTOR
                    val region = layout.regions.first { address in it.start until it.end }
                    val label = "${FlashLayout.hex(address.toLong())} · ${t(region.name)}"
                    Box(Modifier.weight(1f).aspectRatio(1f).background(
                        if (address in changed) colors.error else regionColor(region.name), MaterialTheme.shapes.extraSmall)
                        .semantics { contentDescription = if (address in changed) "$label · ${t("tools_changed")}" else label }
                        .clickable { selectedSector = address })
                }
            }
        }
    }
    selectedSector?.let { address ->
        Text("${FlashLayout.hex(address.toLong())} · ${t(layout.regions.first { address in it.start until it.end }.name)}",
            style = typography.titleSmall)
    }
    if (changed.isNotEmpty()) Text(t("tools_changed_legend"), style = typography.labelMedium, color = colors.error)
    layout.regions.forEach { region ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(regionColor(region.name), MaterialTheme.shapes.extraSmall))
            Text("${t(region.name)} · ${FlashLayout.hex(region.start.toLong())}–${FlashLayout.hex((region.end - 1).toLong())}",
                style = typography.bodySmall)
        }
    }
    if (view.target != null && view.layoutChanges) {
        Text(t("tools_current_layout"), style = typography.titleSmall)
        analysis.regions.forEach { r ->
            Text("${t(r.name)} · ${FlashLayout.hex(r.start.toLong())}–${FlashLayout.hex((r.end - 1).toLong())}", style = typography.bodySmall)
        }
    }
}

private fun diagnosticsText(info: DeviceDiagnostics, language: String): String {
    fun t(key: String) = resolveCoreText(msg(key), language)
    val key = if (info.chipKey < 0) t("tools_unknown_bad") else FlashLayout.hex(info.chipKey.toLong(), 4)
    return "${t("tools_transport")}: ${info.transport}\n${t("tools_flash_id")}: ${FlashLayout.hex(info.flashId)}\n" +
        "${t("tools_chip_key")}: $key\n${t("tools_capacity")}: ${info.capacity} B\nUID: ${info.uid ?: "—"}\n${t("tools_loader")}: ${info.loader ?: "—"}"
}
