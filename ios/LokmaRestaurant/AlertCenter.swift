import AVFoundation
import UIKit
import UserNotifications

struct ReadinessRow: Identifiable { let id: String; let title: String; let hint: String; let ok: Bool }

/// Everything about "making noise for a new order" on iOS.
///
/// What iOS allows (and what it does not):
///  • App open  → looping alarm + haptics until the order is accepted (like Android).
///  • App closed / phone locked → ONLY a push notification can wake it. The server must send an APNs push with the
///    custom sound (≤30 s) and `interruption-level: time-sensitive` (docs/SERVER-PUSH.md). iOS has no way for an app
///    to keep ringing in the background on its own; a repeating alarm is done by the server re-sending the push.
///  • Local notifications below only cover the gap when the app WAS running with unaccepted orders and then went to
///    the background (they repeat a few times; they are cancelled the moment the app comes back).
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

    /// Apple: call this on EVERY launch (the token can change). Without it the server never gets this iPhone's push token —
    /// last release dropped it and push silently stopped working for users who had already granted notifications.
    func registerForPushIfAllowed() async {
        let s = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if s == .authorized || s == .provisional || s == .ephemeral { UIApplication.shared.registerForRemoteNotifications() }
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        let ok = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        if ok { UIApplication.shared.registerForRemoteNotifications() }
        return ok
    }

    // ---- gap cover: app had unaccepted orders and went to the background ----------------------------
    func scheduleBackgroundAlerts(for queue: [RestaurantOrder]) {
        cancelBackgroundAlerts()
        guard let head = queue.first else { return }
        let center = UNUserNotificationCenter.current()
        for i in 0..<6 {
            let c = UNMutableNotificationContent()
            c.title = "🔔 Yangi buyurtma"
            c.body = queue.count > 1 ? "#\(head.number) • \(som(head.total)) — yana \(queue.count - 1) ta kutmoqda" : "#\(head.number) • \(som(head.total)) — qabul qilinmagan"
            c.sound = UNNotificationSound(named: UNNotificationSoundName(soundFile))
            c.interruptionLevel = .timeSensitive
            c.threadIdentifier = "orders"
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5 + 25 * Double(i), repeats: false)
            center.add(UNNotificationRequest(identifier: "order-alert-\(i)", content: c, trigger: trigger))
        }
    }

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
