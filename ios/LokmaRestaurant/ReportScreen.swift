import SwiftUI
import Charts

struct ReportScreen: View {
    @EnvironmentObject private var model: AppModel

    private func todayLabel() -> String {
        let months = ["yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"]
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "Asia/Tashkent") ?? .current
        let c = cal.dateComponents([.day, .month], from: Date())
        return "Bugun, \(c.day ?? 1) \(months[max(0, min(11, (c.month ?? 1) - 1))])"
    }
    private func delta(_ v: Int, suffix: String = "") -> String? { v == 0 ? nil : "\(v > 0 ? "↑" : "↓") \(abs(v))\(suffix)" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Dt.spaceGap) {
                Text("Hisobot").font(.system(size: 26, weight: .heavy)).padding(.top, 8)
                SectionCard(bg: Dt.cardStat) {
                    HStack {
                        Text(todayLabel()).font(.system(size: 15, weight: .semibold))
                        Spacer()
                        LgIcon(name: "ic_calendar", color: Dt.textSecondary, size: 20)
                    }
                }
                if let s = model.stats { content(s) }
                else { Text(model.error ?? "Yuklanmoqda…").foregroundStyle(Dt.textMuted) }
            }
            .padding(Dt.spaceScreen)
        }
        .refreshable { await model.refresh() }
    }

    @ViewBuilder private func content(_ s: TodayStats) -> some View {
        let total = max(s.cashRevenue + s.cardRevenue, 1)
        let cashPct = Int((Double(s.cashRevenue) * 100 / Double(total)).rounded())
        HStack(spacing: Dt.spaceGap) {
            StatCard(label: "Buyurtmalar soni", value: "\(s.ordersCount)", delta: delta(s.ordersDelta))
            StatCard(label: "Jami savdo", value: som(s.revenue), delta: delta(s.revenueDeltaPct, suffix: "%"))
        }
        HStack(spacing: Dt.spaceGap) {
            SectionCard(bg: Dt.cardStat) {
                HStack(spacing: 6) { LgIcon(name: "ic_cash", color: Dt.green, size: 16); Text("Naqd usuli").font(.system(size: 12)).foregroundStyle(Dt.green) }
                Text(som(s.cashRevenue)).font(.system(size: 15, weight: .bold)).padding(.top, 4)
                Text("\(cashPct)%").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
            }
            SectionCard(bg: Dt.cardStat) {
                HStack(spacing: 6) { LgIcon(name: "ic_card", color: Dt.blue, size: 16); Text("Karta usuli").font(.system(size: 12)).foregroundStyle(Dt.blue) }
                Text(som(s.cardRevenue)).font(.system(size: 15, weight: .bold)).padding(.top, 4)
                Text("\(100 - cashPct)%").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
            }
        }

        Text("Savdo grafigi").font(.system(size: Dt.fontSection, weight: .bold)).padding(.top, 6)
        SectionCard {
            Chart {
                ForEach(9...21, id: \.self) { h in
                    BarMark(x: .value("Soat", String(format: "%02d:00", h)), y: .value("So'm", Double(s.hourly[h])))
                        .foregroundStyle(Dt.green).cornerRadius(4)
                }
            }
            .chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(Dt.line); AxisValueLabel().foregroundStyle(Dt.textMuted) } }
            .chartXAxis { AxisMarks(values: .automatic(desiredCount: 6)) { _ in AxisValueLabel().foregroundStyle(Dt.textMuted) } }
            .frame(height: 170)
        }

        Text("To'lov turlari").font(.system(size: Dt.fontSection, weight: .bold)).padding(.top, 6)
        SectionCard {
            HStack(spacing: 24) {
                Chart {
                    SectorMark(angle: .value("Naqd", Double(max(s.cashRevenue, 0))), innerRadius: .ratio(0.62), angularInset: 2).foregroundStyle(Dt.green)
                    SectorMark(angle: .value("Karta", Double(max(s.cardRevenue, 0))), innerRadius: .ratio(0.62), angularInset: 2).foregroundStyle(Dt.blueBox)
                }
                .frame(width: 130, height: 130)
                VStack(alignment: .leading, spacing: 12) {
                    legend(color: Dt.green, title: "Naqd", pct: cashPct)
                    legend(color: Dt.blueBox, title: "Karta", pct: 100 - cashPct)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private func legend(color: Color, title: String, pct: Int) -> some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(title).font(.system(size: 14)); Spacer(minLength: 16); Text("\(pct)%").font(.system(size: 14, weight: .semibold))
        }.frame(width: 130)
    }
}
