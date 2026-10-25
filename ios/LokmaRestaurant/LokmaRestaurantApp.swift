import SwiftUI

@main struct LokmaRestaurantApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            Group {
                if model.isSignedIn { MainView().environmentObject(model) }
                else { LoginView().environmentObject(model) }
            }
            .tint(Color(red: 0.12, green: 0.62, blue: 0.39))
            .preferredColorScheme(.light)
            .task { if model.isSignedIn { await model.refresh(); model.startPolling() } }
        }
    }
}

struct LoginView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var input = LoginInput()
    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            Image(systemName: "fork.knife.circle.fill").font(.system(size: 68)).foregroundStyle(.green)
            Text("Lokma Go").font(.largeTitle.bold())
            Text("Restoran boshqaruvi").foregroundStyle(.secondary)
            VStack(spacing: 14) {
                TextField("Login", text: $input.login).textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder)
                SecureField("Parol", text: $input.password).textContentType(.password).textFieldStyle(.roundedBorder)
                if let error = model.error { Text(error).font(.footnote).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading) }
                Button { Task { await model.signIn(login: input.login, password: input.password) } } label: {
                    HStack { if model.isLoading { ProgressView().tint(.white) }; Text("Kirish").bold() }
                        .frame(maxWidth: .infinity).padding().background(Color.green).foregroundStyle(.white).clipShape(RoundedRectangle(cornerRadius: 14))
                }.disabled(model.isLoading || input.login.isEmpty || input.password.isEmpty)
            }.padding(.horizontal, 28)
            Spacer(); Text("Xavfsiz kirish").font(.caption).foregroundStyle(.secondary).padding(.bottom, 24)
        }
    }
}

struct MainView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var selection = TabSelection()
    var body: some View {
        TabView(selection: $selection.tab) {
            NavigationStack { HomeView() }.tabItem { Label("Asosiy", systemImage: "house") }.tag(0)
            NavigationStack { OrdersView() }.tabItem { Label("Buyurtmalar", systemImage: "list.bullet.rectangle") }.tag(1)
            NavigationStack { ReportView() }.tabItem { Label("Hisobot", systemImage: "chart.bar") }.tag(2)
            NavigationStack { ProfileView() }.tabItem { Label("Profil", systemImage: "person.crop.circle") }.tag(3)
        }
        .refreshable { await model.refresh() }
        .task { await model.refresh() }
        .alert("Xatolik", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
            Button("Yopish", role: .cancel) { model.error = nil }
        } message: { Text(model.error ?? "") }
    }
}

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Bugungi buyurtmalar").foregroundStyle(.secondary)
                        Text("\(model.stats?.ordersCount ?? 0)").font(.system(size: 38, weight: .bold))
                        Text("Kutilmoqda: \(model.stats?.waiting ?? 0)").foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "takeoutbag.and.cup.and.straw.fill").font(.system(size: 36)).foregroundStyle(.green)
                }.padding(.vertical, 8)
                HStack { Text("Tushum"); Spacer(); Text(money(model.stats?.revenue ?? 0)).bold() }
            }
            Section("Yangi buyurtmalar") {
                let pending = model.orders.filter { $0.status == "pending" }
                if pending.isEmpty { ContentUnavailableView("Yangi buyurtma yo'q", systemImage: "checkmark.circle") }
                ForEach(pending.prefix(5)) { order in NavigationLink(value: order.id) { OrderRow(order: order) } }
            }
        }
        .navigationTitle(model.restaurant?.name ?? "Lokma Go")
        .navigationDestination(for: String.self) { id in if let order = model.orders.first(where: { $0.id == id }) { OrderDetailView(order: order) } }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { Task { await model.refresh() } } label: { Image(systemName: "arrow.clockwise") } } }
    }
}

struct OrdersView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            if model.orders.isEmpty { ContentUnavailableView("Buyurtmalar yo'q", systemImage: "tray") }
            ForEach(model.orders) { order in NavigationLink(value: order.id) { OrderRow(order: order) } }
        }
        .navigationTitle("Buyurtmalar")
        .navigationDestination(for: String.self) { id in if let order = model.orders.first(where: { $0.id == id }) { OrderDetailView(order: order) } }
    }
}

