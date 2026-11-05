import SwiftUI
import CoreLocation
import UIKit

/// BITTA ilova, IKKI tomon: birinchi ochilganda va HAR CHIQISHDAN KEYIN «Restoran boshqaruvi» yoki «Kuryer» tanlanadi; tanlangani bilan login/parol so'raladi.
/// Moslashuvchan: iPhone'da o'zgarishsiz; iPad / landshaft'da kontent markazdagi ustun (640 pt), cho'zilgan emas.
struct EntryView: View {
    @AppStorage("app-role") private var role = ""
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var courier: CourierModel

    private var courierSide: Bool { role == "courier" }

    var body: some View {
        ZStack {
            (courierSide ? Cr.surface : Dt.bg).ignoresSafeArea()
            Group {
                switch role {
                case "courier": if courier.isSignedIn { CourierRootView() } else { CourierLoginView { role = "" } }
                case "restaurant": RootView(switchSide: { role = "" })
                default: RoleChooserView { role = $0 }
                }
            }
            .frame(maxWidth: 640)
        }
        // Existing installs that were already signed in as a restaurant skip the chooser
        .onAppear { if role.isEmpty && model.isSignedIn { role = "restaurant" } }
        // Any touch = "the user is here": the order alarm stays a whisper (AlertAttention)
        .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in AlertCenter.shared.touched() })
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

// ───────────────────────── KURYER: yorug' to'q sariq dizayn ─────────────────────────
enum Cr {
    static let orange = Color(red: 1.0, green: 0.42, blue: 0.0), orangeDark = Color(red: 0.91, green: 0.36, blue: 0.0), orangeSoft = Color(red: 1.0, green: 0.945, blue: 0.90)
    static let surface = Color(red: 0.965, green: 0.965, blue: 0.973), line = Color(red: 0.91, green: 0.91, blue: 0.925)
    static let text = Color(red: 0.086, green: 0.094, blue: 0.114), muted = Color(red: 0.42, green: 0.447, blue: 0.5)
    static let green = Color(red: 0.086, green: 0.639, blue: 0.29), greenSoft = Color(red: 0.91, green: 0.969, blue: 0.933), red = Color(red: 0.898, green: 0.282, blue: 0.302)
    static let dark = Color(red: 0.078, green: 0.09, blue: 0.11)
}

struct CrCard<Content: View>: View {
    var bg: Color = .white
    var border = true
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content).padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(bg, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(border ? Cr.line : .clear, lineWidth: 1))
    }
}

struct CrButton: View {
    let title: String
    var filled = true
    var danger = false
    var loading = false
    var enabled = true
    let action: () -> Void
    var body: some View {
        let tint = danger ? Cr.red : Cr.orange
        Button(action: action) {
            Group { if loading { ProgressView().tint(filled ? .white : tint) } else { Text(title).font(.system(size: 16, weight: .bold)) } }
                .foregroundStyle(filled ? Color.white : tint).frame(maxWidth: .infinity, minHeight: 54)
                .background(filled ? tint : .clear, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(filled ? .clear : tint, lineWidth: 1.5))
                .opacity(enabled ? 1 : 0.4)
        }.buttonStyle(.plain).disabled(!enabled || loading)
    }
}

struct CrSegment: View {
    let options: [String]
    @Binding var selected: Int
    var body: some View {
        HStack(spacing: 0) {
            ForEach(options.indices, id: \.self) { i in
                Button { selected = i } label: {
                    Text(options[i]).font(.system(size: 14, weight: .semibold)).foregroundStyle(i == selected ? .white : Cr.muted)
                        .frame(maxWidth: .infinity).padding(.vertical, 10).background(i == selected ? Cr.orange : .clear, in: RoundedRectangle(cornerRadius: 11))
                }.buttonStyle(.plain)
            }
        }.padding(4).background(Color(red: 0.93, green: 0.93, blue: 0.945), in: RoundedRectangle(cornerRadius: 14))
    }
}

