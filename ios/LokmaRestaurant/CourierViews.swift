import SwiftUI
import CoreLocation
import UIKit

/// BITTA ilova, IKKI tomon: birinchi ochilganda «Restoran boshqaruvi» yoki «Kuryer» tanlanadi; tanlangani bilan login/parol so'raladi va
/// o'sha bo'limga kiriladi (alohida akkaunt, alohida token, alohida ekranlar). Tanlov eslab qolinadi; login ekranidan o'zgartiriladi.
struct EntryView: View {
    @AppStorage("app-role") private var role = ""
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var courier: CourierModel

    var body: some View {
        Group {
            switch role {
            case "courier": if courier.isSignedIn { CourierRootView() } else { CourierLoginView { role = "" } }
            case "restaurant": RootView(switchSide: { role = "" })
            default: RoleChooserView { role = $0 }
            }
        }
        // Existing installs that were already signed in as a restaurant skip the chooser
        .onAppear { if role.isEmpty && model.isSignedIn { role = "restaurant" } }
    }
}

struct RoleChooserView: View {
    let pick: (String) -> Void
    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Image("logo_lgo").resizable().scaledToFit().frame(width: 110, height: 110).clipShape(RoundedRectangle(cornerRadius: 26))
            Text("LGO Business").font(.system(size: 28, weight: .heavy))
            Text("Qaysi tomon bilan ishlaysiz?").font(.system(size: 14)).foregroundStyle(Dt.textMuted).padding(.bottom, 18)
            card("ic_store", "Restoran boshqaruvi", "Buyurtmalar, menyu, stop list, hisobot", Dt.green) { pick("restaurant") }
            card("ic_delivery", "Kuryer", "Smena, yetkazish takliflari, tarix", Dt.orange) { pick("courier") }
            Spacer()
        }
        .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity).lgScreen().preferredColorScheme(.dark)
    }
    private func card(_ icon: String, _ title: String, _ hint: String, _ accent: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                LgIcon(name: icon, color: accent, size: 30).frame(width: 56, height: 56).background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 19, weight: .bold)).foregroundStyle(Dt.text)
                    Text(hint).font(.system(size: 13)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                LgIcon(name: "ic_chevron_right", color: Dt.textMuted, size: 22)
            }
            .padding(20).background(Dt.card, in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(accent.opacity(0.6), lineWidth: 1.5))
        }.buttonStyle(.plain)
    }
}

struct CourierLoginView: View {
    let switchSide: () -> Void
    @EnvironmentObject private var courier: CourierModel
    @State private var login = UserDefaults.standard.string(forKey: "courier-login") ?? ""
    @State private var password = ""
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Image("logo_lgo").resizable().scaledToFit().frame(width: 96, height: 96).clipShape(RoundedRectangle(cornerRadius: 22))
            Text("Kuryer").font(.system(size: 26, weight: .heavy))
            Text("Kuryer akkaunti bilan kiring").font(.system(size: 13)).foregroundStyle(Dt.textMuted).padding(.bottom, 12)
            field("Login") { TextField("", text: $login).textInputAutocapitalization(.never).autocorrectionDisabled() }
            field("Parol") { SecureField("", text: $password) }
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(Dt.red) }
            PrimaryButton(title: "Kirish", loading: loading) { Task { loading = true; error = await courier.signIn(login: login, password: password); loading = false } }.padding(.top, 8)
            Button("Restoran boshqaruviga o'tish", action: switchSide).font(.system(size: 14, weight: .semibold)).foregroundStyle(Dt.greenText).padding(16)
            Spacer()
        }
        .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity).lgScreen().preferredColorScheme(.dark)
    }
    private func field<C: View>(_ label: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.system(size: 11)).foregroundStyle(Dt.textMuted); content().font(.system(size: 16)).foregroundStyle(Dt.text)
        }
        .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 56).background(Dt.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Dt.line, lineWidth: 1))
    }
}

struct CourierRootView: View {
    @EnvironmentObject private var courier: CourierModel
    @AppStorage("app-role") private var role = ""
    @State private var tab = 0

