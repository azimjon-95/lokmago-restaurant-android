import SwiftUI

/// Pages pushed on top of the tabs.
enum Route: Hashable { case order(String), report, reviews }

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var menu: MenuStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var path: [Route] = []

    var body: some View {
        ZStack {
            if model.isSignedIn {
                NavigationStack(path: $path) {
                    Group {
                        switch tab {
                        case 0: HomeScreen(openOrder: { path.append(.order($0)) }, showOrders: { tab = 1 })
                        case 1: OrdersScreen(openOrder: { path.append(.order($0)) })
                        case 2: MenuScreen()
                        case 3: StopListScreen(openMenu: { tab = 2 })
                        default: SettingsScreen(openReport: { path.append(.report) }, openReviews: { path.append(.reviews) })
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    // safeAreaInset: lists end ABOVE the bar, yet scroll beneath the glass like in Instagram
                    .safeAreaInset(edge: .bottom, spacing: 0) { TabBar(tab: $tab, badge: model.queue.count, stopBadge: menu.stoppedCount) }
                    .lgScreen()
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .order(let id): OrderDetailScreen(orderId: id)
                        case .report: ReportScreen(standalone: true)
                        case .reviews: ReviewsScreen()
                        }
                    }
                }
                if !model.queue.isEmpty && !model.minimized {
                    AlertScreen(openOrder: { id in model.minimize(); path.append(.order(id)) }).transition(.opacity)
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

/// Instagram / iPhone uslubidagi suzuvchi menyu: ekran chetlaridan ajralib turgan kapsula, faqat ikonkalar, tanlangani yumshoq
/// "tabletka" bilan ajratiladi, sonlar qizil nishonda. iOS 26+ da haqiqiy "Liquid Glass", undan eskida blur material.
struct TabBar: View {
    @Binding var tab: Int
    let badge: Int
    var stopBadge = 0
    private let items: [(icon: String, label: String)] = [("ic_home", "Asosiy"), ("ic_orders", "Buyurtmalar"), ("ic_book", "Menyu"), ("ic_ban", "Stop List"), ("ic_profile", "Profil")]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(items.indices, id: \.self) { i in
                let count = i == 1 ? badge : (i == 3 ? stopBadge : 0)
                Button {
                    if tab != i { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                    tab = i
                } label: {
                    ZStack(alignment: .topTrailing) {
                        LgIcon(name: items[i].icon, color: tab == i ? Dt.green : Dt.textSecondary, size: 26)
                        if count > 0 {
                            Text(count > 99 ? "99+" : "\(count)").font(.system(size: 10, weight: .bold)).foregroundStyle(Dt.onAccent)
                                .padding(.horizontal, 5).padding(.vertical, 1).background(Dt.red, in: Capsule()).offset(x: 12, y: -8)
                        }
                    }
                    .frame(maxWidth: .infinity).frame(height: 54)
                    .background { if tab == i { Capsule().fill(Color.white.opacity(0.14)) } }
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(items[i].label + (count > 0 ? ", \(count)" : "")))
            }
        }
        .padding(6)
        .modifier(GlassCapsule())
        .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 6)
        .animation(.spring(response: 0.3, dampingFraction: 0.82), value: tab)
    }
}

struct GlassCapsule: ViewModifier {
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: Capsule())
        } else {
            legacy(content)
        }
        #else
        legacy(content)
        #endif
    }

    private func legacy(_ content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 0.5))
            .shadow(color: .black.opacity(0.35), radius: 16, y: 6)
    }
}