private func km(_ m: Int?) -> String { guard let m else { return "" }; return m < 950 ? "\(m / 10 * 10) m" : String(format: "%.1f km", Double(m) / 1000) }
private func minutes(_ n: Int) -> String { n >= 60 ? "\(n / 60) soat \(n % 60) daq" : "\(n) daq" }
private func initials(_ name: String) -> String { let r = name.split(separator: " ").prefix(2).compactMap { $0.first }.map { String($0).uppercased() }.joined(); return r.isEmpty ? "K" : r }

/// Kuryer kirishi: FAQAT login va parol (telefon raqamni SMS bilan tasdiqlash YO'Q). Akkauntni administrator yaratadi.
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
            Image("logo_lgo").resizable().scaledToFit().frame(width: 88, height: 88).clipShape(RoundedRectangle(cornerRadius: 22))
            Text("Kirish").font(.system(size: 28, weight: .heavy)).foregroundStyle(Cr.text)
            Text("Kuryer akkauntingiz login va parolini kiriting").font(.system(size: 14)).foregroundStyle(Cr.muted).multilineTextAlignment(.center).padding(.bottom, 14)
            field("Login") { TextField("", text: $login).textInputAutocapitalization(.never).autocorrectionDisabled() }
            field("Parol") { SecureField("", text: $password) }
            if let error { Text(error).font(.system(size: 13)).foregroundStyle(Cr.red) }
            CrButton(title: "Davom etish", loading: loading) { Task { loading = true; error = await courier.signIn(login: login, password: password); loading = false } }.padding(.top, 8)
            Button("Restoran boshqaruviga o'tish", action: switchSide).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cr.orange).padding(16)
            Spacer()
        }
        .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.white).preferredColorScheme(.light)
    }
    private func field<C: View>(_ label: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.system(size: 11)).foregroundStyle(Cr.muted); content().font(.system(size: 16)).foregroundStyle(Cr.text)
        }
        .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 56).background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cr.line, lineWidth: 1))
    }
}

struct CourierRootView: View {
    @EnvironmentObject private var courier: CourierModel
    @State private var tab = 0
    @State private var skipped: Set<String> = []          // «Rad etish» — faqat shu telefonda ko'rsatmaslik

    private var offer: COffer? { (courier.onShift && courier.active == nil && tab != 3) ? courier.offers.first { !skipped.contains($0.id) } : nil }

