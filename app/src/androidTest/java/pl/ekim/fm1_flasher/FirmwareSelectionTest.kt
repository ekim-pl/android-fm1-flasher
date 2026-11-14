package pl.ekim.fm1_flasher

import android.test.InstrumentationTestCase
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner

@Suppress("DEPRECATION")
class FirmwareSelectionTest : InstrumentationTestCase() {
    fun testSameIdInTwoSourcesSelectsTheRequestedLocalOffer() {
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme_Light)
            val first = localEntry()
            val second = first.copy(sourceId = "awesome-fm1", chip = "unknown")
            val selected = mutableListOf<String>()
            val panel = FlasherPanel(context, {}, {}, {}, {}, {}, { selected += it }, {}, {})
            panel.render(UiState(busy = false, ready = first,
                downloads = listOf(LocalPackage(first, 1, 1000), LocalPackage(second, 1, 1000))))
            fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val picker = descendants(panel).filterIsInstance<Spinner>().single {
                it.contentDescription == context.getString(R.string.select_downloaded_firmware)
            }
            assertEquals(1, picker.selectedItemPosition)
            picker.onItemSelectedListener!!.onItemSelected(picker, null, 2, 2)
            assertEquals(listOf(second.key), selected)
            assertFalse(FlashChip.OLD.allows(second))
            assertFalse(FlashChip.NEW.allows(second))
        }
    }
}
