package pl.ekim.fm1_flasher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
internal fun SourceSettings(state: AppSnapshot, language: String, backend: AppBackend, close: () -> Unit) {
    fun text(key: String, vararg args: Any) = resolveCoreText(msg(key, *args), language)
    fun send(kind: AppActionKind, id: String = "", value: String = "") = backend.perform(AppAction(kind, id, value))
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CatalogSource?>(null) }
    val listScroll = rememberScrollState()
    val editor = state.catalog?.sources?.firstOrNull { it.id == editorId }
    if (adding || editor != null) {
        key(editor?.id ?: "new") {
            SourceEditor(state, language, backend, editor, { id -> adding = false; editorId = id }) { adding = false; editorId = null }
        }
    } else SettingsPage(text("catalog_sources"), text("back"), close, listScroll, state.busy, resolveCoreText(state.status, language)) {
        Text(text("sources_help"))
        if (state.backgroundAvailable) SettingsCard(text("sources_background")) {
            SettingSwitch(text("sources_background"), state.backgroundSync, !state.busy) { send(AppActionKind.BACKGROUND_SYNC, it.toString()) }
            Text(text("sources_background_help"), style = MaterialTheme.typography.bodySmall)
        }
        state.catalog?.sources.orEmpty().filter { !it.deleted }.forEach { source -> key(source.id) {
            SettingsCard(source.name) {
                SettingSwitch(source.name, source.enabled, !state.busy) { send(AppActionKind.SOURCE_ENABLE, source.id, it.toString()) }
                Text("${source.provider.id} · ${text(source.authMethod.labelKey)}", style = MaterialTheme.typography.labelMedium)
                Text(source.url, style = MaterialTheme.typography.bodySmall)
                if (source.authMethod == HttpAuthMethod.OAUTH2) OutlinedButton({ send(AppActionKind.AUTH_LOGIN, source.id) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.offline) { Text(text("oauth_connect")) }
                Text(text("source_summary", source.entryCount, if (source.checkedAt == 0L) text("source_never") else formatCatalogTime(source.checkedAt)))
                Text(text("source_next", if (source.nextAttemptAt == 0L) text("source_never") else formatCatalogTime(source.nextAttemptAt)), style = MaterialTheme.typography.bodySmall)
                val hosts = state.catalog?.knownEntries.orEmpty().filter { it.sourceId == source.id }.flatMap { listOfNotNull(it.directUrl, it.cardUrl) }.map { it.substringAfter("://").substringBefore('/') }.distinct().sorted()
                Text(text("source_domains", hosts.joinToString(", ").ifEmpty { "—" }), style = MaterialTheme.typography.bodySmall)
                source.error?.let { Text(text("source_error_retained", source.name, it), color = MaterialTheme.colorScheme.error) }
                OutlinedButton({ send(AppActionKind.SOURCE_CHECK, source.id) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.offline) { Text(text("source_check")) }
                OutlinedButton({ editorId = source.id }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(text("source_edit")) }
                if (!source.builtin) TextButton({ removing = source }, enabled = !state.busy) { Text(text("source_remove")) }
            }
        } }
        Button({ adding = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(text("source_add")) }
        TextButton({ send(AppActionKind.SOURCE_RESTORE) }, enabled = !state.busy) { Text(text("sources_restore")) }
        Text(text("source_unsigned"), style = MaterialTheme.typography.bodySmall)
    }
    removing?.let { source -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(text("source_remove")) }, text = { Text("${source.name}\n\n${text("source_remove_help")}") },
        confirmButton = { TextButton({ send(AppActionKind.SOURCE_REMOVE, source.id); removing = null }, enabled = !state.busy) { Text(text("source_remove")) } },
        dismissButton = { TextButton({ removing = null }) { Text(text("cancel")) } }) }
}