    var body: some View {
        Group {
            switch tab {
            case 0: HomeTab(openEarnings: { tab = 2 })
            case 1: OrdersTab()
            case 2: EarningsTab()
            default: ProfileTab()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(Cr.surface)
        .safeAreaInset(edge: .bottom, spacing: 0) { CourierTabBar(tab: $tab, badge: courier.offers.count + (courier.active != nil ? 1 : 0)) }
        .fullScreenCover(isPresented: Binding(get: { offer != nil }, set: { _ in })) {
            if let o = offer { OfferModalView(o: o, onAccept: { Task { await courier.accept(o.id) }; tab = 1 }, onReject: { skipped.insert(o.id) }, onExpire: { skipped.insert(o.id) }) }
        }
        .preferredColorScheme(.light)
        .task { courier.start(); await courier.loadStats() }
        .onChange(of: tab) { _, t in Task { if t == 1 { await courier.loadHistory() }; if t == 2 { await courier.loadStats(); await courier.loadHistory() } } }
    }
}

struct CourierTabBar: View {
    @Binding var tab: Int
    let badge: Int
    private let items: [(String, String)] = [("ic_home", "Bosh sahifa"), ("ic_orders", "Buyurtmalar"), ("ic_report", "Daromad"), ("ic_profile", "Profil")]
    var body: some View {
        HStack(spacing: 0) {
            ForEach(items.indices, id: \.self) { i in
                Button { if tab != i { UIImpactFeedbackGenerator(style: .light).impactOccurred() }; tab = i } label: {
                    VStack(spacing: 3) {
                        ZStack(alignment: .topTrailing) {
                            LgIcon(name: items[i].0, color: tab == i ? Cr.orange : Cr.muted, size: 26)
                            if i == 1 && badge > 0 { Text("\(badge)").font(.system(size: 10, weight: .bold)).foregroundStyle(.white).padding(.horizontal, 5).padding(.vertical, 1).background(Cr.red, in: Capsule()).offset(x: 10, y: -6) }
                        }
                        Text(items[i].1).font(.system(size: 11, weight: tab == i ? .bold : .regular)).foregroundStyle(tab == i ? Cr.orange : Cr.muted).lineLimit(1)
                    }.frame(maxWidth: .infinity)
                }.buttonStyle(.plain)
            }
        }
        .padding(.top, 8).padding(.bottom, 4).background(Color.white.shadow(color: .black.opacity(0.08), radius: 8, y: -2))
    }
}

/// Yangi buyurtma — telefon qo'ng'irog'iga o'xshash to'liq ekran: 30 soniyalik hisoblagich, «Rad etish» / «Qabul qilish».
struct OfferModalView: View {
    let o: COffer
    let onAccept: () -> Void, onReject: () -> Void, onExpire: () -> Void
    @EnvironmentObject private var courier: CourierModel
    @State private var left = 30
    var body: some View {
        VStack(spacing: 18) {
            Text("Yangi buyurtma!").font(.system(size: 22, weight: .heavy)).foregroundStyle(.white).padding(.top, 20)
            ZStack {
                Circle().stroke(Color.white.opacity(0.2), lineWidth: 8)
                Circle().trim(from: 0, to: CGFloat(left) / 30).stroke(Cr.orange, style: StrokeStyle(lineWidth: 8, lineCap: .round)).rotationEffect(.degrees(-90))
                Text("\(left)s").font(.system(size: 24, weight: .heavy)).foregroundStyle(.white)
            }.frame(width: 96, height: 96)
            VStack(alignment: .leading, spacing: 10) {
                Text(o.restaurantName ?? "Restoran").font(.system(size: 18, weight: .heavy)).foregroundStyle(Cr.text)
                stop(Cr.green, "Olish", (o.restaurantAddress ?? "").isEmpty ? (o.restaurantName ?? "") : (o.restaurantAddress ?? ""), km(o.distanceM))
                stop(Cr.orange, "Yetkazish", (o.addressLabel ?? "").isEmpty ? "Mijoz manzili qabul qilgandan keyin ko'rinadi" : (o.addressLabel ?? ""), "")
                if let s = o.itemsSummary, !s.isEmpty { Text(s).font(.system(size: 13)).foregroundStyle(Cr.muted) }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sizning daromadingiz").font(.system(size: 12)).foregroundStyle(Cr.muted)
                    Text(som(o.deliveryFee ?? 0)).font(.system(size: 24, weight: .heavy)).foregroundStyle(Cr.orange)
                    if o.freeDelivery == true { Text("Mijozga yetkazish tekin (LokmaGo to'laydi)").font(.system(size: 12)).foregroundStyle(Cr.muted) }
                    Text(o.isPaid == true ? "To'langan — mijozdan pul olinmaydi" : "Mijozdan olinadi: \(som(o.collectAmount ?? 0))").font(.system(size: 13)).foregroundStyle(Cr.text)
                }.frame(maxWidth: .infinity, alignment: .leading).padding(14).background(Cr.orangeSoft, in: RoundedRectangle(cornerRadius: 14))
            }.padding(18).background(Color.white, in: RoundedRectangle(cornerRadius: 24))
            Spacer()
            HStack(spacing: 12) {
                Button(action: onReject) { Text("Rad etish").font(.system(size: 16, weight: .bold)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 56).background(Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 16)) }.buttonStyle(.plain)
                CrButton(title: "Qabul qilish", loading: courier.busy, action: onAccept).frame(maxWidth: .infinity).layoutPriority(1)
            }
        }
        .padding(20).frame(maxWidth: .infinity, maxHeight: .infinity).background(Cr.dark.ignoresSafeArea()).preferredColorScheme(.dark)
        .task(id: o.id) { left = 30; while left > 0 { try? await Task.sleep(nanoseconds: 1_000_000_000); if Task.isCancelled { return }; left -= 1 }; onExpire() }
    }
    private func stop(_ c: Color, _ title: String, _ address: String, _ trailing: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle().fill(c).frame(width: 12, height: 12).padding(.top, 5)
            VStack(alignment: .leading, spacing: 1) { Text(title).font(.system(size: 15, weight: .bold)).foregroundStyle(Cr.text); Text(address).font(.system(size: 13)).foregroundStyle(Cr.muted) }
            Spacer(minLength: 0)
            if !trailing.isEmpty { Text(trailing).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cr.text) }
        }
    }
}

