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
    fun testFullScreenSettingsSourceEditorAndBackInAllLanguages() {
        val actions = mutableListOf<AppAction>()
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false, fleetAvailable = true,
                catalog = Catalog(emptyList(), "", 0, false, sources = listOf(CatalogSource.hub))))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = if (name.startsWith("about/")) "# App\nIntro\n\n## Licenses\n[MIT](../LICENSE.md)" else "MIT license body"
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun click(label: String) {
            repeat(15) {
                instrumentation.waitForIdleSync(); Thread.sleep(60)
                var clicked = false
                instrumentation.runOnMainSync {
                    val n = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    if (n != null) { n.config[SemanticsActions.OnClick].action!!.invoke(); clicked = true }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 450f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        fun assertPage() {
            instrumentation.waitForIdleSync(); Thread.sleep(120)
            instrumentation.runOnMainSync { assertFalse(nodes().any { it.config.contains(SemanticsProperties.IsDialog) }) }
        }
        fun back() { instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }; instrumentation.waitForIdleSync(); Thread.sleep(100) }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            for (language in listOf("en", "pl", "zh")) {
                instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(language = language) }
                val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(if (language == "zh") "zh-Hans" else language)) }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                fun t(key: String) = context.getString(context.resources.getIdentifier(key, "string", context.packageName))
                click(t("settings")); assertPage()
                click(t("theme_dark")); assertEquals(AppAction(AppActionKind.APPEARANCE, "DARK"), actions.last()); assertPage()
                click(t("catalog_sources")); assertPage()
                click(t("source_add")); assertPage()
                instrumentation.runOnMainSync {
                    val field = nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
                    assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Draft source")))
                }
                click(t("provider_package_policies")); assertPage()
                back(); assertPage()
                back(); assertPage()
                back(); assertPage()
                back()
                instrumentation.runOnMainSync { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == t("fleet_devices") } == true }) }
                click(t("settings")); click(t("tab_about"))
                var openedLicense = false
                for (attempt in 0 until 15) {
                    instrumentation.waitForIdleSync(); Thread.sleep(60)
                    var location: androidx.compose.ui.geometry.Offset? = null
                    instrumentation.runOnMainSync {
                        val link = nodes().firstOrNull {
                            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "MIT" } == true
                        }
                        if (link != null && link.boundsInWindow.height > 0f) location = link.boundsInWindow.center
                        else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                            ?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 450f)
                    }
                    location?.let { point ->
                        val now = android.os.SystemClock.uptimeMillis()
                        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                            val event = android.view.MotionEvent.obtain(now, now + action * 50L, action, point.x, point.y, 0)
                            instrumentation.sendPointerSync(event); event.recycle()
                        }
                        openedLicense = true
                    }
                    if (openedLicense) break
                }
                assertTrue("Missing About license link", openedLicense)
                assertPage()
                instrumentation.runOnMainSync {
                    val texts = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    assertTrue(texts.contains(t("license_title")))
                    assertTrue(texts.contains("MIT license body"))
                    assertFalse(texts.contains("LICENSE.md"))
                }
                back(); assertPage()
                click(t("fleet_devices"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }
    fun testSourceDraftAndFullScreenRouteSurviveActivityRecreation() {
        val context = instrumentation.targetContext
        val database = CatalogDatabase.get(context)
        val offline = database.setting("offline")
        database.setting("offline", "true")
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        var activity = instrumentation.startActivitySync(Intent(context, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun text(key: String) = activity.getString(activity.resources.getIdentifier(key, "string", context.packageName))
        fun click(label: String) {
            repeat(60) {
                instrumentation.waitForIdleSync(); Thread.sleep(60)
                var clicked = false
                instrumentation.runOnMainSync {
                    val n = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null && !it.config.contains(SemanticsProperties.Disabled) }
                    if (n != null) { n.config[SemanticsActions.OnClick].action!!.invoke(); clicked = true }
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
            instrumentation.runOnMainSync { nodes().firstOrNull { it.config.getOrNull(SemanticsActions.SetText) != null }?.let { wrote = it.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Restored draft")) } }
            assertTrue(wrote)
            val monitor = instrumentation.addMonitor(ComposeActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as ComposeActivity
            instrumentation.removeMonitor(monitor)
            var restored = false
            for (attempt in 0 until 30) {
                instrumentation.waitForIdleSync(); Thread.sleep(80)
                instrumentation.runOnMainSync { restored = nodes().any { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Restored draft" } }
                if (restored) break
            }
            assertTrue("Source draft was lost during recreation", restored)
            var dialog = false
            instrumentation.runOnMainSync { dialog = nodes().any { it.config.contains(SemanticsProperties.IsDialog) } }
            assertFalse(dialog)
            repeat(3) { instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }; instrumentation.waitForIdleSync(); Thread.sleep(120) }
            var librarySelected = false
            instrumentation.runOnMainSync { librarySelected = nodes().any { it.config.getOrNull(SemanticsProperties.Selected) == true && it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text("fleet_library") } == true } }
            assertTrue("Back must return to the original Library tab", librarySelected)
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
            database.setting("offline", offline)
        }
    }

}