    var body: some View {
        Group {
            switch tab {
            case 0: WorkTab()
            case 1: HistoryTab()
            case 2: StatsTab()
            default: ProfileTab(switchSide: { role = "" })
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .safeAreaInset(edge: .bottom, spacing: 0) { CourierTabBar(tab: $tab, offers: courier.offers.count) }
        .lgScreen().preferredColorScheme(.dark)
        .task { courier.start() }
        .onChange(of: tab) { _, t in Task { if t == 1 { await courier.loadHistory() }; if t == 2 { await courier.loadStats() } } }
    }
}

struct CourierTabBar: View {
    @Binding var tab: Int
    let offers: Int
    private let items: [(String, String)] = [("ic_delivery", "Ish"), ("ic_orders", "Tarix"), ("ic_report", "Hisobot"), ("ic_profile", "Profil")]
    var body: some View {
        HStack(spacing: 4) {
            ForEach(items.indices, id: \.self) { i in
                Button { if tab != i { UIImpactFeedbackGenerator(style: .light).impactOccurred() }; tab = i } label: {
                    ZStack(alignment: .topTrailing) {
                        LgIcon(name: items[i].0, color: tab == i ? Dt.green : Dt.textSecondary, size: 26)
                        if i == 0 && offers > 0 {
                            Text("\(offers)").font(.system(size: 10, weight: .bold)).foregroundStyle(Dt.onAccent).padding(.horizontal, 5).padding(.vertical, 1).background(Dt.red, in: Capsule()).offset(x: 12, y: -8)
                        }
                    }
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background { if tab == i { Capsule().fill(Color.white.opacity(0.14)) } }
                }.buttonStyle(.plain).accessibilityLabel(Text(items[i].1))
            }
        }
        .padding(6).modifier(GlassCapsule()).padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 6)
    }
}

private func km(_ m: Int?) -> String { guard let m else { return "" }; return m < 950 ? "\(m / 10 * 10) m" : String(format: "%.1f km", Double(m) / 1000) }
private func minutes(_ n: Int) -> String { n >= 60 ? "\(n / 60) soat \(n % 60) daq" : "\(n) daq" }

struct WorkTab: View {
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(courier.name.isEmpty ? "Kuryer" : courier.name).font(.system(size: 24, weight: .heavy))
                        Text(courier.onShift ? "Smena ochiq" : "Smena yopiq").font(.system(size: 13, weight: .semibold)).foregroundStyle(courier.onShift ? Dt.greenText : Dt.textMuted)
                    }
                    Spacer(); Circle().fill(courier.onShift ? Dt.green : Dt.textMuted).frame(width: 12, height: 12)
                }
                if let m = courier.message {
                    Text(m).font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.orange).frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12).background(Dt.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                }
                // «Ishni boshladim / yakunladim» — kuryer har kuni bosadi; ochiq smenada joylashuv har 5 s yuboriladi
                if !courier.onShift {
                    PrimaryButton(title: "Ishni boshladim", icon: "ic_check_circle", loading: courier.busy) { Task { await courier.beginShift() } }
                } else {
                    if let s = courier.shift {
                        SectionCard(bg: Dt.cardStat) {
                            HStack { Text("Bugungi smena").font(.system(size: 12)).foregroundStyle(Dt.textSecondary); Spacer()
                                Text("\(s.deliveries ?? 0) ta yetkazish • \(km(s.distanceM))").font(.system(size: 13, weight: .semibold)) }
                        }
                    }
                    Button { Task { await courier.endShift() } } label: {
                        Text(courier.active != nil ? "Avval buyurtmani topshiring" : "Ishni yakunladim").font(.system(size: 16, weight: .bold))
                            .foregroundStyle(courier.active != nil ? Dt.textMuted : Dt.red).frame(maxWidth: .infinity).padding(16)
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(courier.active != nil ? Dt.line : Dt.red, lineWidth: 1.5))
                    }.buttonStyle(.plain).disabled(courier.active != nil || courier.busy)
                }
                if courier.locationDenied { Text("Joylashuv ruxsatisiz smena ishlamaydi: buyurtma sizga yaqinlik bo'yicha beriladi. Sozlamalar → LGO Business → Joylashuv → «Ilova ishlatilganda»").font(.system(size: 12)).foregroundStyle(Dt.orange) }

                if let a = courier.active { ActiveCard(a: a) }
                else if courier.onShift {
                    Text("Yaqin atrofdagi buyurtmalar").font(.system(size: 13, weight: .bold)).foregroundStyle(Dt.textSecondary)
                    if courier.offers.isEmpty {
                        VStack(spacing: 8) { LgIcon(name: "ic_delivery", color: Dt.textMuted, size: 40); Text("Hozircha buyurtma yo'q").font(.system(size: 15, weight: .semibold))
                            Text("Yangi yetkazish chiqqanda bildirishnoma va ovoz bilan xabar beramiz").font(.system(size: 13)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center) }
                            .frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                    ForEach(courier.offers) { o in OfferCard(o: o) }
                } else {
                    VStack(spacing: 8) { LgIcon(name: "ic_delivery", color: Dt.textMuted, size: 40); Text("Ish boshlang").font(.system(size: 15, weight: .semibold))
                        Text("«Ishni boshladim»ni bosing — yaqin atrofdagi buyurtmalar shu yerda chiqadi").font(.system(size: 13)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center) }
                        .frame(maxWidth: .infinity).padding(.vertical, 40)
                }
            }.padding(Dt.spaceScreen).padding(.bottom, 12)
        }
        .refreshable { await courier.refresh() }
    }
}