struct HomeTab: View {
    let openEarnings: () -> Void
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Assalomu alaykum,").font(.system(size: 13)).foregroundStyle(Cr.muted)
                        Text((courier.name.isEmpty ? "Kuryer" : courier.name) + "!").font(.system(size: 24, weight: .heavy)).foregroundStyle(Cr.text).lineLimit(1)
                    }
                    Spacer()
                    Text(initials(courier.name)).font(.system(size: 16, weight: .heavy)).foregroundStyle(Cr.orange).frame(width: 44, height: 44).background(Cr.orangeSoft, in: Circle())
                }
                if let m = courier.message { Text(m).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cr.orangeDark).frame(maxWidth: .infinity, alignment: .leading).padding(12).background(Cr.orangeSoft, in: RoundedRectangle(cornerRadius: 12)) }
                // Onlayn / Oflayn = smena (har kuni «Ishni boshladim / yakunladim»)
                HStack(spacing: 12) {
                    Circle().fill(courier.onShift ? Cr.green : Cr.muted).frame(width: 12, height: 12)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(courier.onShift ? "Onlayn" : "Oflayn").font(.system(size: 17, weight: .heavy)).foregroundStyle(Cr.text)
                        Text(courier.onShift ? "Buyurtmalar qabul qilinmoqda" : "Smena yopiq. Yoqing — buyurtma olasiz").font(.system(size: 12)).foregroundStyle(Cr.muted)
                    }
                    Spacer()
                    Toggle("", isOn: Binding(get: { courier.onShift }, set: { on in Task { if on { await courier.beginShift() } else if courier.active == nil { await courier.endShift() } } }))
                        .labelsHidden().tint(Cr.green).disabled(courier.busy || (courier.onShift && courier.active != nil))
                }
                .padding(16).background(courier.onShift ? Cr.greenSoft : Color.white, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(courier.onShift ? Cr.green.opacity(0.4) : Cr.line, lineWidth: 1))
                if courier.onShift && courier.active != nil { Text("Smenani yakunlash uchun avval buyurtmani topshiring").font(.system(size: 12)).foregroundStyle(Cr.muted) }
                if courier.locationDenied { Text("Joylashuv ruxsatisiz smena ishlamaydi: buyurtma sizga yaqinlik bo'yicha beriladi. Sozlamalar → LGO Business → Joylashuv → «Ilova ishlatilganda».").font(.system(size: 12)).foregroundStyle(Cr.red) }

                DistrictsCard()
                Button(action: openEarnings) {
                    CrCard {
                        Text("Bugungi daromad").font(.system(size: 13)).foregroundStyle(Cr.muted)
                        Text(som(courier.stats?.today?.deliveryFees ?? 0)).font(.system(size: 26, weight: .heavy)).foregroundStyle(Cr.text)
                        Text("\(courier.stats?.today?.deliveries ?? 0) ta yetkazish • \(km(Int((courier.stats?.today?.distanceKm ?? 0) * 1000)))").font(.system(size: 12)).foregroundStyle(Cr.muted)
                    }
                }.buttonStyle(.plain)
                CrCard {
                    let q = courier.gpsAccuracy
                    Text(!courier.onShift ? "GPS: smena yopiq" : (q == nil ? "GPS aniqligi: aniqlanmoqda…" : "GPS aniqligi: ±\(q!) m")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cr.text)
                    Text(!courier.onShift || q == nil ? "Joylashuv smena davomida har 5 soniyada yuboriladi" : (q! <= 15 ? "Joylashuv aniq" : (q! <= 50 ? "Joylashuv yaxshi" : "Joylashuv past aniqlikda — ochiq joyga chiqing")))
                        .font(.system(size: 12)).foregroundStyle(q != nil && q! > 50 ? Cr.red : Cr.green)
                    HStack(spacing: 8) {
                        Circle().fill(!courier.onShift ? Cr.muted : (courier.linkOK == false ? Cr.red : Cr.green)).frame(width: 8, height: 8)
                        Text("Ulanish: " + (!courier.onShift ? "—" : (courier.linkOK == false ? "Uzilgan (qayta uriniladi)" : "Barqaror"))).font(.system(size: 12)).foregroundStyle(Cr.muted)
                    }.padding(.top, 8)
                }
                if let a = courier.active { ActiveCard(a: a) }
                else if courier.onShift {
                    Text("Yaqin atrofdagi buyurtmalar").font(.system(size: 16, weight: .bold)).foregroundStyle(Cr.text)
                    if courier.offers.isEmpty { empty("Hozircha buyurtma yo'q", "Yangi yetkazish chiqqanda bildirishnoma va ovoz bilan xabar beramiz") }
                    ForEach(courier.offers) { o in OfferCard(o: o) }
                } else { empty("Ish boshlang", "«Onlayn»ni yoqing — yaqin atrofdagi buyurtmalar shu yerda chiqadi") }
            }.padding(16).padding(.bottom, 12)
        }
        .refreshable { await courier.refresh() }
    }
}

