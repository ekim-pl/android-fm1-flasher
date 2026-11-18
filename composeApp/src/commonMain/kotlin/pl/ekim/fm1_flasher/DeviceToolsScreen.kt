package pl.ekim.fm1_flasher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun DeviceToolsScreen(
    state: AppSnapshot, language: String, send: (AppAction) -> Unit,
    confirm: (AppAction, AppText) -> Unit, recovery: () -> Unit, midiTools: () -> Unit,
) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    val target = state.target
    val identity = state.identity
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("device_tools"), Modifier.semantics { heading() }, style = typography.headlineMedium)
                Text(t("tools_rom_help"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (state.usbRecovery) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("mode_download"), Modifier.semantics { heading() }, style = typography.titleLarge)
                    if (state.experimental) {
                        Text(t("mode_download_help"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
                        val canEnter = target != null && identity?.isLoader == false
                        if (!canEnter) {
                            Text(t("mode_enter_requirement"), style = typography.bodySmall, color = colors.onSurfaceVariant)
                        } else {
                            Text(t("mode_current", target?.label.orEmpty()), style = typography.bodyMedium)
                        }
                        OutlinedButton(
                            { confirm(AppAction(AppActionKind.RECOVERY_ENTER, target?.id.orEmpty()), msg("recovery_enter_confirm", target?.label.orEmpty())) },
                            enabled = !state.busy && canEnter,
                        ) { Text(t("recovery_enter")) }
                        HorizontalDivider()
                        Text(t("detect_chip_help"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
                        FilledTonalButton({ send(AppAction(AppActionKind.DETECT)) }, enabled = !state.busy) {
                            Text(t("detect_chip"))
                        }
                        state.chipDetection?.let {
                            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                                Text(resolveCoreText(it, language), Modifier.padding(12.dp), style = typography.bodyMedium)
                            }
                        }
                    } else {
                        Text(t("experimental_disabled"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.experimental && state.usbRecovery) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) { MflasherTools(state, language, send, confirm) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(recovery, enabled = !state.busy) { Text(t("recovery_title")) }
                TextButton(midiTools) { Text(t("device_midi_tools")) }
            }
        }
    }
}

@Composable internal fun MidiDeviceToolsScreen(
    state: AppSnapshot, language: String, send: (AppAction) -> Unit, back: () -> Unit, logs: () -> Unit, romTools: () -> Unit,
) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    var selected by rememberSaveable { mutableStateOf("") }
    val target = state.target
    val identity = state.identity
    val port = state.ports.firstOrNull { it.id == selected } ?: target ?: state.ports.singleOrNull()
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(back) { Text(t("back")) }
            Text(t("device_midi_tools"), Modifier.semantics { heading() }, style = typography.headlineMedium)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("mode_midi"), Modifier.semantics { heading() }, style = typography.titleLarge)
                    if (port == null && state.ports.isEmpty()) {
                        Text(t("mode_no_ports"), style = typography.bodyMedium, color = colors.onSurfaceVariant)
                    } else {
                        Choice(t("select_device_accessibility"), port?.label ?: t("select_port"),
                            state.ports.map { it.id to it.label }, { selected = it },
                            enabled = !state.busy && state.ports.isNotEmpty())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ port?.let { send(AppAction(AppActionKind.CONNECT, it.id)) } },
                            enabled = !state.busy && port != null) { Text(t("fleet_identify")) }
                        TextButton({ send(AppAction(AppActionKind.PORTS_REFRESH)) }, enabled = !state.busy) {
                            Text(t("refresh_ports"))
                        }
                    }
                    if (target != null) {
                        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                            color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t("mode_current", target.label), style = typography.bodyMedium)
                                identity?.let { Text("${it.model} · ${it.version}", style = typography.titleMedium) }
                            }
                        }
                    }
                    if (identity != null) {
                        Choice(t("flash_chip_label"), t(state.chip.labelKey), FlashChip.entries.map { it.name to t(it.labelKey) },
                            { send(AppAction(AppActionKind.CHIP, it)) }, enabled = !state.busy)
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) { MidiBackupTools(state, language, send) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(logs) { Text(t("connection_log")) }
                if (state.experimental) TextButton(romTools) { Text(t("device_tools")) }
            }
        }
    }
}
