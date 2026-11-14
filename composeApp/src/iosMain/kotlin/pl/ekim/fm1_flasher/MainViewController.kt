package pl.ekim.fm1_flasher

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(backend: PortableAppBackend) = ComposeUIViewController { FlasherApp(backend) }