struct OfferCard: View {
    let o: COffer
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        SectionCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(o.restaurantName ?? "Restoran").font(.system(size: 17, weight: .bold))
                    if let a = o.restaurantAddress, !a.isEmpty { Text(a).font(.system(size: 12)).foregroundStyle(Dt.textMuted) }
                }
                Spacer(); if let d = o.distanceM { Text(km(d)).font(.system(size: 15, weight: .heavy)).foregroundStyle(Dt.greenText) }
            }
            if let l = o.addressLabel, !l.isEmpty { Text("📍 \(l)").font(.system(size: 13)).foregroundStyle(Dt.textSecondary).padding(.top, 8) }
            if let s = o.itemsSummary, !s.isEmpty { Text(s).font(.system(size: 12)).foregroundStyle(Dt.textMuted) }
            HStack(spacing: 10) {
                Text("Haq: \(som(o.deliveryFee ?? 0))\(o.freeDelivery == true ? " • mijozga tekin" : "")").font(.system(size: 14, weight: .bold)).foregroundStyle(Dt.orange)
                Text(o.isPaid == true ? "To'langan" : "Olinadi: \(som(o.collectAmount ?? 0))").font(.system(size: 13)).foregroundStyle(o.isPaid == true ? Dt.greenText : Dt.text)
            }.padding(.top, 8)
            PrimaryButton(title: "Qabul qilish", icon: "ic_check", loading: courier.busy) { Task { await courier.accept(o.id) } }.padding(.top, 12)
        }
    }
}

struct ActiveCard: View {
    let a: CActive
    @EnvironmentObject private var courier: CourierModel
    private func open(_ lat: Double?, _ lng: Double?, _ label: String) {
        guard let lat, let lng, let url = URL(string: "http://maps.apple.com/?ll=\(lat),\(lng)&q=\(label.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")") else { return }
        UIApplication.shared.open(url)
    }
    private func call(_ phone: String?) { if let p = phone?.filter({ $0.isNumber || $0 == "+" }), !p.isEmpty, let url = URL(string: "tel:\(p)") { UIApplication.shared.open(url) } }
    private func chip(_ title: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) { LgIcon(name: icon, color: Dt.greenText, size: 18); Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.greenText) }
                .padding(.horizontal, 12).padding(.vertical, 8).background(Dt.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }
    var body: some View {
        SectionCard(bg: Dt.cardStat) {
            Text("FAOL BUYURTMA  #\(a.orderCode ?? "")").font(.system(size: 12, weight: .heavy)).foregroundStyle(Dt.orange).tracking(1)
            // Bosqich: restoranga boryapman (taom hali olinmagan) / taom qo'lida, mijozga boryapman
            if a.canPickup == true { Text("📍 Restoranga boring va taomni oling").font(.system(size: 14, weight: .bold)).foregroundStyle(Dt.orange).padding(.top, 6) }
            else if a.stage == "to_customer" { Text("🛵 Taom qo'lingizda — mijozga olib boring").font(.system(size: 14, weight: .bold)).foregroundStyle(Dt.greenText).padding(.top, 6) }
            Text("1. Restoran").font(.system(size: 12)).foregroundStyle(Dt.textSecondary).padding(.top, 10)
            Text(a.restaurantName ?? "").font(.system(size: 17, weight: .bold)); Text(a.restaurantAddress ?? "").font(.system(size: 13)).foregroundStyle(Dt.textMuted)
            HStack(spacing: 8) { chip("Xaritada", "ic_map") { open(a.restaurantLat, a.restaurantLng, a.restaurantName ?? "Restoran") }
                if let p = a.restaurantPhone, !p.isEmpty { chip("Qo'ng'iroq", "ic_phone") { call(p) } } }.padding(.top, 6)
            Text("2. Mijoz").font(.system(size: 12)).foregroundStyle(Dt.textSecondary).padding(.top, 14)
            Text((a.customerName ?? "").isEmpty ? "Mijoz" : a.customerName!).font(.system(size: 17, weight: .bold))
            Text([a.addressLabel, a.addressNote].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")).font(.system(size: 13)).foregroundStyle(Dt.textMuted)
            HStack(spacing: 8) { chip("Xaritada", "ic_map") { open(a.lat, a.lng, "Mijoz") }
                if let p = a.customerPhone, !p.isEmpty { chip("Qo'ng'iroq", "ic_phone") { call(p) } } }.padding(.top, 6)
            ForEach(Array((a.items ?? []).enumerated()), id: \.offset) { _, i in Text("• \(i.quantity ?? 0)× \(i.name ?? "")").font(.system(size: 14)) }
            Text(a.isPaid == true ? "✅ To'langan — mijozdan pul olinmaydi" : "💵 Mijozdan olinadi: \(som(a.collectAmount ?? 0))").font(.system(size: 15, weight: .bold))
                .foregroundStyle(a.isPaid == true ? Dt.greenText : Dt.text).padding(.top, 12)
            Text(a.freeDelivery == true ? "Sizning haqingiz: \(som(a.deliveryFee ?? 0)) (mijozga yetkazish tekin)" : "Sizning haqingiz: \(som(a.deliveryFee ?? 0))").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
            // Ikki qadam: avval «Taomni oldim», keyin «Topshirdim» (havola oqimida faqat «Topshirdim»)
            if a.canPickup == true { PrimaryButton(title: "Taomni oldim", icon: "ic_check_circle", loading: courier.busy) { Task { await courier.pickup() } }.padding(.top, 14) }
            else { PrimaryButton(title: "Topshirdim", icon: "ic_check_circle", loading: courier.busy) { Task { await courier.deliver() } }.padding(.top, 14) }
        }
    }
}

