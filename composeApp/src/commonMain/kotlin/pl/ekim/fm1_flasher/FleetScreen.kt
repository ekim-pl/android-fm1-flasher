package pl.ekim.fm1_flasher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable private fun DeviceDot(index: Int) {
    Box(Modifier.size(12.dp).background(Color(DeviceColor.fromIndex(index).argb), CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DeviceColorPicker(device: FleetDevice, language: String, enabled: Boolean, send: (AppAction) -> Unit) {
    Text(resolveCoreText(msg("device_color"), language), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeviceColor.entries.forEach { color ->
            val selected = device.color == color.ordinal
            FilterChip(selected = selected, onClick = { send(AppAction(AppActionKind.DEVICE_COLOR, device.id, color.ordinal.toString())) },
                enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
                label = { Text((if (selected) "✓ " else "") + resolveCoreText(msg(color.labelKey), language)) },
                leadingIcon = { DeviceDot(color.ordinal) })
        }
    }
}

@Composable internal fun FleetScreen(state: AppSnapshot, language: String, send: (AppAction) -> Unit, library: () -> Unit, tasks: () -> Unit, tools: () -> Unit, midiTools: () -> Unit) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    val online = state.fleet.filter { it.endpoint != null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(t("fleet_workspace"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(t("fleet_devices"), style = MaterialTheme.typography.headlineLarge)
            Text(t("fleet_connected_count", online.size), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ send(AppAction(AppActionKind.PORTS_REFRESH)) }, enabled = !state.busy) { Text(t("refresh_ports")) }
                TextButton(tasks) { Text(t("fleet_plan_count", state.jobs.count { it.outcome == JobOutcome.QUEUED })) }
            }
        }
        if (state.usbRecovery) item {
            FilledTonalButton(midiTools, modifier = Modifier.fillMaxWidth()) { Text(t("device_midi_tools")) }
        }
        if (state.usbRecovery && state.experimental) item {
            FilledTonalButton(tools, modifier = Modifier.fillMaxWidth()) { Text(t("device_tools")) }
        }
        if (online.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("fleet_connect_title"), style = MaterialTheme.typography.titleLarge)
                Text(t("fleet_connect_help"))
                FilledTonalButton(library) { Text(t("fleet_open_library")) }
            } }
        }
        items(online, key = { it.id }) { device ->
            var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
            var alias by rememberSaveable(device.id, device.alias) { mutableStateOf(device.alias) }
            var selected by rememberSaveable(device.id) { mutableStateOf(state.ready?.key.orEmpty()) }
            val entry = state.downloads.singleOrNull { it.entry.key == selected }?.entry
            var forceDialog by remember(device, entry) { mutableStateOf(false) }
            val pinned = state.jobs.any { it.deviceId == device.id && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }
            val endpoint = device.endpoint!!
            val portId = "${endpoint.id}:${endpoint.usbPath}:${endpoint.generation}"
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceDot(device.color)
                    Column(Modifier.weight(1f)) {
                        Text(device.alias, style = MaterialTheme.typography.titleLarge)
                        Text(device.identity?.let { "${it.model} · ${it.version}" } ?: t("fleet_unidentified"), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton({ expanded = !expanded }) { Text(t("fleet_details")) }
                }
                if (expanded) {
                    DeviceColorPicker(device, language, !state.busy && !pinned, send)
                    OutlinedTextField(alias, { alias = it.take(48) }, label = { Text(t("fleet_alias")) }, singleLine = true, enabled = !state.busy && !pinned, modifier = Modifier.fillMaxWidth())
                    TextButton({ send(AppAction(AppActionKind.DEVICE_ALIAS, device.id, alias)) }, enabled = !state.busy && !pinned && alias.isNotBlank() && alias != device.alias) { Text(t("fleet_save_alias")) }
                    Text(t("fleet_attachment", endpoint.usbPath), style = MaterialTheme.typography.bodySmall)
                    Text(t(if (device.serial.isNullOrBlank()) "fleet_no_serial" else "fleet_serial_observed"), style = MaterialTheme.typography.bodySmall)
                }
                if (state.usbRecovery && state.experimental) OutlinedButton({
                    send(AppAction(AppActionKind.CONNECT, portId)); tools()
                }, enabled = !state.busy) { Text(t("tools_open")) }
                if (device.identity == null) {
                    Button({ send(AppAction(AppActionKind.CONNECT, portId)) }, enabled = !state.busy) { Text(t("fleet_identify")) }
                } else {
                    Choice(t("flash_chip_label"), t(device.chip.labelKey), FlashChip.entries.map { it.name to t(it.labelKey) },
                        { send(AppAction(AppActionKind.DEVICE_CHIP, device.id, it)) }, enabled = !state.busy && !pinned)
                    if (expanded) Text(t("fleet_chip_evidence"), style = MaterialTheme.typography.bodySmall)
                    Choice(t("fleet_package"), entry?.let { "${it.name} · ${it.version}" } ?: t("no_package_selected"), state.downloads.map { it.entry.key to "${it.entry.name} · ${it.entry.version}" }, { selected = it }, enabled = !state.busy && !pinned)
                    if (entry != null && !device.chip.allows(entry)) Text(t("chip_not_supported"), color = MaterialTheme.colorScheme.error)
                    if (entry != null && device.chip.canForce(entry)) {
                        OutlinedButton({ forceDialog = true }, enabled = !state.busy && !pinned) { Text(t("force_unknown")) }
                        if (forceDialog) ForceUnknownDialog(language, "${device.alias} · ${entry.name} · ${entry.version}\nSHA-256 ${entry.sha256}", !state.busy && !pinned,
                            { forceDialog = false }) {
                            forceDialog = false
                            send(AppAction(AppActionKind.QUEUE_ADD, device.id, entry.key, forceUnknown = true, confirmedHash = entry.sha256))
                        }
                    }
                    if (state.downloads.isEmpty()) TextButton(library) { Text(t("fleet_open_library")) }
                    FilledTonalButton({ send(AppAction(AppActionKind.QUEUE_ADD, device.id, selected)) }, enabled = !state.busy && !pinned && entry != null && device.chip.allows(entry)) {
                        Text(t(if (pinned) "fleet_in_plan" else "fleet_add"))
                    }
                }
            } }
        }
        val offline = state.fleet.filter { it.endpoint == null }
        if (offline.isNotEmpty()) item { Text(t("fleet_saved_devices"), style = MaterialTheme.typography.titleMedium); Text(t("fleet_pair_help"), style = MaterialTheme.typography.bodySmall) }
        items(offline, key = { it.id }) { old ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceDot(old.color)
                    Text(old.alias, style = MaterialTheme.typography.titleMedium)
                }
                var expanded by rememberSaveable(old.id) { mutableStateOf(false) }
                TextButton({ expanded = !expanded }) { Text(t("fleet_details")) }
                if (expanded) DeviceColorPicker(old, language, !state.busy && state.jobs.none {
                    it.deviceId == old.id && it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING)
                }, send)
                Text(t("fleet_disconnected"), style = MaterialTheme.typography.bodySmall)
                val available = online.filter { it.identity != null && state.jobs.none { j -> j.deviceId in listOf(it.id, old.id) && j.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) } }
                if (available.isNotEmpty()) Choice(t("fleet_pair"), t("fleet_choose_physical"), available.map { it.id to it.alias }, { send(AppAction(AppActionKind.DEVICE_PAIR, old.id, it)) }, enabled = !state.busy)
            } }
        }
    }
}

