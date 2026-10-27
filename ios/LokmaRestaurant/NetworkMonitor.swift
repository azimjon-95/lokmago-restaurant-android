import Network

/// Is there a usable network path RIGHT NOW (Wi-Fi / LTE)? Deliberately separate from "is the server socket joined":
/// a phone with LTE whose socket is still reconnecting is NOT offline — saying "Internet yo'q" there was the bug.
final class NetworkMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "uz.lokmago.network")
    private var last = true
    /// Called on the main queue, only when the state actually changes.
    var onChange: ((Bool) -> Void)?

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let online = path.status == .satisfied
            guard online != self.last else { return }
            self.last = online
            DispatchQueue.main.async { self.onChange?(online) }
        }
        monitor.start(queue: queue)
    }

    func stop() { monitor.cancel() }
}
