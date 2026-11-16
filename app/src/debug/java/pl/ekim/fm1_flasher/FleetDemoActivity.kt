package pl.ekim.fm1_flasher

import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug-only visual fixture. Never binds to the hardware service or production storage. */
class FleetDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("fleet-color-demo", MODE_PRIVATE)
        val devices = (0..3).map { index ->
            FleetDevice("demo-$index", listOf("Bass", "Lead", "Pads", "Offline FM1")[index],
                color = DeviceColor.fromIndex(prefs.getInt("color-$index", index)).ordinal,
                endpoint = if (index == 3) null else Endpoint(index, "demo/$index", 1),
                identity = FirmwareIdentity("FM-1", "015"), remembered = true)
        }
        val backend = object : AppBackend {
            override val state = MutableStateFlow(AppSnapshot(initialized = true, busy = false,
                fleetAvailable = true, fleet = devices, offline = true, language = "pl", status = msg("fleet_devices")))
            override fun perform(action: AppAction) {
                if (action.kind == AppActionKind.DEVICE_COLOR && !state.value.queueRunning) {
                    val color = action.value.toIntOrNull() ?: return
                    if (color !in DeviceColor.entries.indices) return
                    val index = devices.indexOfFirst { it.id == action.id }
                    if (index < 0) return
                    prefs.edit().putInt("color-$index", color).apply()
                    state.value = state.value.copy(fleet = state.value.fleet.map {
                        if (it.id == action.id) it.copy(color = color) else it
                    })
                }
            }
            override fun saveBrowse(filters: BrowseFilters) = Unit
            override fun document(name: String) = ""
            override suspend fun artwork(entry: CatalogEntry): androidx.compose.ui.graphics.ImageBitmap? = null
        }
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.statusBars())
                view.setPadding(bars.left, bars.top, bars.right, 0)
                insets
            }
        }
        layout.addView(android.widget.TextView(this).apply { text = "DEMO · fikcyjne FM1 · bez USB" })
        layout.addView(android.widget.Button(this).apply {
            text = "Jasny / ciemny"
            setOnClickListener {
                val snapshot = backend.state.value
                backend.state.value = snapshot.copy(appearance = if (snapshot.appearance == "DARK") "LIGHT" else "DARK")
            }
        })
        layout.addView(android.widget.Button(this).apply {
            text = "Pokaż / wyczyść zadania"
            setOnClickListener {
                val snapshot = backend.state.value
                val jobs = if (snapshot.jobs.isEmpty()) snapshot.fleet.mapIndexed { index, device ->
                    FlashJob("job-$index", device.id, device.alias, device.endpoint ?: Endpoint(9, "demo/offline", 1),
                        "demo", "0".repeat(64), "Demo firmware", "1.0", device.identity!!, device.chip,
                        outcome = listOf(JobOutcome.QUEUED, JobOutcome.RUNNING, JobOutcome.CONFIRMED, JobOutcome.UNCONFIRMED)[index],
                        phase = if (index == 1) "flash_stage_write" else "fleet_unconfirmed",
                        progress = 47, color = device.color)
                } else emptyList()
                backend.state.value = snapshot.copy(jobs = jobs, queueRunning = jobs.isNotEmpty(), busy = jobs.isNotEmpty())
            }
        })
        layout.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { FlasherApp(backend) }
        }, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
    }
}
