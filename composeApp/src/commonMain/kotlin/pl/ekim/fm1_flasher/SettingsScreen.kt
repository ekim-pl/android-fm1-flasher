package pl.ekim.fm1_flasher

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable internal fun SettingsPage(
    title: String, backLabel: String, back: () -> Unit,
    scroll: ScrollState? = rememberScrollState(), busy: Boolean = false, status: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(back) { Text("← $backLabel") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        HorizontalDivider()
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status != null) Text(status, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Column(Modifier.weight(1f).fillMaxWidth().then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().then(if (scroll == null) Modifier.fillMaxHeight() else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable internal fun SettingsScreen(
    state: AppSnapshot, language: String, scroll: ScrollState, send: (AppAction) -> Unit,
    back: () -> Unit, sources: () -> Unit, privacy: () -> Unit,
    confirm: (AppAction, AppText) -> Unit, navigate: (LibraryTab) -> Unit,
) {
    fun t(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    val pinned = state.jobs.any { it.outcome in listOf(JobOutcome.QUEUED, JobOutcome.RUNNING) }
    SettingsPage(t("settings"), t("back"), back, scroll, state.busy, resolveCoreText(state.status, language)) {
        SettingsCard(t("settings_interface")) {
            SettingOptions(t("language_title"), state.language.substringBefore('-'), listOf("pl" to "Polski", "en" to "English", "zh" to "简体中文")) {
                send(AppAction(AppActionKind.LANGUAGE, it))
            }
            HorizontalDivider()
            SettingOptions(t("appearance_title"), state.appearance, listOf("SYSTEM" to t("system_default"), "LIGHT" to t("theme_light"), "DARK" to t("theme_dark"))) {
                send(AppAction(AppActionKind.APPEARANCE, it))
            }
        }
        SettingsCard(t("settings_library")) {
            SettingSwitch(t("offline_mode"), state.offline, !state.busy) { send(AppAction(AppActionKind.OFFLINE, it.toString())) }
            if (state.catalog?.sources?.isNotEmpty() == true) OutlinedButton(sources, Modifier.fillMaxWidth()) { Text(t("catalog_sources")) }
            OutlinedButton({ confirm(AppAction(AppActionKind.CLEAR_DOWNLOADS), msg("downloads_clear_confirm", state.downloads.size)) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !pinned) { Text(t("downloads_clear")) }
            if (pinned) Text(t("fleet_package_pinned"), style = MaterialTheme.typography.bodySmall)
        }
        SettingsCard(t("experimental_features")) {
            SettingSwitch(t("experimental_features"), state.experimental, !state.busy) { send(AppAction(AppActionKind.EXPERIMENTAL, it.toString())) }
            Text(t("experimental_help"), style = MaterialTheme.typography.bodySmall)
            if (state.experimental) {
                if (state.fleetAvailable) {
                    OutlinedButton({ navigate(LibraryTab.FLASHER) }, Modifier.fillMaxWidth()) { Text(t("device_tools")) }
                    OutlinedButton({ navigate(LibraryTab.LOGGER) }, Modifier.fillMaxWidth()) { Text(t("tab_logger")) }
                }
                HorizontalDivider()
                Text(t("operation_log_title"), style = MaterialTheme.typography.titleMedium)
                Text(t("operation_log_help"), style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ send(AppAction(AppActionKind.LOG_EXPORT)) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(t("operation_log_export")) }
                OutlinedButton({ confirm(AppAction(AppActionKind.LOG_CLEAR), msg("operation_log_clear_confirm")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(t("operation_log_clear")) }
            }
        }
        SettingsCard(t("settings_app")) {
            if (state.fleetAvailable) OutlinedButton({ navigate(LibraryTab.ABOUT) }, Modifier.fillMaxWidth()) { Text(t("tab_about")) }
            OutlinedButton(privacy, Modifier.fillMaxWidth()) { Text(t("privacy_title")) }
            Text(t("about_version", "0.9.3"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable internal fun SettingOptions(title: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean = true, change: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    choices.forEach { (id, label) ->
        Row(Modifier.fillMaxWidth().selectable(selected == id, enabled = enabled, onClick = { change(id) }, role = Role.RadioButton).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == id, onClick = null, enabled = enabled)
            Text(label, Modifier.padding(start = 12.dp))
        }
    }
}

@Composable internal fun SettingSwitch(title: String, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(selected, onCheckedChange = null, enabled = enabled)
    }
}
