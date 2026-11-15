package pl.ekim.fm1_flasher

import android.content.Intent
import android.test.InstrumentationTestCase
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("DEPRECATION")
class FleetUiTest : InstrumentationTestCase() {
    fun testOnePlanApprovalInAllLanguagesAndChangedAttachmentBlocksStart() {
        val entry = localEntry()
        val devices = (1..2).map { FleetDevice("device-$it", "Instrument $it", endpoint = Endpoint(it, "usb/$it", it.toLong()), identity = FirmwareIdentity("FM-1", "015"), chip = FlashChip.OLD) }
        val jobs = devices.map { FlashJob("job-${it.id}", it.id, it.alias, it.endpoint!!, entry.key, entry.sha256, entry.name, entry.version, it.identity!!, it.chip) }
        val actions = mutableListOf<AppAction>()
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false, fleetAvailable = true,
                fleet = devices, jobs = jobs, downloads = listOf(LocalPackage(entry, 0, 960))))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun find(label: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
        fun click(label: String) {
            repeat(12) {
                instrumentation.waitForIdleSync(); Thread.sleep(80)
                var clicked = false
                instrumentation.runOnMainSync {
                    val node = find(label)
                    if (node != null) { assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); clicked = true }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 500f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            click("Tasks")
            for (language in listOf("en", "pl", "zh")) {
                instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(language = language) }
                val label = mapOf("en" to "Install on 2 instruments", "pl" to "Zainstaluj na 2 instrumentach", "zh" to "在 2 台乐器上安装").getValue(language)
                click(label)
                assertEquals(AppAction(AppActionKind.QUEUE_START, FleetPolicy.token(jobs)), actions.last())
            }
            assertEquals(3, actions.count { it.kind == AppActionKind.QUEUE_START })
            instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(fleet = listOf(devices[0].copy(endpoint = null), devices[1])) }
            instrumentation.waitForIdleSync(); Thread.sleep(100)
            instrumentation.runOnMainSync {
                val label = "在 2 台乐器上安装"
                assertTrue(find(label)!!.config.contains(SemanticsProperties.Disabled))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }
    fun testModeToolsAreDirectlyAccessibleAndChipResultOpensDialog() {
        val actions = mutableListOf<AppAction>()
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false,
                fleetAvailable = true, experimental = true, usbRecovery = true))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun click(label: String) {
            repeat(15) {
                instrumentation.waitForIdleSync(); Thread.sleep(80)
                var clicked = false
                instrumentation.runOnMainSync {
                    val node = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    if (node != null) { assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); clicked = true }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 350f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            for (language in listOf("en", "pl", "zh")) {
                instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(language = language, chipDetection = null) }
                val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(if (language == "zh") "zh-Hans" else language))
                }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                fun t(key: String) = context.getString(context.resources.getIdentifier(key, "string", context.packageName))
                click(t("fleet_devices"))
                click(t("device_tools"))
                click(t("detect_chip"))
                assertEquals(AppActionKind.DETECT, actions.last().kind)
                instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(chipDetection = AppText.Plain("0xB36014")) }
                instrumentation.waitForIdleSync(); Thread.sleep(150)
                instrumentation.runOnMainSync {
                    assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == t("chip_result_title") } == true })
                }
                click(t("close"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }

    fun testLogAndDownloadCleanupConfirmationsInAllLanguages() {
        val actions = mutableListOf<AppAction>()
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false,
                fleetAvailable = true, experimental = true, usbRecovery = true, downloads = listOf(LocalPackage(localEntry(), 0, 960))))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun click(label: String) {
            repeat(15) {
                instrumentation.waitForIdleSync(); Thread.sleep(80)
                var clicked = false
                instrumentation.runOnMainSync {
                    val node = nodes().lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    if (node != null) { assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); clicked = true }
                    else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 350f)
                }
                if (clicked) return
            }
            error("Missing $label")
        }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            for (language in listOf("en", "pl", "zh")) {
                instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(language = language, chipDetection = null) }
                val config = android.content.res.Configuration(instrumentation.targetContext.resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(if (language == "zh") "zh-Hans" else language))
                }
                val context = instrumentation.targetContext.createConfigurationContext(config)
                fun t(key: String) = context.getString(context.resources.getIdentifier(key, "string", context.packageName))
                click(t("settings"))
                click(t("operation_log_export"))
                assertEquals(AppActionKind.LOG_EXPORT, actions.last().kind)
                click(t("operation_log_clear"))
                val before = actions.size
                click(t("cancel"))
                assertEquals(before, actions.size)
                click(t("operation_log_clear"))
                click(t("operation_log_clear"))
                assertEquals(AppActionKind.LOG_CLEAR, actions.last().kind)
                click(t("downloads_clear"))
                assertEquals(AppActionKind.LOG_CLEAR, actions.last().kind)
                click(t("downloads_clear"))
                assertEquals(AppActionKind.CLEAR_DOWNLOADS, actions.last().kind)
                click("← ${t("back")}")
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }

}
