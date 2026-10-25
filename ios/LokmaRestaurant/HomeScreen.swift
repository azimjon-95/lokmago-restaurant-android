import SwiftUI

struct HomeScreen: View {
    @EnvironmentObject private var model: AppModel
    let openOrder: (String) -> Void
    let showOrders: () -> Void

    private var recent: [RestaurantOrder] {
        model.orders.filter { $0.status.lowercased() != "pending" }.sorted { $0.createdAt > $1.createdAt }.prefix(5).map { $0 }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Dt.spaceGap) {
                HStack {
                    BrandHeader()
                    Spacer()
                    ConnectionLabel(online: model.online)
                    BellButton(count: model.queue.count, action: showOrders).padding(.leading, 6)
                }
                restaurantCard
                if !model.online { banner(icon: "ic_wifi_off", text: "Internet yo'q. Ulanish tiklanganda buyurtmalar avtomatik yangilanadi.", color: Dt.red) }

                if !model.queue.isEmpty {
                    HStack {
                        Text("Yangi buyurtmalar").font(.system(size: Dt.fontSection, weight: .bold))
                        Spacer()
                        Text("\(model.queue.count) ta kutmoqda").font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.red)
                    }
                    ForEach(model.queue) { o in row(o, highlight: true, compact: false) }
                }

                if let s = model.stats { statsBlock(s) }

                if !recent.isEmpty {
                    HStack {
                        Text("So'nggi buyurtmalar").font(.system(size: Dt.fontSection, weight: .bold))
                        Spacer()
                        Button("Barchasi", action: showOrders).font(.system(size: 14)).foregroundStyle(Dt.greenText)
                    }.padding(.top, 6)
                    ForEach(recent) { o in row(o, highlight: false, compact: true) }
                }
            }
            .padding(Dt.spaceScreen)
        }
        .refreshable { await model.refresh() }
    }

    private var restaurantCard: some View {
        HStack(spacing: 14) {
            LgIcon(name: "ic_store", color: Dt.orange, size: 34).frame(width: 72, height: 72)
                .background(Dt.cardHigh, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 8) {
                Text(model.restaurant?.name ?? "").font(.system(size: 20, weight: .bold))
                OnlineChip(online: model.online)
            }
            Spacer()
            LgIcon(name: "ic_chevron_right", color: Dt.textSecondary, size: 20)
        }
        .padding(12).background(Dt.card, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
    }

    @ViewBuilder private func statsBlock(_ s: TodayStats) -> some View {
        HStack(spacing: Dt.spaceGap) {
            StatCard(label: "Bugungi buyurtmalar", value: "\(s.ordersCount)", delta: delta(s.ordersDelta))
            StatCard(label: "Kutilayotgan", value: "\(s.waiting)", delta: nil) { LgIcon(name: "ic_clock", color: Dt.blue, size: 26) }
        }
        HStack(spacing: Dt.spaceGap) {
            StatCard(label: "Naqd", value: "\(s.cashCount)", delta: nil) { IconBox(icon: "ic_cash", bg: Dt.cardHigh, tint: Dt.greenText) }
            StatCard(label: "Karta", value: "\(s.cardCount)", delta: nil) { IconBox(icon: "ic_card", bg: Dt.blueBox, tint: Dt.onAccent) }
        }
        let parts = som(s.revenue).split(separator: " ")
        HStack(spacing: 14) {
            IconBox(icon: "ic_wallet", bg: Dt.green, tint: Dt.onAccent)
            VStack(alignment: .leading, spacing: 2) {
                Text("Bugungi tushum").font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textSecondary)
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text(parts.dropLast().joined(separator: " ")).font(.system(size: Dt.fontBig, weight: .heavy))
                    Text(String(parts.last ?? "")).font(.system(size: 16)).foregroundStyle(Dt.textSecondary)
                }
            }
            Spacer()
            if let d = delta(s.revenueDeltaPct, suffix: "%") { Text(d).font(.system(size: 14, weight: .semibold)).foregroundStyle(Dt.greenText) }
        }
        .padding(Dt.spaceCardPad).background(Dt.cardStat, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
    }

    private func row(_ o: RestaurantOrder, highlight: Bool, compact: Bool) -> some View {
        Button { openOrder(o.id) } label: { OrderRow(order: o, highlight: highlight, compact: compact) }.buttonStyle(.plain)
    }

    private func delta(_ v: Int, suffix: String = "") -> String? { v == 0 ? nil : "\(v > 0 ? "↑" : "↓") \(abs(v))\(suffix)" }

    private func banner(icon: String, text: String, color: Color) -> some View {
        HStack(spacing: 10) {
            LgIcon(name: icon, color: color, size: 20)
            Text(text).font(.system(size: 13))
            Spacer(minLength: 0)
        }
        .padding(12).background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
    }
}
