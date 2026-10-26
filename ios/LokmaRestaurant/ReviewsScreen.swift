import SwiftUI

/// Mijozlar izohlari: bitta sahifada barcha baho va fikrlar. Server alohida endpoint bermaydi — baho/izoh buyurtmaning
/// o'zida (rating, comment), shuning uchun oxirgi 120 kun tarixi o'qiladi va faqat bahosi yoki izohi borlari qoldiriladi.
struct ReviewsScreen: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var items: [Review] = []
    @State private var loading = true
    @State private var error: String?
    @State private var filter = 0            // 0 = hammasi, 1...5 = shu baho
    @State private var onlyText = false

    private var rated: [Review] { items.filter { $0.stars > 0 } }
    private var average: Double { rated.isEmpty ? 0 : Double(rated.reduce(0) { $0 + $1.stars }) / Double(rated.count) }
    private var shown: [Review] { items.filter { (filter == 0 || $0.stars == filter) && (!onlyText || !$0.comment.isEmpty) } }
    private func stars(_ n: Int) -> String { String(repeating: "★", count: min(max(n, 0), 5)) + String(repeating: "☆", count: 5 - min(max(n, 0), 5)) }
    private func date(_ ms: Int64) -> String { ms > 0 ? hhmmDate(ms) : "" }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { dismiss() } label: { LgIcon(name: "ic_arrow_left").frame(width: 36, height: 36) }.buttonStyle(.plain)
                Text("Mijozlar izohlari").font(.system(size: 22, weight: .heavy))
                Spacer()
            }.padding(16)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    if loading && items.isEmpty { ProgressView().tint(Dt.green).frame(maxWidth: .infinity).padding(.top, 24) }
                    if !rated.isEmpty { summary }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(0..<6, id: \.self) { i in chip(i == 0 ? "Hammasi" : "\(i) ★", filter == i) { filter = i } }
                            chip("Faqat izohli", onlyText) { onlyText.toggle() }
                        }
                    }
                    if !loading && shown.isEmpty {
                        VStack(spacing: 8) {
                            LgIcon(name: "ic_message", color: Dt.textMuted, size: 40)
                            Text("Izoh topilmadi").font(.system(size: 15, weight: .semibold))
                            Text(error ?? "Mijozlar buyurtmani baholaganda va izoh yozganda shu yerda ko'rinadi").font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center)
                        }.frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                    ForEach(shown) { r in
                        SectionCard {
                            HStack {
                                Text(r.stars > 0 ? stars(r.stars) : "Izoh").font(.system(size: 16, weight: .bold)).foregroundStyle(Dt.orange)
                                Spacer()
                                Text("#\(r.number)").font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.textSecondary)
                            }
                            if !r.comment.isEmpty { Text(r.comment).font(.system(size: 15)).padding(.top, 8) }
                            HStack {
                                Text(r.customer).font(.system(size: 12, weight: .semibold)).foregroundStyle(Dt.textSecondary)
                                Spacer()
                                Text("\(date(r.at)) • \(som(r.total))").font(.system(size: 11)).foregroundStyle(Dt.textMuted)
                            }.padding(.top, 10)
                        }
                    }
                }
                .padding(.horizontal, Dt.spaceScreen).padding(.bottom, 12)
            }
            .refreshable { await load() }
        }
        .lgScreen()
        .navigationBarBackButtonHidden(true).toolbar(.hidden, for: .navigationBar)
        .task { await load() }
    }

    private var summary: some View {
        SectionCard(bg: Dt.cardStat) {
            HStack(spacing: 16) {
                VStack(spacing: 2) {
                    Text(String(format: "%.1f", average)).font(.system(size: 40, weight: .heavy))
                    Text(stars(Int(average.rounded()))).font(.system(size: 16)).foregroundStyle(Dt.orange)
                    Text("\(rated.count) ta baho").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                }
                VStack(spacing: 4) {
                    ForEach([5, 4, 3, 2, 1], id: \.self) { s in
                        let n = rated.filter { $0.stars == s }.count
                        HStack(spacing: 8) {
                            Text("\(s)").font(.system(size: 11)).foregroundStyle(Dt.textMuted).frame(width: 10)
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(Dt.cardHigh)
                                    Capsule().fill(Dt.orange).frame(width: geo.size.width * CGFloat(n) / CGFloat(max(rated.count, 1)))
                                }
                            }.frame(height: 6)
                            Text("\(n)").font(.system(size: 11)).foregroundStyle(Dt.textMuted).frame(width: 24, alignment: .trailing)
                        }
                    }
                }
            }
        }
    }

    private func chip(_ label: String, _ selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(selected ? Dt.onAccent : Dt.textSecondary)
                .padding(.horizontal, 14).padding(.vertical, 9).background(selected ? Dt.green : Dt.cardHigh, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }

    private func load() async {
        loading = true; error = nil
        defer { loading = false }
        do { items = try await model.loadReviews() } catch { self.error = error.localizedDescription }
    }
}