struct HistoryTab: View {
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                Text("Tarix").font(.system(size: 24, weight: .heavy))
                if courier.history.isEmpty { Text("Hali yetkazish yo'q. Topshirgan buyurtmalaringiz shu yerda ko'rinadi.").font(.system(size: 13)).foregroundStyle(Dt.textMuted).padding(.vertical, 30) }
                ForEach(courier.history) { h in
                    SectionCard {
                        HStack { Text("#\(h.orderCode ?? "") • \(h.restaurantName ?? "")").font(.system(size: 15, weight: .bold)); Spacer(); Text(som(h.deliveryFee ?? 0)).font(.system(size: 14, weight: .bold)).foregroundStyle(Dt.orange) }
                        Text([h.addressLabel, h.minutes.map { "\($0) daq" }, h.deliveredAt.map { String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") }].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • "))
                            .font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                    }
                }
            }.padding(Dt.spaceScreen).padding(.bottom, 12)
        }
        .refreshable { await courier.loadHistory() }
    }
}

struct StatsTab: View {
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Hisobot").font(.system(size: 24, weight: .heavy))
                if let s = courier.stats {
                    ForEach([("Bugun", s.today), ("Oxirgi 7 kun", s.week), ("Oxirgi 30 kun", s.month)], id: \.0) { title, p in
                        SectionCard(bg: Dt.cardStat) {
                            Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(Dt.textSecondary)
                            HStack(alignment: .lastTextBaseline) { Text("\(p?.deliveries ?? 0)").font(.system(size: 34, weight: .heavy)); Text("yetkazish").font(.system(size: 13)).foregroundStyle(Dt.textMuted) }
                            Text("Yetkazish haqi: \(som(p?.deliveryFees ?? 0))").font(.system(size: 14, weight: .semibold)).foregroundStyle(Dt.orange)
                            Text("Ish vaqti: \(minutes(p?.workedMinutes ?? 0)) • \(p?.shifts ?? 0) smena • \(p?.distanceKm ?? 0) km").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                        }
                    }
                } else { ProgressView().tint(Dt.green).frame(maxWidth: .infinity).padding(.top, 30) }
            }.padding(Dt.spaceScreen).padding(.bottom, 12)
        }
        .refreshable { await courier.loadStats() }
    }
}

struct ProfileTab: View {
    let switchSide: () -> Void
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Profil").font(.system(size: 24, weight: .heavy))
                SectionCard { Text(courier.name).font(.system(size: 18, weight: .bold)); Text("Login: \(courier.login)").font(.system(size: 13)).foregroundStyle(Dt.textMuted) }
                if let m = courier.message { Text(m).font(.system(size: 13)).foregroundStyle(Dt.orange) }
                PrimaryButton(title: "Push tekshiruvi (test xabar)", icon: "ic_bell_ring", color: Dt.orange, loading: courier.busy) { Task { await courier.testPush() } }
                Text("Test bosgach ilovani yoping va telefonni bloklang — «Push ishlayapti ✓» kelishi kerak.").font(.system(size: 11)).foregroundStyle(Dt.textMuted)
                Button(action: switchSide) {
                    Text(courier.onShift ? "Chiqishdan oldin ishni yakunlang" : "Restoran / Kuryer tanlashga qaytish").font(.system(size: 15, weight: .semibold)).foregroundStyle(Dt.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(16).background(Dt.card, in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain).disabled(courier.onShift)
                Button { Task { await courier.signOut() } } label: {
                    Text("Chiqish").font(.system(size: 15, weight: .bold)).foregroundStyle(courier.onShift ? Dt.textMuted : Dt.red).frame(maxWidth: .infinity).padding(16)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(courier.onShift ? Dt.line : Dt.red, lineWidth: 1.5))
                }.buttonStyle(.plain).disabled(courier.onShift)
            }.padding(Dt.spaceScreen).padding(.bottom, 12)
        }
    }
}
