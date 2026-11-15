import SwiftUI
import FM1Compose

@main
struct FM1App: App {
    @Environment(\.scenePhase) private var phase
    private let backend: PortableAppBackend
    init() {
        let midi = CoreMidiBackend()
        backend = PortableAppBackend(midi: midi, monitor: midi, storage: NativeStorage(), http: NativeHttp(), host: NativeAppHost(), analyticsSink: NativeAnalytics())
    }
    var body: some Scene {
        WindowGroup {
            ComposeScreen(backend: backend).ignoresSafeArea()
                .onChange(of: phase) { phase in backend.foreground(visible: phase == .active) }
        }
    }
}
private struct ComposeScreen: UIViewControllerRepresentable {
    let backend: PortableAppBackend
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController(backend: backend) }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
