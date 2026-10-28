import AVFoundation
import UIKit
import UserNotifications

struct ReadinessRow: Identifiable { let id: String; let title: String; let hint: String; let ok: Bool }

/// Everything about "making noise for a new order" on iOS.
///
/// What iOS allows (and what it does not):
///  • App open  → looping alarm + haptics until the order is accepted (like Android).
///  • App closed / phone locked → APNs alert with a custom sound (≤30 s). The server repeats it while order status
///    remains pending (docs/SERVER-PUSH.md). iOS does not allow indefinite app audio in the background.
@MainActor
final class AlertCenter {
    static let shared = AlertCenter()
    private var player: AVAudioPlayer?
    private var hapticTimer: Timer?
    private let soundFile = "order_alert.wav"

    // ---- in-app alarm --------------------------------------------------------------------------------
    func startRinging(volume: Float, sound: Bool, vibrate: Bool) {
        if sound {
            if player == nil, let url = Bundle.main.url(forResource: "order_alert", withExtension: "wav") {
                // .playback keeps the sound audible with the silent switch on, like an alarm
                try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try? AVAudioSession.sharedInstance().setActive(true)
                player = try? AVAudioPlayer(contentsOf: url)
                player?.numberOfLoops = -1
            }
            player?.volume = volume
            if player?.isPlaying == false { player?.play() }
        } else { stopSound() }
        if vibrate {
            if hapticTimer == nil {
                let gen = UINotificationFeedbackGenerator()
                gen.notificationOccurred(.warning)
                hapticTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { _ in gen.notificationOccurred(.warning) }
            }
        } else { stopHaptics() }
    }

    func stopRinging() { stopSound(); stopHaptics() }
    private func stopSound() { player?.stop(); player = nil; try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    private func stopHaptics() { hapticTimer?.invalidate(); hapticTimer = nil }

    // ---- permissions ---------------------------------------------------------------------------------
    func authorizationStatus() async -> UNAuthorizationStatus { await UNUserNotificationCenter.current().notificationSettings().authorizationStatus }

    private func registerCategories() {
        let accept = UNNotificationAction(identifier: "ORDER_ACCEPT", title: "Qabul qilish", options: [.foreground])
        let cancel = UNNotificationAction(identifier: "ORDER_CANCEL", title: "Bekor qilish", options: [.foreground, .destructive])
        let category = UNNotificationCategory(identifier: "NEW_ORDER", actions: [accept, cancel], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    /// Apple: call this on EVERY launch (the token can change). Without it the server never gets this iPhone's push token —
    /// last release dropped it and push silently stopped working for users who had already granted notifications.
    func registerForPushIfAllowed() async {
        registerCategories()                    // lock-screen "Qabul qilish / Bekor qilish" buttons must exist on EVERY launch, not only at the first permission prompt
        let s = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if s == .authorized || s == .provisional || s == .ephemeral { UIApplication.shared.registerForRemoteNotifications() }
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        registerCategories()
        let ok = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge, .timeSensitive])) ?? false
        if ok { UIApplication.shared.registerForRemoteNotifications() }
        return ok
    }

    // Clear any scheduled reminders left by older builds.
    func cancelBackgroundAlerts() {
        let ids = (0..<6).map { "order-alert-\($0)" }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
    }

    // ---- "will a new order really wake this phone?" ---------------------------------------------------
    func readiness() async -> [ReadinessRow] {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        return [
            ReadinessRow(id: "auth", title: "Bildirishnomalar", hint: "Yangi buyurtma xabari kelishi uchun yoqilgan bo'lishi shart", ok: s.authorizationStatus == .authorized),
            ReadinessRow(id: "sound", title: "Bildirishnoma ovozi", hint: "Buyurtma ovozi chalinishi uchun «Tovushlar» yoqilgan bo'lsin", ok: s.soundSetting == .enabled),
            ReadinessRow(id: "lock", title: "Bloklangan ekranda ko'rsatish", hint: "Telefon bloklangan paytda xabar ekranda chiqishi uchun", ok: s.lockScreenSetting == .enabled),
            ReadinessRow(id: "time", title: "Muhim bildirishnomalar", hint: "«Time Sensitive» yoqilsa, Fokus / Bezovta qilmang rejimida ham o'tadi", ok: s.timeSensitiveSetting == .enabled),
            ReadinessRow(id: "bg", title: "Fonda yangilash", hint: "Sozlamalar → Umumiy → Fonda yangilash: ilova fonda ham buyurtmalarni tekshira olishi uchun", ok: UIApplication.shared.backgroundRefreshStatus == .available),
            ReadinessRow(id: "lowpower", title: "Quvvat tejash rejimi o'chiq", hint: "Yoqilgan bo'lsa iOS fondagi ishni cheklaydi — buyurtma kechikishi mumkin", ok: !ProcessInfo.processInfo.isLowPowerModeEnabled),
        ]
    }
}