private func empty(_ title: String, _ hint: String) -> some View {
    VStack(spacing: 8) { LgIcon(name: "ic_delivery", color: Cr.muted, size: 40); Text(title).font(.system(size: 16, weight: .bold)).foregroundStyle(Cr.text)
        Text(hint).font(.system(size: 13)).foregroundStyle(Cr.muted).multilineTextAlignment(.center) }.frame(maxWidth: .infinity).padding(.vertical, 36)
}

struct OfferCard: View {
    let o: COffer
    @EnvironmentObject private var courier: CourierModel
    var body: some View {
        CrCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(o.restaurantName ?? "Restoran").font(.system(size: 17, weight: .bold)).foregroundStyle(Cr.text)
                    if let a = o.restaurantAddress, !a.isEmpty { Text(a).font(.system(size: 12)).foregroundStyle(Cr.muted) }
                }
                Spacer(); if let d = o.distanceM { Text(km(d)).font(.system(size: 15, weight: .heavy)).foregroundStyle(Cr.orange) }
            }
            if let l = o.addressLabel, !l.isEmpty { Text("Yetkazish: \(l)").font(.system(size: 13)).foregroundStyle(Cr.text).padding(.top, 8) }
            if let s = o.itemsSummary, !s.isEmpty { Text(s).font(.system(size: 12)).foregroundStyle(Cr.muted) }
            Text("Daromad: \(som(o.deliveryFee ?? 0))" + (o.freeDelivery == true ? " • mijozga tekin" : "")).font(.system(size: 15, weight: .heavy)).foregroundStyle(Cr.orange).padding(.top, 8)
            Text(o.isPaid == true ? "To'langan" : "Mijozdan olinadi: \(som(o.collectAmount ?? 0))").font(.system(size: 12)).foregroundStyle(Cr.muted)
            CrButton(title: "Qabul qilish", loading: courier.busy) { Task { await courier.accept(o.id) } }.padding(.top, 12)
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
    private func pill(_ title: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) { LgIcon(name: icon, color: Cr.orange, size: 18); Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cr.orange) }
                .padding(.horizontal, 12).padding(.vertical, 8).background(Cr.orangeSoft, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }
    private func step(_ n: Int, _ label: String, done: Bool, current: Bool) -> some View {
        HStack(spacing: 6) {
            Text(done ? "✓" : "\(n)").font(.system(size: 12, weight: .bold)).foregroundStyle(done || current ? Color.white : Cr.muted)
                .frame(width: 24, height: 24).background(done ? Cr.green : (current ? Cr.orange : Cr.line), in: Circle())
            Text(label).font(.system(size: 12, weight: current ? .bold : .regular)).foregroundStyle(current || done ? Cr.text : Cr.muted).lineLimit(1)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    private func stop(_ c: Color, _ title: String, _ address: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle().fill(c).frame(width: 12, height: 12).padding(.top, 5)
            VStack(alignment: .leading, spacing: 1) { Text(title).font(.system(size: 15, weight: .bold)).foregroundStyle(Cr.text); Text(address).font(.system(size: 13)).foregroundStyle(Cr.muted) }
        }
    }
    var body: some View {
        CrCard {
            Text("FAOL BUYURTMA  #" + (a.orderCode ?? "")).font(.system(size: 12, weight: .heavy)).foregroundStyle(Cr.orange).tracking(1)
            // Ikki bosqich (LokmaGo qutisi): 1) restorandan olish → «Taomni oldim»  2) mijozga yetkazish → «Yetkazildi». Server topshirishni olishdan OLDIN rad etadi (409).
            HStack(spacing: 6) {
                step(1, "Restorandan olish", done: a.canPickup != true, current: a.canPickup == true)
                Rectangle().fill(a.canPickup != true ? Cr.green : Cr.line).frame(width: 18, height: 2)
                step(2, "Mijozga yetkazish", done: false, current: a.canPickup != true)
            }.padding(.top, 10)
            stop(Cr.green, a.restaurantName ?? "Restoran", a.restaurantAddress ?? "").padding(.top, 12)
            HStack(spacing: 8) { pill("Xaritada", "ic_map") { open(a.restaurantLat, a.restaurantLng, a.restaurantName ?? "Restoran") }
                if let p = a.restaurantPhone, !p.isEmpty { pill("Qo'ng'iroq", "ic_phone") { call(p) } } }.padding(.leading, 24).padding(.top, 8)
            stop(Cr.orange, (a.customerName ?? "").isEmpty ? "Mijoz" : a.customerName!, [a.addressLabel, a.addressNote].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")).padding(.top, 14)
            HStack(spacing: 8) { pill("Xaritada", "ic_map") { open(a.lat, a.lng, "Mijoz") }
                if let p = a.customerPhone, !p.isEmpty { pill("Qo'ng'iroq", "ic_phone") { call(p) } } }.padding(.leading, 24).padding(.top, 8)
            ForEach(Array((a.items ?? []).enumerated()), id: \.offset) { _, i in Text("• \(i.quantity ?? 0)× " + (i.name ?? "")).font(.system(size: 14)).foregroundStyle(Cr.text) }.padding(.top, 14)
            VStack(alignment: .leading, spacing: 2) {
                Text(a.isPaid == true ? "To'langan — mijozdan pul olinmaydi" : "Mijozdan olinadi: \(som(a.collectAmount ?? 0))").font(.system(size: 15, weight: .bold)).foregroundStyle(Cr.text)
                Text("Sizning daromadingiz: \(som(a.deliveryFee ?? 0))" + (a.freeDelivery == true ? " (mijozga yetkazish tekin)" : "")).font(.system(size: 14, weight: .heavy)).foregroundStyle(Cr.orange)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(14).background(Cr.orangeSoft, in: RoundedRectangle(cornerRadius: 14)).padding(.top, 12)
            if a.canPickup == true { CrButton(title: "Taomni oldim", loading: courier.busy) { Task { await courier.pickup() } }.padding(.top, 14) }
            else { CrButton(title: "Yetkazildi", loading: courier.busy, enabled: a.canDeliver ?? true) { Task { await courier.deliver() } }.padding(.top, 14) }
        }
    }
}

struct OrdersTab: View {
    @EnvironmentObject private var courier: CourierModel
    @State private var seg = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Buyurtmalar").font(.system(size: 24, weight: .heavy)).foregroundStyle(Cr.text)
                CrSegment(options: ["Faol (\(courier.active != nil ? 1 : 0))", "Tarix"], selected: $seg)
                if seg == 0 {
                    if let a = courier.active { ActiveCard(a: a) } else { empty("Faol buyurtma yo'q", "Qabul qilgan buyurtmangiz shu yerda ko'rinadi") }
                } else {
                    if courier.history.isEmpty { empty("Hali yetkazish yo'q", "Topshirgan buyurtmalaringiz shu yerda ko'rinadi") }
                    ForEach(courier.history) { h in
                        CrCard {
                            HStack { Text("#" + (h.orderCode ?? "") + " • " + (h.restaurantName ?? "")).font(.system(size: 15, weight: .bold)).foregroundStyle(Cr.text); Spacer(); Text(som(h.deliveryFee ?? 0)).font(.system(size: 14, weight: .heavy)).foregroundStyle(Cr.orange) }
                            Text([h.addressLabel, h.minutes.map { "\($0) daq" }, h.deliveredAt.map { String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") }].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")).font(.system(size: 12)).foregroundStyle(Cr.muted)
                        }
                    }
                }
            }.padding(16).padding(.bottom, 12)
        }
        .refreshable { await courier.loadHistory() }
    }
}

