import SwiftUI

struct OrdersScreen: View {
    @EnvironmentObject private var model: AppModel
    let openOrder: (String) -> Void
    @State private var tab = 0
    private let tabs: [(status: String, label: String)] = [
        ("pending", "Yangi"), ("accepted", "Qabul qilingan"), ("preparing", "Tayyorlanmoqda"),
        ("ready", "Tayyor"), ("delivering", "Yetkazilmoqda"), ("delivered", "Yakunlangan"),
    ]

    private func count(_ status: String) -> Int { model.orders.filter { $0.status.lowercased() == status }.count }
    private var shown: [RestaurantOrder] {
        model.orders.filter { $0.status.lowercased() == tabs[tab].status }.sorted { $0.createdAt > $1.createdAt }
    }
    /// Badge colours exactly as in the mockup (and Android): Qabul qilingan = green, Tayyorlanmoqda = blue ...
    private func badge(_ status: String) -> Color {
        switch status {
        case "pending": return Dt.red
        case "accepted": return Dt.green
        case "preparing": return Dt.blueBox
        case "ready": return Dt.teal
        case "delivering": return Dt.purple
        default: return Dt.green
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Buyurtmalar").font(.system(size: 26, weight: .heavy))
                Spacer()
                BellButton(count: model.queue.count) { tab = 0 }
            }
            .padding(.horizontal, Dt.spaceScreen).padding(.top, 8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(tabs.indices, id: \.self) { i in
                        TabChip(label: tabs[i].label, count: count(tabs[i].status), selected: i == tab,
                                color: StatusStyle.bar(tabs[i].status), badge: badge(tabs[i].status)) { tab = i }
                    }
                }
                .padding(.horizontal, Dt.spaceScreen).padding(.vertical, 14)
            }

            ScrollView {
                LazyVStack(spacing: Dt.spaceGap) {
                    if shown.isEmpty {
                        VStack(spacing: 8) {
                            LgIcon(name: "ic_orders", color: Dt.textMuted, size: 40)
                            Text("Bu bo'limda buyurtma yo'q").font(.system(size: 15, weight: .semibold))
                            Text("Yangi buyurtmalar shu yerda ko'rinadi").font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textMuted)
                        }.padding(.vertical, 40)
                    }
                    ForEach(shown) { o in
                        Button { openOrder(o.id) } label: { OrderRow(order: o) }.buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Dt.spaceScreen).padding(.bottom, 12)
            }
            .refreshable { await model.refresh() }
        }
    }
}
