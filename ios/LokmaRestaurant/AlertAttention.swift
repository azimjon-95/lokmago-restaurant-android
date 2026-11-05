import Foundation

/// ALARM OVOZINI FOYDALANUVCHI HARAKATIGA QARAB BOSHQARISH — Android'dagi `AlertAttention.kt` bilan BIR XIL mantiq (u yerda testlangan, 46 tekshiruv).
///  Ilovaga kirdi → JUDA PAST ovoz · buyurtma tafsilotini ochdi → yanada past · tafsilotdan hech narsa qilmay chiqdi → bir necha soniyadan keyin yana ko'tariladi ·
///  ilovada 20 s tegmay turdi → o'rtacha · ilovadan chiqdi → 3 s dan keyin to'liq. Hamma o'tish yumshoq (fadeDuration), keskin sakrash yo'q.
/// (iOS ilova fonda ovoz chalolmaydi — u yerda ovozni serverdan kelgan push beradi; bu sinf faqat ilova ochiq paytda ishlaydi.)
final class AlertAttention {
    struct Target { let level: Float; let rampMs: Int }
    static let full: Float = 1.0, recover: Float = 0.45, inApp: Float = 0.18, onDetails: Float = 0.08
    static let awayGraceMs = 3_000, idleMs = 20_000, leftDetailsRiseMs = 6_000, downRampMs = 600, upRampMs = 1_800

    private let now: () -> Int
    private var visible = false, details = false
    private var leftAt = 0, detailsLeftAt = 0, lastTouch = 0

    init(now: @escaping () -> Int = { Int(ProcessInfo.processInfo.systemUptime * 1000) }) { self.now = now }

    func appVisible(_ v: Bool) { guard v != visible else { return }; visible = v; if !v { leftAt = now(); details = false } else { lastTouch = now() } }
    func detailsOpen(_ v: Bool) { guard v != details else { return }; details = v; if !v { detailsLeftAt = now() } else { lastTouch = now() } }
    func touched() { lastTouch = now() }

    func target() -> Target {
        let t = now()
        if !visible { return (leftAt == 0 || t - leftAt >= Self.awayGraceMs) ? Target(level: Self.full, rampMs: Self.upRampMs) : Target(level: Self.inApp, rampMs: Self.downRampMs) }
        if details { return Target(level: Self.onDetails, rampMs: Self.downRampMs) }
        if detailsLeftAt != 0 && lastTouch <= detailsLeftAt && t - detailsLeftAt >= Self.leftDetailsRiseMs { return Target(level: Self.recover, rampMs: Self.upRampMs) }
        if t - lastTouch >= Self.idleMs { return Target(level: Self.recover, rampMs: Self.upRampMs) }
        return Target(level: Self.inApp, rampMs: Self.downRampMs)
    }
}
