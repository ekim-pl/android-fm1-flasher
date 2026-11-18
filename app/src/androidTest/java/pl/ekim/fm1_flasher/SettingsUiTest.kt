package pl.ekim.fm1_flasher

import android.content.Intent
import android.test.InstrumentationTestCase
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("DEPRECATION")
class SettingsUiTest : InstrumentationTestCase() {
    // Report failed UI assertions on the test thread instead of crashing the app process.
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    fun testFullScreenSettingsSourceEditorAndBackInAllLanguages() {
        val actions = mutableListOf<AppAction>()
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false, fleetAvailable = true,
                appBuild = AppBuildInfo("0.99.0-nightly.9", "nightly", "2026-11-18"),
                catalog = Catalog(emptyList(), "", 0, false, sources = listOf(CatalogSource.hub))))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = if (name.startsWith("about/")) "# App\nIntro\n\n## Licenses\n[MIT](../LICENSE.md)" else "MIT license body"
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        onMain { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun click(label: String) {
            repeat(15) {
                instrumentation.waitForIdleSync(); Thread.sleep(60)
                var clicked = false
                onMain {
                    val n = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    if (n != null) { clicked = n.config[SemanticsActions.OnClick].action!!.invoke() }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 450f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        fun assertPage() {
            instrumentation.waitForIdleSync(); Thread.sleep(120)
            onMain { assertFalse(nodes().any { it.config.contains(SemanticsProperties.IsDialog) }) }
        }
        fun back() { onMain { activity.onBackPressedDispatcher.onBackPressed() }; instrumentation.waitForIdleSync(); Thread.sleep(100) }
        try {
            onMain { activity.setContent { FlasherApp(backend) } }
            assertPage()
            onMain {
                val tabs = nodes().filter { it.config.contains(SemanticsProperties.Selected) }
                val first = tabs.first { it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                    text.text in setOf("Library", "Devices", "Tasks")
                } == true }
                assertEquals(true, first.config.getOrNull(SemanticsProperties.Selected))
                assertTrue(first.config[SemanticsProperties.Text].any { it.text == "Library" })
            }
            for (language in listOf("en", "pl", "zh")) {
                onMain { backend.state.value = backend.state.value.copy(language = language) }
                val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(if (language == "zh") "zh-Hans" else language)) }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                fun t(key: String) = context.getString(context.resources.getIdentifier(key, "string", context.packageName))
                click(t("settings")); assertPage()
                click(t("history_unlimited")); assertEquals(AppAction(AppActionKind.HISTORY_RETENTION, "unlimited"), actions.last())
                click(context.getString(context.resources.getIdentifier("retention_days", "string", context.packageName), 7))
                assertEquals(AppAction(AppActionKind.HISTORY_RETENTION, "7"), actions.last())
                val beforeClear = actions.size
                click(t("history_clear"))
                assertEquals(beforeClear, actions.size)
                click(t("cancel")); assertEquals(beforeClear, actions.size)
                click(t("history_clear"))
                onMain { backend.state.value = backend.state.value.copy(busy = true) }
                var clearDisabled = false
                for (attempt in 0 until 50) {
                    instrumentation.waitForIdleSync(); Thread.sleep(60)
                    onMain {
                        val clear = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == t("history_clear") } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
                        clearDisabled = clear?.config?.contains(SemanticsProperties.Disabled) == true
                    }
                    if (clearDisabled) break
                }
                assertTrue("History confirmation must become disabled while busy", clearDisabled)
                onMain { backend.state.value = backend.state.value.copy(busy = false) }
                click(t("history_clear")); assertEquals(AppAction(AppActionKind.HISTORY_CLEAR), actions.last())
                // Return to the top of the settings scroll before testing earlier controls.
                onMain { nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, -10000f) }
                click(t("theme_dark")); assertEquals(AppAction(AppActionKind.APPEARANCE, "DARK"), actions.last()); assertPage()
                click(t("catalog_sources")); assertPage()
                click(t("source_add")); assertPage()
                onMain {
                    val field = nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
                    assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Draft source")))
                }
                click(t("provider_package_policies")); assertPage()
                back(); assertPage()
                back(); assertPage()
                back(); assertPage()
                back()
                onMain { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == t("fleet_devices") } == true }) }
                click(t("settings")); click(t("tab_about"))
                assertPage()
                onMain {
                    val visibleText = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    assertTrue(context.getString(R.string.about_version, "0.99.0-nightly.9") in visibleText)
                    assertTrue(context.getString(R.string.about_build_kind, t("about_build_nightly")) in visibleText)
                    assertTrue(context.getString(R.string.about_build_date, "2026-11-18") in visibleText)
                    assertFalse(t("app_name") in visibleText)
                }
                var openedLicense = false
                for (attempt in 0 until 50) {
                    instrumentation.waitForIdleSync(); Thread.sleep(60)
                    onMain {
                        val all = roots.filter { it.view.isAttachedToWindow }
                            .flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false) }
                        openedLicense = all.any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "MIT license body" } == true }
                        if (!openedLicense) {
                            val link = all.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "MIT" } == true }
                            fun activate(node: androidx.compose.ui.semantics.SemanticsNode): Boolean =
                                node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true || node.children.any(::activate)
                            if (link == null || !activate(link)) {
                                nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                                    ?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 450f)
                            }
                        }
                    }
                    if (openedLicense) break
                }
                assertTrue("About license link must open the license document", openedLicense)
                assertPage()
                onMain {
                    val texts = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    assertTrue(texts.contains(t("license_title")))
                    assertTrue(texts.contains("MIT license body"))
                    assertFalse(texts.contains("LICENSE.md"))
                }
                back(); assertPage()
                click(t("fleet_devices"))
            }
        } finally {
            onMain { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }
    fun testSourceDraftAndFullScreenRouteSurviveActivityRecreation() {
        val context = instrumentation.targetContext
        val database = CatalogDatabase.get(context)
        val offline = database.setting("offline")
        database.setting("offline", "true")
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        onMain { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        var activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun text(key: String) = activity.getString(activity.resources.getIdentifier(key, "string", context.packageName))
        fun click(label: String) {
            repeat(60) {
                instrumentation.waitForIdleSync(); Thread.sleep(60)
                var clicked = false
                onMain {
                    val n = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null && !it.config.contains(SemanticsProperties.Disabled) }
                    if (n != null) { clicked = n.config[SemanticsActions.OnClick].action!!.invoke() }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 400f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        try {
            click(text("fleet_library")); click(text("settings")); click(text("catalog_sources")); click(text("source_add"))
            instrumentation.waitForIdleSync(); Thread.sleep(150)
            var wrote = false
            onMain { nodes().firstOrNull { it.config.getOrNull(SemanticsActions.SetText) != null }?.let { wrote = it.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Restored draft")) } }
            assertTrue(wrote)
            val monitor = instrumentation.addMonitor(ComposeActivity::class.java.name, null, false)
            onMain { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as ComposeActivity
            instrumentation.removeMonitor(monitor)
            var restored = false
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync(); Thread.sleep(80)
                onMain { restored = nodes().any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Restored draft" } }
                if (restored) break
            }
            assertTrue("Source draft was lost during recreation", restored)
            var dialog = false
            onMain { dialog = nodes().any { it.config.contains(SemanticsProperties.IsDialog) } }
            assertFalse(dialog)
            repeat(3) { onMain { activity.onBackPressedDispatcher.onBackPressed() }; instrumentation.waitForIdleSync(); Thread.sleep(120) }
            var librarySelected = false
            onMain { librarySelected = nodes().any { it.config.getOrNull(SemanticsProperties.Selected) == true && it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text("fleet_library") } == true } }
            assertTrue("Back must return to the original Library tab", librarySelected)
        } finally {
            onMain { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
            database.setting("offline", offline)
        }
    }

}