struct EarningsTab: View {
    @EnvironmentObject private var courier: CourierModel
    @State private var seg = 0
    private var period: CPeriod? { seg == 0 ? courier.stats?.today : (seg == 1 ? courier.stats?.week : courier.stats?.month) }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Daromad").font(.system(size: 24, weight: .heavy)).foregroundStyle(Cr.text)
                CrSegment(options: ["Bugun", "Hafta", "Oy"], selected: $seg)
                CrCard(bg: Cr.orangeSoft, border: false) {
                    Text(["Bugungi daromad", "7 kunlik daromad", "30 kunlik daromad"][seg]).font(.system(size: 13)).foregroundStyle(Cr.muted)
                    Text(som(period?.deliveryFees ?? 0)).font(.system(size: 30, weight: .heavy)).foregroundStyle(Cr.orange)
                }
                let n = period?.deliveries ?? 0
                HStack(spacing: 10) {
                    tile("Bajarilgan", "\(n) ta"); tile("O'rtacha", n > 0 ? som((period?.deliveryFees ?? 0) / Int64(n)) : "—"); tile("Yo'l", "\(period?.distanceKm ?? 0) km")
                }
                Text("Ish vaqti: \(minutes(period?.workedMinutes ?? 0)) • \(period?.shifts ?? 0) smena").font(.system(size: 12)).foregroundStyle(Cr.muted)
                Text("To'lovlar tarixi").font(.system(size: 16, weight: .bold)).foregroundStyle(Cr.text).padding(.top, 4)
                if courier.history.isEmpty { Text("Hali yetkazish yo'q").font(.system(size: 13)).foregroundStyle(Cr.muted) }
                ForEach(courier.history.prefix(20)) { h in
                    HStack {
                        VStack(alignment: .leading, spacing: 1) { Text(String((h.deliveredAt ?? "").prefix(16)).replacingOccurrences(of: "T", with: " ")).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cr.text); Text("#" + (h.orderCode ?? "")).font(.system(size: 11)).foregroundStyle(Cr.muted) }
                        Spacer(); Text(som(h.deliveryFee ?? 0)).font(.system(size: 14, weight: .heavy)).foregroundStyle(Cr.text)
                    }.padding(14).background(Color.white, in: RoundedRectangle(cornerRadius: 14)).overlay(RoundedRectangle(cornerRadius: 14).stroke(Cr.line, lineWidth: 1))
                }
            }.padding(16).padding(.bottom, 12)
        }
        .refreshable { await courier.loadStats(); await courier.loadHistory() }
    }
    private func tile(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) { Text(title).font(.system(size: 11)).foregroundStyle(Cr.muted); Text(value).font(.system(size: 15, weight: .heavy)).foregroundStyle(Cr.text).lineLimit(1) }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(Color.white, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Cr.line, lineWidth: 1))
    }
}

