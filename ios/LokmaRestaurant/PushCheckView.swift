import SwiftUI
import UIKit

/// "Nega yopiq ilovaga xabar kelmayapti?" — push zanjirining har bir halqasini tekshiradi va nima qilish kerakligini aytadi.
/// Zanjir: ruxsat → iPhone push tokeni → token serverda → serverda APNs sozlangan → test push haqiqatan yetadimi.
struct PushCheckView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var steps: [Step] = []
    @State private var running = false
    @State private var testing = false
    @State private var testLines: [Step] = []

    struct Step: Identifiable { let id = UUID(); let ok: Bool; let title: String; let advice: String }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Push tekshiruvi").font(.system(size: 22, weight: .heavy))
                Spacer()
                Button { dismiss() } label: { LgIcon(name: "ic_close").frame(width: 36, height: 36) }.buttonStyle(.plain)
            }.padding(16)
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Ilova yopiq yoki telefon bloklangan paytda yangi buyurtma xabari faqat shu zanjir to'liq bo'lsa keladi.")
                        .font(.system(size: 13)).foregroundStyle(Dt.textSecondary)
                    if running { ProgressView().tint(Dt.green).frame(maxWidth: .infinity).padding(.vertical, 20) }
                    ForEach(steps + testLines) { s in
                        HStack(alignment: .top, spacing: 12) {
                            LgIcon(name: s.ok ? "ic_check" : "ic_warning", color: s.ok ? Dt.greenText : Dt.orange, size: 16)
                                .frame(width: 30, height: 30).background((s.ok ? Dt.green : Dt.orange).opacity(0.18), in: Circle())
                            VStack(alignment: .leading, spacing: 3) {
                                Text(s.title).font(.system(size: 15, weight: .semibold))
                                if !s.advice.isEmpty { Text(s.advice).font(.system(size: 12)).foregroundStyle(Dt.textMuted) }
                            }
                            Spacer(minLength: 0)
                        }.padding(14).background(Dt.card, in: RoundedRectangle(cornerRadius: 16))
                    }
                }.padding(.horizontal, 16).padding(.bottom, 12)
            }
            VStack(spacing: 8) {
                PrimaryButton(title: "Test push yuborish", icon: "ic_bell_ring", color: Dt.orange, loading: testing, enabled: !running, action: sendTest)
                Text("Bosgach ilovani yoping (yuqoriga suring) va bloklang — 5-10 soniyada «Push ishlayapti ✓» xabari kelishi kerak.")
                    .font(.system(size: 11)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center)
            }.padding(16)
        }
        .lgScreen().preferredColorScheme(.dark)
        .task { await run() }
    }

    private func run() async {
        running = true; defer { running = false }
        var out: [Step] = []
        let ud = UserDefaults.standard
        let auth = await AlertCenter.shared.authorizationStatus()
        let allowed = auth == .authorized || auth == .provisional || auth == .ephemeral
        out.append(Step(ok: allowed, title: allowed ? "Bildirishnoma ruxsati berilgan" : "Bildirishnoma ruxsati YO'Q",
                        advice: allowed ? "" : "Sozlamalar → LGO Business → Bildirishnomalar → «Bildirishnomalarga ruxsat»ni yoqing."))
        let token = ud.string(forKey: "apns-token")
        if let err = ud.string(forKey: "apns-error") {
            out.append(Step(ok: false, title: "iPhone push tokenini ololmadi", advice: "\(err). Xcode: Signing & Capabilities → «Push Notifications» qo'shilganini tekshiring; haqiqiy iPhone kerak (simulyatorda token yo'q); internet bo'lsin."))
        } else {
            out.append(Step(ok: token != nil, title: token != nil ? "iPhone push tokeni olingan" : "iPhone push tokeni hali yo'q",
                            advice: token != nil ? "" : "Ilovani qayta oching. Chiqmasa: Xcode → Signing & Capabilities → «Push Notifications» va «Time Sensitive Notifications»ni qo'shing, ilovani qayta o'rnating."))
        }
        let reg = ud.string(forKey: "apns-server")
        if let reg, reg != "ok" {
            out.append(Step(ok: false, title: "Token serverga yuborilmadi", advice: "\(reg). Server yangilanganmi (POST /api/panel/devices)? Chiqib qayta kirib ko'ring."))
        }
        do {
            let r = try await model.api.devices()
            out.append(Step(ok: r.providers.ios, title: r.providers.ios ? "Serverda iOS push (APNs) sozlangan" : "Serverda APNs SOZLANMAGAN",
                            advice: r.providers.ios ? "" : "Server .env: APNS_KEY_BASE64, APNS_KEY_ID, APNS_TEAM_ID, APNS_BUNDLE_ID (=uz.lokmago.restaurant), APNS_PRODUCTION. So'ng serverni qayta ishga tushiring."))
            let tail = String((token ?? "").suffix(6))
            let mine = r.devices.contains { $0.platform == "ios" && !tail.isEmpty && $0.token.hasSuffix(tail) }
            out.append(Step(ok: mine, title: mine ? "Bu iPhone serverda ro'yxatda" : "Bu iPhone serverda ro'yxatda YO'Q",
                            advice: mine ? "" : "Chiqib qayta kiring. Server ro'yxatida \(r.devices.count) ta qurilma bor."))
        } catch {
            var notFound = false
            if let api = error as? APIError, case .http(404, _) = api { notFound = true }
            out.append(Step(ok: false, title: notFound ? "Server eski: push endpointi yo'q" : "Server bilan bog'lanib bo'lmadi",
                            advice: notFound ? "lakmago-serverni yangilab (git pull, pm2 restart) qayta ishga tushiring." : error.localizedDescription))
        }
        steps = out
    }

    private func sendTest() {
        Task {
            testing = true; defer { testing = false }
            do {
                let r = try await model.api.testPush()
                if r.results.isEmpty {
                    testLines = [Step(ok: false, title: "Serverda hech qanday qurilma yo'q", advice: "Avval yuqoridagi qadamlarni tuzating.")]
                } else {
                    testLines = r.results.map { x in
                        Step(ok: x.ok, title: x.ok ? "Test yuborildi (\(x.platform) \(x.token))" : "Test yetmadi (\(x.platform) \(x.token))", advice: x.ok ? "Telefonga xabar kelishi kerak." : Self.explain(x.error ?? ""))
                    }
                }
            } catch { testLines = [Step(ok: false, title: "Test yuborilmadi", advice: error.localizedDescription)] }
        }
    }

    /// Apple/Google xatosi → nima qilish kerak (oddiy tilda)
    static func explain(_ e: String) -> String {
        if e.contains("BadDeviceToken") { return "\(e). Xcode'dan o'rnatilgan build uchun serverda APNS_PRODUCTION=false, TestFlight/App Store uchun true bo'lishi kerak." }
        if e.contains("DeviceTokenNotForTopic") { return "\(e). Serverdagi APNS_BUNDLE_ID ilovaning Bundle ID'siga (uz.lokmago.restaurant) mos emas." }
        if e.contains("InvalidProviderToken") || e.contains("403") { return "\(e). APNs kaliti noto'g'ri: APNS_KEY_ID, APNS_TEAM_ID yoki .p8 faylni tekshiring." }
        if e.contains("TopicDisallowed") { return "\(e). Bu .p8 kalit shu ilova uchun yoqilmagan (Apple Developer → Keys → APNs)." }
        if e.contains("sozlanmagan") { return "\(e). Server .env to'ldirilmagan." }
        return e
    }
}
