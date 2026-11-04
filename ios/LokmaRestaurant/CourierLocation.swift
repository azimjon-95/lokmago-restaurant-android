import CoreLocation

/// «Ishni boshladim» dan «Ishni yakunladim» gacha kuryerning joylashuvini HAR 5 SONIYADA yuboradi (server → Redis).
/// Ilova ko'rinib turgan paytda boshlanadi va fonda ham davom etadi (UIBackgroundModes: location, ekranning yuqorisida ko'k belgi) —
/// «Har doim» ruxsati KERAK EMAS. Foydalanuvchi smenani yakunlamaguncha to'xtatilmaydi. Server 409/401 desa — to'xtaydi.
@MainActor final class CourierLocation: NSObject, CLLocationManagerDelegate {
    static let shared = CourierLocation()
    private let manager = CLLocationManager()
    private var last: CLLocation?
    private var loop: Task<Void, Never>?
    var status: CLAuthorizationStatus { manager.authorizationStatus }
    var onAuthChange: ((CLAuthorizationStatus) -> Void)?

    override init() { super.init(); manager.delegate = self }

    var isDenied: Bool { status == .denied || status == .restricted }
    func requestPermission() { if status == .notDetermined { manager.requestWhenInUseAuthorization() } }

    /// [post] har 5 s da eng yangi nuqta bilan chaqiriladi; `false` qaytarsa (server «smena yopiq / token yaroqsiz») sikl to'xtaydi.
    func start(post: @escaping (CLLocation) async -> Bool) {
        guard loop == nil else { return }
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = .automotiveNavigation
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        loop = Task { [weak self] in
            while !Task.isCancelled {
                if let loc = self?.last, abs(loc.timestamp.timeIntervalSinceNow) < 30 {
                    if await post(loc) == false { self?.stop(); return }
                }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    func stop() {
        loop?.cancel(); loop = nil
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        last = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let newest = locations.last, newest.horizontalAccuracy >= 0 else { return }
        Task { @MainActor in self.last = newest }
    }
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let s = manager.authorizationStatus
        Task { @MainActor in self.onAuthChange?(s) }
    }
}
