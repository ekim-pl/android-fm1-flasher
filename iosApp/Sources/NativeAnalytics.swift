import Foundation
import FM1Compose
#if canImport(FirebaseAnalytics)
import FirebaseCore
import FirebaseAnalytics
#endif

final class NativeAnalytics: NSObject, AnalyticsSink {
    #if canImport(FirebaseAnalytics)
    private var configured = false
    private var options: FirebaseOptions? {
        let environment = Bundle.main.object(forInfoDictionaryKey: "FM1AnalyticsEnvironment") as? String
        guard environment == "test" || environment == "production" else { return nil }
        #if DEBUG
        guard environment == "test" else { return nil }
        #endif
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path), options.bundleID == Bundle.main.bundleIdentifier else { return nil }
        return options
    }
    var available: Bool { options != nil }
    func configure(enabled: Bool, reset: Bool) {
        if enabled && !configured {
            guard let options = options else { return }
            FirebaseApp.configure(options: options)
            configured = true
        }
        guard configured else { return }
        Analytics.setAnalyticsCollectionEnabled(false)
        Analytics.setConsent([
            .analyticsStorage: enabled ? .granted : .denied,
            .adStorage: .denied, .adUserData: .denied, .adPersonalization: .denied
        ])
        if reset { Analytics.resetAnalyticsData() }
        if enabled { Analytics.setAnalyticsCollectionEnabled(true) }
    }
    func record(name: String, parameters: [String: String]) {
        guard configured else { return }
        var values: [String: Any] = parameters
        for key in ["duration_ms", "schema_version"] {
            if let value = parameters[key], let number = Int64(value) { values[key] = number }
        }
        Analytics.logEvent(name, parameters: values)
    }
    #else
    var available: Bool { false }
    func configure(enabled: Bool, reset: Bool) {}
    func record(name: String, parameters: [String: String]) {}
    #endif
}
