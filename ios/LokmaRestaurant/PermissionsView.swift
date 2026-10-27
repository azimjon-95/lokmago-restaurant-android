import SwiftUI
import UIKit

/// Permission walk-through: shown once after the first login (and from Profil / the Home banner). Every row says WHY it is
/// needed. iOS lets an app ask for notification permission exactly once; everything else can only be switched on by the
/// user in Settings — so this screen explains, asks what can be asked, and opens Settings for the rest. Nothing is forced.
struct PermissionsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var rows: [ReadinessRow] = []
    @State private var working = false

    private var allOk: Bool { !rows.isEmpty && rows.allSatisfy(\.ok) }
    private var authMissing: Bool { rows.first { $0.id == "auth" }?.ok == false }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    LgIcon(name: "ic_bell_ring", color: Dt.orange, size: 40).padding(.top, 8)
                    Text("Buyurtma o'tkazib yuborilmasin").font(.system(size: 24, weight: .heavy))
                    Text("Telefon bloklangan yoki ilova yopiq bo'lsa ham yangi buyurtma ovoz bilan xabar berishi uchun quyidagi sozlamalar yoqilgan bo'lishi kerak.")
                        .font(.system(size: 14)).foregroundStyle(Dt.textSecondary)

                    ForEach(rows) { r in
                        HStack(alignment: .top, spacing: 12) {
                            LgIcon(name: r.ok ? "ic_check" : "ic_warning", color: r.ok ? Dt.greenText : Dt.orange, size: 16)
                                .frame(width: 30, height: 30).background((r.ok ? Dt.green : Dt.orange).opacity(0.18), in: Circle())
                            VStack(alignment: .leading, spacing: 3) {
                                Text(r.title).font(.system(size: 15, weight: .semibold))
                                Text(r.hint).font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14).background(Dt.card, in: RoundedRectangle(cornerRadius: 16))
                    }

                    Text("Ruxsatlarni majburan olib bo'lmaydi — ularni faqat siz yoqasiz. Yoqmasangiz ilova ochiq paytda ishlayveradi, lekin yopiq yoki bloklangan telefonda yangi buyurtma haqida xabar kelmaydi.")
                        .font(.system(size: 12)).foregroundStyle(Dt.textMuted).padding(.top, 4)
                }
                .padding(20)
            }
            VStack(spacing: 6) {
                PrimaryButton(title: allOk ? "Tayyor" : (authMissing ? "Bildirishnomaga ruxsat berish" : "Sozlamalarni ochish"),
                              icon: allOk ? "ic_check" : nil, color: allOk ? Dt.green : Dt.orange, loading: working, action: primary)
                if !allOk { Button("Keyinroq") { model.permissionsFinished() }.font(.system(size: 14)).foregroundStyle(Dt.textMuted).padding(8) }
            }
            .padding(.horizontal, 16).padding(.bottom, 8)
        }
        .lgScreen().preferredColorScheme(.dark)
        .task { await reload() }
        .onChange(of: scenePhase) { _, p in if p == .active { Task { await reload() } } }   // back from system Settings
    }

    private func reload() async { rows = await AlertCenter.shared.readiness() }

    private func primary() {
        if allOk { model.permissionsFinished(); return }
        Task {
            working = true; defer { working = false }
            if await AlertCenter.shared.authorizationStatus() == .notDetermined {
                _ = await AlertCenter.shared.requestAuthorization()     // the one system dialog iOS allows
                await reload()
            } else if let url = URL(string: UIApplication.openSettingsURLString) {
                await UIApplication.shared.open(url)
            }
        }
    }
}
