import Foundation
import UIKit
import FM1Compose

final class NativeStorage: NSObject, AppStorage {
    private let root: URL
    override init() {
        root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("FM1Flasher", isDirectory: true)
        super.init()
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        var url = root
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }
    private func url(_ path: String) throws -> URL {
        let target = root.appendingPathComponent(path).standardizedFileURL
        guard target.path.hasPrefix(root.standardizedFileURL.path + "/") else { throw nativeError("Invalid storage path") }
        return target
    }
    func read(path: String, limit: Int32) throws -> KotlinByteArray? {
        let file = try url(path)
        if !FileManager.default.fileExists(atPath: file.path) { return nil }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? Int.max
        guard limit > 0, size <= Int(limit) else { throw nativeError("Stored file exceeds size limit") }
        let bytes = try Data(contentsOf: file)
        guard bytes.count <= Int(limit) else { throw nativeError("Stored file changed while reading") }
        return kotlinBytes(Array(bytes))
    }
    func write(path: String, bytes: KotlinByteArray) throws {
        let file = try url(path)
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try nativeData(bytes).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    func remove(path: String) throws {
        let file = try url(path)
        if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
    }
    func size(path: String) throws -> KotlinLong? {
        let file = try url(path)
        if !FileManager.default.fileExists(atPath: file.path) { return nil }
        return KotlinLong(value: Int64(try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0))
    }
    func list(path: String) throws -> [String] {
        let directory = try url(path)
        if !FileManager.default.fileExists(atPath: directory.path) { return [] }
        return try FileManager.default.contentsOfDirectory(atPath: directory.path)
    }
}

func nativeError(_ text: String) -> NSError { NSError(domain: "FM1", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
func nativeData(_ bytes: KotlinByteArray) -> Data { Data((0..<Int(bytes.size)).map { UInt8(bitPattern: bytes.get(index: Int32($0))) }) }

/// Download delegates bound every redirect and streamed chunk, including responses without Content-Length.
final class NativeHttp: NSObject, HttpClient {
    func get(url: String, limit: Int32, artworkOnly: Bool) throws -> KotlinByteArray {
        let download = BoundedDownload(limit: Int(limit), artworkOnly: artworkOnly)
        return try download.fetch(url)
    }
}
private final class BoundedDownload: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate {
    private let limit: Int
    private let artworkOnly: Bool
    private let done = DispatchSemaphore(value: 0)
    private var bytes = Data()
    private var failure: Error?
    private var redirects = 0
    init(limit: Int, artworkOnly: Bool) { self.limit = limit; self.artworkOnly = artworkOnly }
    private func permitted(_ url: URL?) -> Bool {
        guard let url, url.scheme == "https", url.user == nil, url.password == nil, url.fragment == nil,
              url.port == nil || url.port == 443, let host = url.host?.lowercased() else { return false }
        let allowed = artworkOnly ? ["thefirmwarehub.com"] : ["thefirmwarehub.com", "api.github.com", "github.com", "raw.githubusercontent.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"]
        return allowed.contains(host)
    }
    func fetch(_ address: String) throws -> KotlinByteArray {
        guard limit > 0, let url = URL(string: address), permitted(url) else { throw nativeError("Download address blocked") }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20; configuration.timeoutIntervalForResource = 60
        configuration.httpCookieStorage = nil; configuration.urlCache = nil
        let queue = OperationQueue(); queue.maxConcurrentOperationCount = 1
        let session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
        request.setValue("FM1Flasher/\(version) (+iOS)", forHTTPHeaderField: "User-Agent")
        let task = session.dataTask(with: request); task.resume()
        guard done.wait(timeout: .now() + 60) == .success else { task.cancel(); throw nativeError("Download timed out") }
        if let failure { throw failure }
        return kotlinBytes(Array(bytes))
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        redirects += 1
        guard redirects <= 5, permitted(request.url) else {
            failure = nativeError("Redirect blocked"); completionHandler(nil); task.cancel(); return
        }
        completionHandler(request)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let response = response as? HTTPURLResponse, response.statusCode == 200, permitted(response.url), response.expectedContentLength <= Int64(limit) else {
            failure = nativeError("Invalid or oversized HTTP response"); completionHandler(.cancel); return
        }
        completionHandler(.allow)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard bytes.count <= limit - data.count else { failure = nativeError("Download exceeds size limit"); dataTask.cancel(); return }
        bytes.append(data)
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if failure == nil { failure = error }; done.signal()
    }
}

final class NativeAppHost: NSObject, AppHost {
    var systemLanguage: String { Locale.preferredLanguages.first ?? "en" }
    private var backgroundTask = UIBackgroundTaskIdentifier.invalid
    func document(name: String) -> String {
        guard !name.contains(".."), let root = Bundle.main.resourceURL else { return "" }
        return (try? String(contentsOf: root.appendingPathComponent(name), encoding: .utf8)) ?? ""
    }
    func openLink(url: String) {
        guard let target = URL(string: url), ["https", "mailto"].contains(target.scheme ?? "") else { return }
        DispatchQueue.main.async { UIApplication.shared.open(target) }
    }
    func exportText(name: String, text: String) throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try text.write(to: file, atomically: true, encoding: .utf8)
        DispatchQueue.main.async {
            guard let presenter = self.presenter() else { return }
            let share = UIActivityViewController(activityItems: [file], applicationActivities: nil)
            share.popoverPresentationController?.sourceView = presenter.view
            share.popoverPresentationController?.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
            presenter.present(share, animated: true)
        }
    }
    private func presenter() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        var controller = windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
    func setAppearance(theme: String) {
        DispatchQueue.main.async {
            let style: UIUserInterfaceStyle = theme == "DARK" ? .dark : theme == "LIGHT" ? .light : .unspecified
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).forEach { $0.overrideUserInterfaceStyle = style }
        }
    }
    func setFlashing(active: Bool) {
        DispatchQueue.main.async {
            UIApplication.shared.isIdleTimerDisabled = active
            if active && self.backgroundTask == .invalid {
                self.backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "FM1 flash") {
                    if self.backgroundTask != .invalid { UIApplication.shared.endBackgroundTask(self.backgroundTask); self.backgroundTask = .invalid }
                }
            } else if !active && self.backgroundTask != .invalid {
                UIApplication.shared.endBackgroundTask(self.backgroundTask); self.backgroundTask = .invalid
            }
        }
    }
}