@Composable private fun SourceEditor(state: AppSnapshot, language: String, backend: AppBackend, source: CatalogSource?, onSaved: (String) -> Unit, back: () -> Unit) {
    fun text(key: String) = resolveCoreText(msg(key), language)
    var name by rememberSaveable { mutableStateOf(source?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(source?.url.orEmpty()) }
    var hours by rememberSaveable { mutableStateOf((source?.intervalHours ?: 24).toString()) }
    var packages by rememberSaveable { mutableStateOf(source?.packageScopes?.joinToString("\n").orEmpty()) }
    var artwork by rememberSaveable { mutableStateOf(source?.artworkScopes?.joinToString("\n").orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var showPackagePolicies by rememberSaveable { mutableStateOf(false) }
    var chipOverrides by rememberSaveable(stateSaver = mapSaver<Map<String, String>>(
        save = { it }, restore = { it.mapValues { entry -> entry.value as String } }
    )) { mutableStateOf(source?.packageChips.orEmpty()) }
    var providerName by rememberSaveable { mutableStateOf((source?.provider ?: CatalogProviderType.REPO_URL).name) }
    val provider = CatalogProviderType.valueOf(providerName)
    var authName by rememberSaveable { mutableStateOf((source?.authMethod ?: HttpAuthMethod.NONE).name) }
    val auth = HttpAuthMethod.valueOf(authName)
    var username by rememberSaveable { mutableStateOf(source?.authUsername.orEmpty()) }
    var header by rememberSaveable { mutableStateOf(source?.authHeader ?: "X-API-Key") }
    var secret by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var grantName by rememberSaveable { mutableStateOf((source?.oauth?.grant ?: OAuthGrant.PKCE).name) }
    var clientId by rememberSaveable { mutableStateOf(source?.oauth?.clientId.orEmpty()) }
    var authorizationUrl by rememberSaveable { mutableStateOf(source?.oauth?.authorizationUrl.orEmpty()) }
    var tokenUrl by rememberSaveable { mutableStateOf(source?.oauth?.tokenUrl.orEmpty()) }
    var scope by rememberSaveable { mutableStateOf(source?.oauth?.scope.orEmpty()) }
    var clientAuthName by rememberSaveable { mutableStateOf((source?.oauth?.clientAuth ?: OAuthClientAuth.BASIC).name) }
    LaunchedEffect(state.savedSourceId, state.sourceError, state.busy) {
        if (submitted && !state.busy && state.savedSourceId != null && state.sourceError == null) { secret = ""; clientSecret = ""; onSaved(state.savedSourceId!!) }
    }
    fun scopes(value: String) = value.lines().map(String::trim).filter(String::isNotEmpty).toSet()
    if (showPackagePolicies) {
        val entries = state.catalog?.knownEntries.orEmpty().filter { it.sourceId == source?.id }
        SettingsPage(text("provider_package_policies"), text("back"), { showPackagePolicies = false }, scroll = null) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(text("provider_package_policies_help")) }
                if (entries.isEmpty()) item { Text(text("provider_package_policies_empty")) }
                items(entries, key = { it.id }) { entry ->
                    SettingsCard("${entry.name} · ${entry.version}") {
                        Text("SHA-256 ${entry.sha256.take(16)}…", style = MaterialTheme.typography.bodySmall)
                        SettingOptions(text("provider_chip_support"), chipOverrides[entry.sha256] ?: "unknown",
                            listOf("unknown" to text("chip_unconfirmed"), "old" to text("chip_older_only"), "new" to text("chip_both"))) {
                            submitted = false; chipOverrides = chipOverrides + (entry.sha256 to it)
                        }
                    }
                }
            }
        }
        return
    }
    SettingsPage(text(if (source == null) "source_add" else "source_edit"), text("back"), back, busy = state.busy, status = resolveCoreText(state.status, language)) {
        Text(text("source_review"))
        SettingsCard(text("catalog_sources")) {
            if (source?.builtin == true) Text("${text("provider_type")}: ${source.provider.id}")
            else SettingOptions(text("provider_type"), providerName, listOf(CatalogProviderType.REPO_URL, CatalogProviderType.PKG_URL, CatalogProviderType.JSON_URL).map { it.name to text("provider_${it.name.lowercase()}") }, enabled = !state.busy) { submitted = false; providerName = it }
            OutlinedTextField(name, { submitted = false; name = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("source_name")) }, enabled = !state.busy && source?.builtin != true, singleLine = true)
            OutlinedTextField(url, { submitted = false; url = it.take(4096) }, modifier = Modifier.fillMaxWidth(), label = { Text(text(when(provider) { CatalogProviderType.REPO_URL -> "source_repo_url"; CatalogProviderType.PKG_URL -> "source_pkg_url"; else -> "source_url" })) }, enabled = !state.busy && source?.builtin != true, singleLine = true)
            OutlinedTextField(hours, { submitted = false; hours = it.take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("source_hours")) }, enabled = !state.busy, singleLine = true)
            if (provider != CatalogProviderType.REPO_URL) {
                Text(text("source_scopes_help"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(packages, { submitted = false; packages = it.take(32768) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("source_package_scopes")) }, enabled = !state.busy)
                if (provider != CatalogProviderType.PKG_URL) OutlinedTextField(artwork, { submitted = false; artwork = it.take(32768) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("source_artwork_scopes")) }, enabled = !state.busy)
            }
            if (provider in listOf(CatalogProviderType.REPO_URL, CatalogProviderType.PKG_URL)) {
                OutlinedButton({ showPackagePolicies = true }, modifier = Modifier.fillMaxWidth()) { Text(text("provider_package_policies")) }
                Text(text("provider_package_policies_empty"), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            SettingOptions(text("auth_title"), authName, HttpAuthMethod.entries.map { it.name to text(it.labelKey) }, enabled = !state.busy) { submitted = false; authName = it; secret = ""; clientSecret = "" }
            if (auth == HttpAuthMethod.BASIC) OutlinedTextField(username, { submitted = false; username = it.take(256) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("auth_username")) }, singleLine = true, enabled = !state.busy)
            if (auth == HttpAuthMethod.API_KEY) OutlinedTextField(header, { submitted = false; header = it.take(64) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("auth_header")) }, singleLine = true, enabled = !state.busy)
            if (auth in listOf(HttpAuthMethod.BASIC, HttpAuthMethod.BEARER, HttpAuthMethod.API_KEY)) OutlinedTextField(secret, { submitted = false; secret = it.take(8192) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("auth_secret")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !state.busy)
            if (auth == HttpAuthMethod.OAUTH2) {
                SettingOptions(text("oauth_grant"), grantName, listOf("PKCE" to text("oauth_pkce"), "CLIENT_CREDENTIALS" to text("oauth_client_credentials")), enabled = !state.busy) { submitted = false; grantName = it }
                OutlinedTextField(clientId, { submitted = false; clientId = it.take(1024) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("oauth_client_id")) }, singleLine = true, enabled = !state.busy)
                if (grantName == "PKCE") OutlinedTextField(authorizationUrl, { submitted = false; authorizationUrl = it.take(4096) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("oauth_authorization_url")) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(tokenUrl, { submitted = false; tokenUrl = it.take(4096) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("oauth_token_url")) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(scope, { submitted = false; scope = it.take(2048) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("oauth_scope")) }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(clientSecret, { submitted = false; clientSecret = it.take(8192) }, modifier = Modifier.fillMaxWidth(), label = { Text(text("oauth_client_secret")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !state.busy)
                SettingOptions(text("oauth_client_auth"), clientAuthName, listOf("BASIC" to text("oauth_client_basic"), "POST" to text("oauth_client_post")), enabled = !state.busy) { submitted = false; clientAuthName = it }
                Text(text("oauth_redirect_help"), style = MaterialTheme.typography.bodySmall)
            }
            if (auth != HttpAuthMethod.NONE) Text(text("auth_secret_help"), style = MaterialTheme.typography.bodySmall)
            if (submitted) {
                state.sourceError?.let { Text(resolveCoreText(it, language), color = MaterialTheme.colorScheme.error) }
                if (!state.busy && state.savedSourceId != null && state.sourceError == null) Text(text("source_saved"))
            }
            Button({
                submitted = true
                backend.perform(AppAction(AppActionKind.SOURCE_SAVE, source?.id.orEmpty(), source = SourceConfiguration(name, url, hours.toIntOrNull() ?: 0, scopes(packages), scopes(artwork), provider,
                    HttpCredentials(auth, if (auth == HttpAuthMethod.BASIC) username else "", if (auth != HttpAuthMethod.OAUTH2 && auth != HttpAuthMethod.NONE) secret else "", if (auth == HttpAuthMethod.API_KEY) header else "X-API-Key",
                        if (auth == HttpAuthMethod.OAUTH2) OAuthConfiguration(OAuthGrant.valueOf(grantName), clientId, authorizationUrl, tokenUrl, scope, OAuthClientAuth.valueOf(clientAuthName)) else OAuthConfiguration(),
                        if (auth == HttpAuthMethod.OAUTH2) clientSecret else ""), if (provider in listOf(CatalogProviderType.REPO_URL, CatalogProviderType.PKG_URL)) chipOverrides else emptyMap())))
            }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text(text("source_save")) }
        }
    }
}