struct ProfileTab: View {
    @EnvironmentObject private var courier: CourierModel
    @AppStorage("app-role") private var role = ""
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Profil").font(.system(size: 24, weight: .heavy)).foregroundStyle(Cr.text)
                CrCard {
                    HStack(spacing: 14) {
                        Text(initials(courier.name)).font(.system(size: 20, weight: .heavy)).foregroundStyle(Cr.orange).frame(width: 56, height: 56).background(Cr.orangeSoft, in: Circle())
                        VStack(alignment: .leading, spacing: 2) { Text(courier.name).font(.system(size: 18, weight: .heavy)).foregroundStyle(Cr.text); Text("Kuryer • " + courier.login).font(.system(size: 13)).foregroundStyle(Cr.muted) }
                    }
                }
                if let m = courier.message { Text(m).font(.system(size: 13)).foregroundStyle(Cr.orangeDark) }
                CrButton(title: "Push tekshiruvi (test xabar)", filled: false, loading: courier.busy) { Task { await courier.testPush() } }
                Text("Test bosgach ilovani yoping va telefonni bloklang — «Push ishlayapti ✓» kelishi kerak.").font(.system(size: 11)).foregroundStyle(Cr.muted)
                CrButton(title: courier.onShift ? "Chiqishdan oldin ishni yakunlang" : "Restoran / Kuryer tanlash", filled: false, enabled: !courier.onShift) { role = "" }
                CrButton(title: "Chiqish", filled: false, danger: true, enabled: !courier.onShift) { Task { await courier.signOut() } }
            }.padding(16).padding(.bottom, 12)
        }
    }
}

