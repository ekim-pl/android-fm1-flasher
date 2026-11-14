package pl.ekim.fm1_flasher

import android.content.Intent
import android.test.InstrumentationTestCase
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("DEPRECATION")
class ComposeCatalogUpdatesTest : InstrumentationTestCase() {
    fun testCatalogChangesBehindSourceDialogKeepAccessibilityAttached() {
        val hub = CatalogSource.hub.copy(snapshot = true, checkedAt = System.currentTimeMillis(), entryCount = 1)
        val extra = CatalogSource.awesome.copy(snapshot = true, checkedAt = System.currentTimeMillis(), entryCount = 1)
        val first = localEntry().copy(name = "Hub firmware")
        val second = first.copy(sourceId = extra.id, name = "Additional firmware")
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false,
                catalog = Catalog(listOf(first), "", hub.checkedAt, true, listOf(hub, extra), listOf(first, second))))
            override fun perform(action: AppAction) = Unit
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previousCallback = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = { roots += it } }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        fun click(text: String) {
            instrumentation.waitForIdleSync()
            Thread.sleep(50)
            instrumentation.runOnMainSync {
                val node = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true && node.config.getOrNull(SemanticsActions.OnClick) != null }
                    ?: error("Missing $text")
                assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            }
            instrumentation.waitForIdleSync()
        }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            click("Settings"); click("Firmware sources")
            repeat(8) { index ->
                instrumentation.runOnMainSync {
                    val enabled = index % 2 == 0
                    backend.state.value = backend.state.value.copy(busy = true)
                    backend.state.value = backend.state.value.copy(busy = false,
                        catalog = Catalog(if (enabled) listOf(first, second) else listOf(first), "", hub.checkedAt, true,
                            listOf(hub, extra.copy(enabled = enabled, revision = index.toLong())), listOf(first, second)))
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(50)
                instrumentation.runOnMainSync {
                    assertTrue(nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Firmware sources" } == true })
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previousCallback } }
    }
    fun testRefreshSurvivesBusyFramesAndRecycledCatalogSemantics() {
        val entries = (0 until 40).map { localEntry().copy(id = "entry-$it", name = "Firmware $it") }
        var requests = 0
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false,
                catalog = Catalog(entries, "cached", System.currentTimeMillis(), true)))
            override fun perform(action: AppAction) {
                if (action.kind != AppActionKind.REFRESH || state.value.busy) return
                requests++
                state.value = state.value.copy(busy = true, status = msg("checking_catalog"))
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    state.value = state.value.copy(busy = false, status = msg("catalog_checked", 0),
                        catalog = state.value.catalog!!.copy(entries = entries.toList()))
                }, 200)
            }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val roots = mutableListOf<ViewRootForTest>()
        val previousCallback = ViewRootForTest.onViewCreatedCallback
        instrumentation.runOnMainSync {
            ViewRootForTest.onViewCreatedCallback = { root -> roots += root }
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeActivity
        fun nodes() = roots.filter { it.view.isAttachedToWindow }.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true) }
        try {
            instrumentation.runOnMainSync { activity.setContent { FlasherApp(backend) } }
            instrumentation.waitForIdleSync(); Thread.sleep(100)
            repeat(6) { iteration ->
                instrumentation.runOnMainSync {
                    if (iteration == 0) {
                        val button = nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "↻ Refresh" } == true && node.config.getOrNull(SemanticsActions.OnClick) != null }
                        assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                    } else {
                        nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                            ?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, if (iteration % 2 == 0) -900f else 900f)
                        backend.perform(AppAction(AppActionKind.REFRESH))
                    }
                }
                Thread.sleep(70)
                instrumentation.runOnMainSync {
                    assertTrue(backend.state.value.busy)
                    assertTrue(nodes().isNotEmpty())
                }
                Thread.sleep(220); instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertFalse(backend.state.value.busy)
                    assertTrue(nodes().isNotEmpty())
                }
            }
            assertEquals(6, requests)
        } finally { instrumentation.runOnMainSync { activity.finish(); ViewRootForTest.onViewCreatedCallback = previousCallback } }
    }

}
