import SwiftUI

// Order status -> colours, labels. Every value comes from DesignTokens.swift (generated from design/tokens.json,
// the same file that generates the Android colours), so both apps always show the same colours.
enum StatusStyle {
    static func bar(_ s: String) -> Color {
        switch s.lowercased() {
        case "pending": return Dt.barNew
        case "accepted": return Dt.barAccepted
        case "preparing": return Dt.barPreparing
        case "ready": return Dt.barReady
        case "delivering": return Dt.barDelivering
        case "delivered": return Dt.barDelivered
        default: return Dt.barCancelled
        }
    }
    static func pillBg(_ s: String) -> Color {
        switch s.lowercased() {
        case "pending": return Dt.pillNewBg
        case "accepted": return Dt.pillAcceptedBg
        case "preparing": return Dt.pillPreparingBg
        case "ready": return Dt.pillReadyBg
        case "delivering": return Dt.pillDeliveringBg
        case "delivered": return Dt.pillDeliveredBg
        default: return Dt.pillCancelledBg
        }
    }
    static func pillFg(_ s: String) -> Color { ["cancelled", "unknown"].contains(s.lowercased()) ? Dt.pillCancelledFg : Dt.pillNewFg }
    static func title(_ s: String) -> String {
        ["pending": "Yangi", "accepted": "Qabul qilingan", "preparing": "Tayyorlanmoqda", "ready": "Tayyor",
         "delivering": "Yetkazilmoqda", "delivered": "Yetkazildi", "cancelled": "Bekor qilingan"][s.lowercased()] ?? "—"
    }
}

/// 1 245 000 so'm (thin groups of three, exactly like Android's Long.som()).
func som(_ value: Int64) -> String {
    var out = ""
    for (i, ch) in String(value).reversed().enumerated() {
        if i > 0 && i % 3 == 0 { out.append(" ") }
        out.append(ch)
    }
    return String(out.reversed()) + " so'm"
}

func hhmm(_ millis: Int64) -> String {
    let f = DateFormatter(); f.dateFormat = "HH:mm"
    return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
}
func hhmmDate(_ millis: Int64) -> String {
    let f = DateFormatter(); f.dateFormat = "HH:mm • dd.MM.yyyy"
    return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
}

extension View {
    /// The dark page background of every screen.
    func lgScreen() -> some View { background(Dt.bg.ignoresSafeArea()).foregroundStyle(Dt.text) }
}