@Composable internal fun TaskScreen(state: AppSnapshot, language: String, send: (AppAction) -> Unit, devices: () -> Unit) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    val queued = state.jobs.filter { it.outcome == JobOutcome.QUEUED }
    val ready = queued.isNotEmpty() && queued.all { job -> FleetPolicy.ready(job, state.fleet.singleOrNull { it.id == job.deviceId }) && state.downloads.any { it.entry.key == job.packageKey && it.entry.sha256 == job.hash } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(t("fleet_workspace"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(t("fleet_tasks"), style = MaterialTheme.typography.headlineLarge)
            Text(t("fleet_sequential"))
        }
        if (state.awaitingPort) item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("fleet_pair_return"), style = MaterialTheme.typography.titleMedium)
                    Text(t("fleet_return_help"))
                    state.candidates.singleOrNull()?.let { candidate ->
                        Text(candidate.label, style = MaterialTheme.typography.bodySmall)
                        Button({ send(AppAction(AppActionKind.RECONNECT, candidate.id)) }) { Text(t("fleet_confirm_return")) }
                    } ?: Text(t("fleet_waiting"))
                }
            }
        }
        if (state.jobs.isEmpty()) item { Text(t("fleet_empty_tasks")); FilledTonalButton(devices) { Text(t("fleet_devices")) } }
        items(state.jobs.filter { it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }, key = { it.id }) { job -> JobCard(job, language, !state.queueRunning) { send(AppAction(AppActionKind.QUEUE_REMOVE, job.id)) } }
        if (queued.isNotEmpty() || state.queueRunning) item {
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("fleet_review"), style = MaterialTheme.typography.titleLarge)
                Text(t("fleet_review_help"), style = MaterialTheme.typography.bodyMedium)
                if (!ready && !state.queueRunning) Text(t("fleet_plan_changed"), color = MaterialTheme.colorScheme.error)
                if (state.queueRunning) {
                    OutlinedButton({ send(AppAction(AppActionKind.QUEUE_STOP)) }, enabled = !state.queueStopping) { Text(t(if (state.queueStopping) "fleet_stopping" else "fleet_stop")) }
                    Text(t("fleet_stop_help"), style = MaterialTheme.typography.bodySmall)
                } else Button({ send(AppAction(AppActionKind.QUEUE_START, FleetPolicy.token(state.jobs))) }, enabled = ready && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(t("fleet_install_count", queued.size)) }
            } }
        }
        val history = state.jobs.filter { it.outcome !in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }.asReversed()
        if (history.isNotEmpty()) item { Text(t("fleet_history"), style = MaterialTheme.typography.titleLarge) }
        items(history, key = { it.id }) { job -> JobCard(job, language, false) {} }
    }
}

