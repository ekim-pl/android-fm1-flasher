package pl.ekim.fm1_flasher

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.update

fun MainViewController(backend: PortableAppBackend, version: String?, configuration: String?, buildDate: String?): platform.UIKit.UIViewController {
    if (version != null && configuration != null) {
        val kind = if (configuration == "Debug") "debug" else "release"
        backend.state.update { it.copy(appBuild = AppBuildInfo(version, kind, buildDate)) }
    }
    return ComposeUIViewController { FlasherApp(backend) }
}