/// «Ish tumanlari»: kuryer qaysi tumanlarda ishlashini o'zi tanlaydi (masalan bugun Chust tumanida). Tanlanmasa buyurtma ko'rinmaydi.
private struct DistrictsCard: View {
    @EnvironmentObject private var courier: CourierModel
    @State private var open = false
    @State private var pick: Set<String> = []
    private var summary: String {
        courier.workDistricts.isEmpty ? "Tanlanmagan — buyurtma ko'rmaysiz"
            : courier.districts.filter { courier.workDistricts.contains($0.id) }.map { ($0.uz ?? $0.id).replacingOccurrences(of: " tumani", with: "") }.joined(separator: ", ")
    }
    var body: some View {
        if !courier.districts.isEmpty {
            CrCard {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ish tumanlari").font(.system(size: 12)).foregroundStyle(Cr.muted)
                        Text(summary).font(.system(size: 14, weight: .semibold)).foregroundStyle(courier.workDistricts.isEmpty ? Cr.red : Cr.text)
                    }
                    Spacer()
                    Button { pick = Set(courier.workDistricts); open = true } label: { Text("O'zgartirish").font(.system(size: 13, weight: .bold)).foregroundStyle(Cr.orange) }.buttonStyle(.plain)
                }
            }
            .sheet(isPresented: $open) {
                VStack(spacing: 0) {
                    HStack {
                        Button("Bekor") { open = false }.foregroundStyle(Cr.muted)
                        Spacer()
                        Text("Ish tumanlari").font(.system(size: 17, weight: .bold)).foregroundStyle(Cr.text)
                        Spacer()
                        Button("Saqlash") { let ids = courier.districts.map(\.id).filter { pick.contains($0) }; open = false; Task { await courier.saveDistricts(ids) } }
                            .font(.system(size: 16, weight: .bold)).foregroundStyle(Cr.orange)
                    }.padding(16)
                    List(courier.districts) { d in
                        Button { if pick.contains(d.id) { pick.remove(d.id) } else { pick.insert(d.id) } } label: {
                            HStack { Text(d.uz ?? d.id).foregroundStyle(Cr.text); Spacer(); if pick.contains(d.id) { Image(systemName: "checkmark").foregroundStyle(Cr.orange) } }
                        }
                    }
                }.preferredColorScheme(.light)
            }
        }
    }
}
