package pl.ekim.fm1_flasher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun DeviceToolsScreen(
    state: AppSnapshot, language: String, send: (AppAction) -> Unit,
    confirm: (AppAction, AppText) -> Unit, recovery: () -> Unit, logs: () -> Unit,
) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    var selected by rememberSaveable { mutableStateOf("") }
    val port = state.ports.firstOrNull { it.id == selected } ?: state.target ?: state.ports.singleOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(t("device_tools"), style = MaterialTheme.typography.headlineMedium)
            Text(t("device_tools_help"), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("mode_midi"), style = MaterialTheme.typography.titleLarge)
                    Choice(t("select_device_accessibility"), port?.label ?: t("select_port"), state.ports.map { it.id to it.label }, { selected = it }, enabled = !state.busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ port?.let { send(AppAction(AppActionKind.CONNECT, it.id)) } }, enabled = !state.busy && port != null) { Text(t("fleet_identify")) }
                        TextButton({ send(AppAction(AppActionKind.PORTS_REFRESH)) }, enabled = !state.busy) { Text(t("refresh_ports")) }
                    }
                    state.target?.let { target ->
                        Text(t("mode_current", target.label), style = MaterialTheme.typography.bodySmall)
                        state.identity?.let { Text("${it.model} · ${it.version}", style = MaterialTheme.typography.titleMedium) }
                    }
                    if (state.identity != null) Choice(t("flash_chip_label"), t(state.chip.labelKey), FlashChip.entries.map { it.name to t(it.labelKey) }, { send(AppAction(AppActionKind.CHIP, it)) }, enabled = !state.busy && state.identity != null)
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("mode_download"), style = MaterialTheme.typography.titleLarge)
                    Button({ confirm(AppAction(AppActionKind.RECOVERY_ENTER, state.target?.id.orEmpty()), msg("recovery_enter_confirm", state.target?.label.orEmpty())) }, enabled = !state.busy && state.experimental && state.identity?.isLoader == false && state.target?.id == port?.id) { Text(t("recovery_enter")) }
                    FilledTonalButton({ send(AppAction(AppActionKind.DETECT)) }, enabled = !state.busy && state.experimental) { Text(t("detect_chip")) }
                    Text(t("mode_download_help"), style = MaterialTheme.typography.bodyMedium)
                    Text(t("detect_chip_help"), style = MaterialTheme.typography.bodySmall)
                    state.chipDetection?.let { Text(resolveCoreText(it, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (state.experimental && state.usbRecovery) item { MflasherTools(state, language, send, confirm) }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(recovery, enabled = !state.busy) { Text(t("recovery_title")) }
                    TextButton(logs) { Text(t("connection_log")) }
                }
            }
        }
    }
}