@Composable private fun JobCard(job: FlashJob, language: String, editable: Boolean, remove: () -> Unit) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    var details by rememberSaveable(job.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceDot(job.color)
            Text(job.alias, style = MaterialTheme.typography.titleMedium)
        }
        Text("${job.title} · ${job.version}")
        if (job.forceUnknown) Text(t("force_unknown"), color = MaterialTheme.colorScheme.error)
        Text("SHA-256 ${job.hash.take(16)}…", style = MaterialTheme.typography.labelSmall)
        val label = when (job.outcome) {
            JobOutcome.QUEUED -> "fleet_queued"
            JobOutcome.CONFIRMED -> "fleet_confirmed"
            JobOutcome.UNCONFIRMED -> "fleet_unconfirmed"
            JobOutcome.CANCELLED -> "fleet_cancelled"
            JobOutcome.RUNNING -> when(job.phase) { "flash_stage_verify" -> "fleet_verifying"; "flash_stage_write", "flash_progress" -> "fleet_writing"; else -> "fleet_working" }
        }
        Text(t(label), color = if (job.outcome == JobOutcome.UNCONFIRMED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (job.outcome == JobOutcome.UNCONFIRMED && job.phase != "fleet_unconfirmed") Text(t(job.phase), style = MaterialTheme.typography.bodySmall)
        TextButton({ details = !details }) { Text(t("fleet_details")) }
        if (details) {
            Text("SHA-256 ${job.hash}", style = MaterialTheme.typography.labelSmall)
            Text(t(job.chip.labelKey), style = MaterialTheme.typography.bodySmall)
            job.events.forEach { event ->
                val phase = when (event.phase) { "flash_progress", "flash_stage_write" -> "fleet_writing"; "flash_completed" -> "fleet_confirmed"; else -> event.phase }
                Text("${formatCatalogTime(event.time)} · ${t(phase)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (job.outcome == JobOutcome.RUNNING) {
            if (job.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(DeviceColor.fromIndex(job.color).argb)) else LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(DeviceColor.fromIndex(job.color).argb))
        }
        if (editable && job.outcome == JobOutcome.QUEUED) TextButton(remove) { Text(t("fleet_remove_job")) }
    } }
}

@Composable internal fun ForceUnknownDialog(language: String, target: String, enabled: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    fun t(key: String) = resolveCoreText(msg(key), language)
    var acknowledged by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(t("force_unknown")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(target)
            Text(t("force_unknown_warning"), color = MaterialTheme.colorScheme.error)
            Row {
                Checkbox(acknowledged, { acknowledged = it }, enabled = enabled, modifier = Modifier.semantics { contentDescription = t("force_unknown_ack") })
                Text(t("force_unknown_ack"), modifier = Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(confirm, enabled = enabled && acknowledged) { Text(t("force_unknown")) } },
        dismissButton = { TextButton(dismiss) { Text(t("back")) } })
}
