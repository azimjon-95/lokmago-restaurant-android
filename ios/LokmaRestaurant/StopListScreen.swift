import SwiftUI

/// Stop List — vaqtincha to'xtatilgan taomlar (admin panel StopListPage bilan bir xil): mijozlarga ko'rinmaydi,
/// «Qaytarish» bilan darhol sotuvga chiqadi. Ro'yxat soket orqali o'zi yangilanadi (MenuStore).
struct StopListScreen: View {
    @EnvironmentObject private var menu: MenuStore
    let openMenu: () -> Void
    private var stopped: [Dish] { menu.dishes.filter { !$0.available } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Stop List").font(.system(size: 26, weight: .heavy)).padding(.horizontal, Dt.spaceScreen).padding(.top, 8)
            Text("Vaqtincha to'xtatilgan taomlar — mijozlarga ko'rinmaydi").font(.system(size: 13)).foregroundStyle(Dt.textMuted)
                .padding(.horizontal, Dt.spaceScreen).padding(.bottom, 12)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    if !menu.loaded && menu.dishes.isEmpty { ProgressView().tint(Dt.green).frame(maxWidth: .infinity).padding(.top, 30) }
                    else if stopped.isEmpty {
                        VStack(spacing: 10) {
                            LgIcon(name: "ic_check_circle", color: Dt.greenText, size: 34).frame(width: 64, height: 64).background(Dt.green.opacity(0.15), in: Circle())
                            Text("Stop List bo'sh").font(.system(size: 17, weight: .semibold))
                            Text("Barcha taomlar sotuvda. Taom tugasa Menyudan STOP tugmasini bosing.").font(.system(size: 13)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center)
                            Button(action: openMenu) {
                                Text("Menyuga o'tish").font(.system(size: 14, weight: .semibold)).padding(.horizontal, 18).padding(.vertical, 10)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Dt.line, lineWidth: 1))
                            }.buttonStyle(.plain).padding(.top, 6)
                        }.frame(maxWidth: .infinity).padding(.vertical, 48)
                    } else {
                        Text("\(stopped.count) ta taom to'xtatilgan").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                    }
                    ForEach(stopped) { d in
                        HStack(spacing: 12) {
                            DishThumb(url: d.imageUrl, size: 56, grayscale: true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(d.name).font(.system(size: 15, weight: .bold)).lineLimit(2)
                                Text("\(DishCategories.label(d.category)) • \(som(d.price))").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                            }
                            Spacer(minLength: 0)
                            Button { Task { await menu.toggleStop(d) } } label: {
                                Text("Qaytarish").font(.system(size: 13, weight: .bold)).foregroundStyle(Dt.onAccent)
                                    .padding(.horizontal, 14).padding(.vertical, 10).background(Dt.green, in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain)
                        }
                        .padding(10).background(Dt.card, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
                        .overlay(RoundedRectangle(cornerRadius: Dt.radiusCard).stroke(Dt.line, lineWidth: 1))
                    }
                }
                .padding(.horizontal, Dt.spaceScreen).padding(.bottom, 12)
            }
            .refreshable { await menu.refresh() }
        }
    }
}
