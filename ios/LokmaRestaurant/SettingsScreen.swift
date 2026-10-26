import SwiftUI
import UIKit

struct SettingsScreen: View {
    var openReport: () -> Void = {}
    var openReviews: () -> Void = {}
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("sound") private var sound = true
    @AppStorage("vibration") private var vibration = true
    @AppStorage("notifications") private var notifications = true
    @AppStorage("volume") private var volume = 2
    @AppStorage("autoRefresh") private var autoRefresh = 10
    @State private var checks: [ReadinessRow] = []
    private let volumeNames = ["Past", "O'rta", "Baland"]
    private let refreshOptions = [0, 5, 10, 30]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Dt.spaceGap) {
                Text("Sozlamalar").font(.system(size: 26, weight: .heavy)).padding(.top, 8)

                group("Biznes") {
                    row("ic_report", "Hisobot", "Savdo va grafiklar", action: openReport)
                    row("ic_message", "Mijozlar izohlari", "Baho va fikrlar", action: openReviews)
                }

                group("Buyurtmalar") {
                    toggle("ic_volume", "Buyurtma ovozi", $sound)
                    toggle("ic_vibrate", "Vibratsiya", $vibration)
                    toggle("ic_bell", "Bildirishnoma", $notifications)
                    row("ic_refresh", "Avto yangilash", autoRefresh == 0 ? "O'chiq" : "\(autoRefresh) soniya") {
                        autoRefresh = refreshOptions[((refreshOptions.firstIndex(of: autoRefresh) ?? 0) + 1) % refreshOptions.count]
                    }
                    row("ic_volume", "Ovozni sozlash", volumeNames[min(max(volume, 0), 2)]) { volume = (volume + 1) % 3 }
                }

                // "Will a new order really wake THIS phone?" — each line says why it matters and opens the system setting.
                let missing = checks.filter { !$0.ok }.count
                group(missing == 0 ? "Ogohlantirish tayyorligi  ✓" : "Ogohlantirish tayyorligi  (\(missing) ta sozlanmagan)") {
                    ForEach(checks) { c in
                        HStack(spacing: 12) {
                            LgIcon(name: c.ok ? "ic_check" : "ic_warning", color: c.ok ? Dt.greenText : Dt.orange, size: 16)
                                .frame(width: 28, height: 28).background((c.ok ? Dt.green : Dt.orange).opacity(0.18), in: Circle())
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.title).font(.system(size: 14, weight: .semibold))
                                Text(c.hint).font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                            }
                            Spacer()
                            if !c.ok {
                                Button("Sozlash") { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
                                    .font(.system(size: 12, weight: .bold)).foregroundStyle(Dt.onAccent)
                                    .padding(.horizontal, 12).padding(.vertical, 7).background(Dt.orange, in: RoundedRectangle(cornerRadius: 10))
                            }
                        }.padding(.vertical, 10)
                    }
                }

                group("Hisob") {
                    row("ic_user", "Login", UserDefaults.standard.string(forKey: "last-login") ?? "") {}
                    row("ic_store", "Restoran nomi", model.restaurant?.name ?? "") {}
                    Button { Task { await model.logout() } } label: {
                        HStack(spacing: 12) {
                            LgIcon(name: "ic_logout", color: Dt.red)
                            Text("Chiqish").font(.system(size: 15, weight: .semibold)).foregroundStyle(Dt.red)
                            Spacer()
                        }.padding(.vertical, 12)
                    }.buttonStyle(.plain)
                }

                group("Ilova") {
                    row("ic_download", "Versiya", "v" + (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"), chevron: false) {}
                    row("ic_help", "Yordam / Support", "") {}
                    row("ic_shield", "Maxfiylik siyosati", "") {}
                    row("ic_document", "Foydalanish shartlari", "") {}
                }
            }
            .padding(Dt.spaceScreen)
        }
        .task { checks = await AlertCenter.shared.readiness() }
        .onChange(of: scenePhase) { _, p in if p == .active { Task { checks = await AlertCenter.shared.readiness() } } }   // back from system settings
    }

    private func group<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: Dt.spaceGap) {
            Text(title).font(.system(size: 15, weight: .bold)).padding(.top, 6)
            SectionCard { content() }
        }
    }

    private func toggle(_ icon: String, _ label: String, _ value: Binding<Bool>) -> some View {
        HStack(spacing: 12) {
            LgIcon(name: icon, color: Dt.textSecondary)
            Text(label).font(.system(size: 15))
            Spacer()
            Toggle("", isOn: value).labelsHidden().tint(Dt.green)
        }.padding(.vertical, 6)
    }

    private func row(_ icon: String, _ label: String, _ value: String, chevron: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                LgIcon(name: icon, color: Dt.textSecondary)
                Text(label).font(.system(size: 15))
                Spacer()
                if !value.isEmpty { Text(value).font(.system(size: 14)).foregroundStyle(Dt.textMuted) }
                if chevron { LgIcon(name: "ic_chevron_right", color: Dt.textMuted, size: 16) }
            }.padding(.vertical, 12).contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}
