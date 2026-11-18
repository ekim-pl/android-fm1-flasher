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
class DeviceToolsUiTest : InstrumentationTestCase() {
    fun testWriteRequiresExportAndConfirmationAndExperimentalOptOutHidesTools() {
        val actions = mutableListOf<AppAction>()
        val analysis = FlashLayout.analyze(ByteArray(FlashLayout.SIZE) { -1 }, 0x980F)
        val view = ToolsView("test-backup", "0".repeat(64), DeviceDiagnostics(0x856014, 0x980F, FlashLayout.SIZE), analysis,
            FlashOperation.MBOOT, listOf(0), analysis, "preview-token", exported = false)
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false, fleetAvailable = true,
                experimental = true, usbRecovery = true, tools = view))
            override fun perform(action: AppAction) { actions += action }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previous = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun find(label: String) = nodes().lastOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true && node.config.getOrNull(SemanticsActions.OnClick) != null }
        fun locate(label: String, click: Boolean = true) {
            repeat(35) {
                instrumentation.waitForIdleSync(); Thread.sleep(60)
                var found = false
                instrumentation.runOnMainSync {
                    val node = find(label)
                    if (node != null) {
                        if (click) assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
                        else assertTrue(node.config.contains(SemanticsProperties.Disabled))
                        found = true
                    } else nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                        ?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 500f)
                }
                if (found) return
            }
            error("Missing $label")
        }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            locate("Modes and flash chip")
            locate("Read and back up ROM flash")
            assertEquals(AppActionKind.TOOLS_READ, actions.last().kind)
            locate("Write reviewed plan", click = false)
            assertTrue(actions.none { it.kind == AppActionKind.TOOLS_WRITE })
            instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(tools = view.copy(exported = true)) }
            locate("Write reviewed plan")
            assertTrue(actions.none { it.kind == AppActionKind.TOOLS_WRITE })
            locate("Cancel")
            assertTrue(actions.none { it.kind == AppActionKind.TOOLS_WRITE })
            locate("Write reviewed plan")
            locate("Write reviewed plan")
            assertEquals(AppAction(AppActionKind.TOOLS_WRITE, "preview-token"), actions.last())
            assertEquals(1, actions.count { it.kind == AppActionKind.TOOLS_WRITE })
            instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(busy = true) }
            locate("Write reviewed plan", click = false)
            instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(busy = false, tools = view.copy(exported = true, token = null)) }
            locate("Write reviewed plan", click = false)
            locate("Prepare write plan")
            assertEquals(AppAction(AppActionKind.TOOLS_PREPARE, value = "MBOOT"), actions.last())
            instrumentation.runOnMainSync { backend.state.value = backend.state.value.copy(experimental = false) }
            instrumentation.waitForIdleSync(); Thread.sleep(100)
            instrumentation.runOnMainSync { assertNull(find("Prepare write plan")) }
            assertEquals(1, actions.count { it.kind == AppActionKind.TOOLS_WRITE })
            locate("MIDI and backups")
            locate("Import backup")
            assertEquals(AppAction(AppActionKind.TOOLS_IMPORT), actions.last())
        } finally {
            instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previous }
        }
    }
}