struct OrderRow: View {
    let order: RestaurantOrder
    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 12).fill(Color.green.opacity(0.12)).frame(width: 46, height: 46)
                .overlay(Image(systemName: order.status == "pending" ? "bell.badge.fill" : "bag.fill").foregroundStyle(.green))
            VStack(alignment: .leading, spacing: 4) {
                Text("Buyurtma #\(order.number)").fontWeight(.semibold)
                Text("\(order.itemsCount) ta mahsulot · \(order.statusTitle)").font(.caption).foregroundStyle(.secondary)
            }
            Spacer(); Text(money(order.total)).font(.subheadline.bold())
        }.padding(.vertical, 4)
    }
}

struct OrderDetailView: View {
    @EnvironmentObject private var model: AppModel
    let order: RestaurantOrder
    var body: some View {
        List {
            Section { VStack(alignment: .leading, spacing: 8) {
                Text("#\(order.number)").font(.largeTitle.bold())
                Label(order.statusTitle, systemImage: "circle.fill").foregroundStyle(.green)
                Text(Date(timeIntervalSince1970: TimeInterval(order.createdAt) / 1000).formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary)
            }.padding(.vertical, 8) }
            Section("Mahsulotlar") {
                ForEach(order.items) { item in HStack { Text(item.name); Spacer(); Text("×\(item.quantity)").foregroundStyle(.secondary); Text(money(item.price * Int64(item.quantity))).bold() } }
                HStack { Text("Jami").bold(); Spacer(); Text(money(order.total)).bold() }
            }
            Section("Mijoz") {
                if let name = order.customerName { Label(name, systemImage: "person") }
                if let phone = order.customerPhone { Link(destination: URL(string: "tel:\(phone)")!) { Label(phone, systemImage: "phone") } }
                if let address = order.address { Label(address, systemImage: "mappin.and.ellipse") }
                LabeledContent("To'lov", value: order.paymentMethod == "cash" ? "Naqd" : "Karta")
                LabeledContent("Buyurtma", value: order.fulfillment == "delivery" ? "Yetkazib berish" : "Olib ketish")
            }
            if let action = order.nextAction {
                Section { Button(action.title) { Task { await model.update(order, to: action.status) } }.frame(maxWidth: .infinity).fontWeight(.bold) }
            }
        }.navigationTitle("Buyurtma").navigationBarTitleDisplayMode(.inline)
    }
}

struct ReportView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            Section("Bugungi natijalar") {
                LabeledContent("Buyurtmalar", value: "\(model.stats?.ordersCount ?? 0)")
                LabeledContent("Tushum", value: money(model.stats?.revenue ?? 0))
                LabeledContent("Kutayotgan", value: "\(model.stats?.waiting ?? 0)")
            }
            Section("To'lovlar") {
                LabeledContent("Naqd · \(model.stats?.cashCount ?? 0)", value: money(model.stats?.cashRevenue ?? 0))
                LabeledContent("Karta · \(model.stats?.cardCount ?? 0)", value: money(model.stats?.cardRevenue ?? 0))
            }
        }.navigationTitle("Hisobot")
    }
}

struct ProfileView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            Section("Restoran") {
                LabeledContent("Nomi", value: model.restaurant?.name ?? "—")
                if let cuisine = model.restaurant?.cuisine { LabeledContent("Yo'nalish", value: cuisine) }
            }
            Section { Button("Hisobdan chiqish", role: .destructive) { model.signOut() } }
        }.navigationTitle("Profil")
    }
}

@MainActor final class LoginInput: ObservableObject {
    @Published var login = ""
    @Published var password = ""
}

@MainActor final class TabSelection: ObservableObject {
    @Published var tab = 0
}

func money(_ amount: Int64) -> String {
    let formatter = NumberFormatter(); formatter.numberStyle = .decimal; formatter.groupingSeparator = " "
    return "\(formatter.string(from: NSNumber(value: amount)) ?? "\(amount)") so'm"
}
