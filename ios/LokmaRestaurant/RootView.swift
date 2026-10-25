import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var path: [String] = []

    var body: some View {
        ZStack {
            if model.isSignedIn {
                NavigationStack(path: $path) {
                    VStack(spacing: 0) {
                        Group {
                            switch tab {
                            case 0: HomeScreen(openOrder: { path.append($0) }, showOrders: { tab = 1 })
                            case 1: OrdersScreen(openOrder: { path.append($0) })
                            case 2: ReportScreen()
                            default: SettingsScreen()
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        TabBar(tab: $tab, badge: model.queue.count)
                    }
                    .lgScreen()
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: String.self) { OrderDetailScreen(orderId: $0) }
                }
                if !model.queue.isEmpty && !model.minimized {
                    AlertScreen(openOrder: { id in model.minimize(); path.append(id) }).transition(.opacity)
                }
            } else {
                LoginScreen()
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.queue.isEmpty)
        .preferredColorScheme(.dark)
        .tint(Dt.green)
        .onChange(of: scenePhase) { _, phase in model.scenePhaseChanged(phase) }
        .alert("Xatolik", isPresented: Binding(get: { model.error != nil && model.isSignedIn }, set: { if !$0 { model.error = nil } })) {
            Button("Yopish", role: .cancel) { model.error = nil }
        } message: { Text(model.error ?? "") }
        .task { await model.start() }
    }
}

struct TabBar: View {
    @Binding var tab: Int
    let badge: Int
    private let items: [(String, String)] = [("ic_home", "Asosiy"), ("ic_orders", "Buyurtmalar"), ("ic_report", "Hisobot"), ("ic_profile", "Profil")]
    var body: some View {
        HStack {
            ForEach(items.indices, id: \.self) { i in
                Button { tab = i } label: {
                    VStack(spacing: 4) {
                        ZStack(alignment: .topTrailing) {
                            LgIcon(name: items[i].0, color: tab == i ? Dt.green : Dt.textMuted, size: 24)
                            if i == 1 && badge > 0 {
                                Text("\(badge)").font(.system(size: 10, weight: .bold)).foregroundStyle(Dt.onAccent)
                                    .padding(.horizontal, 5).padding(.vertical, 1).background(Dt.red, in: Capsule()).offset(x: 10, y: -6)
                            }
                        }
                        Text(items[i].1).font(.system(size: Dt.fontNav, weight: tab == i ? .bold : .medium)).foregroundStyle(tab == i ? Dt.green : Dt.textMuted)
                    }.frame(maxWidth: .infinity)
                }.buttonStyle(.plain)
            }
        }
        .padding(.top, 10).padding(.bottom, 6)
        .background(Dt.header.overlay(alignment: .top) { Rectangle().fill(Dt.line).frame(height: 1) }.ignoresSafeArea(edges: .bottom))
    }
}
