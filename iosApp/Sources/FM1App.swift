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
    func makeUIViewController(context: Context) -> UIViewController {
        let bundle = Bundle.main
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        let configuration = bundle.object(forInfoDictionaryKey: "FM1BuildConfiguration") as? String
        let date = bundle.url(forResource: "FM1BuildDate", withExtension: "txt").flatMap { url in
            (try? String(contentsOf: url, encoding: .utf8))?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return MainViewControllerKt.MainViewController(
            backend: backend, version: version, configuration: configuration, buildDate: date
        )
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
