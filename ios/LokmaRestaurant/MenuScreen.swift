import SwiftUI

struct MenuScreen: View {
    @EnvironmentObject private var menu: MenuStore
    @State private var query = ""
    @State private var creating = false
    @State private var editing: Dish?
    @State private var deleting: Dish?

    private var shown: [Dish] { menu.dishes.filter { query.trimmingCharacters(in: .whitespaces).isEmpty || $0.name.localizedCaseInsensitiveContains(query.trimmingCharacters(in: .whitespaces)) } }
    /// Kategoriyalar tartibi admin panel bilan bir xil; ro'yxatda yo'q kategoriya oxirida "Boshqa".
    private struct Section: Identifiable { let label: String; let dishes: [Dish]; var id: String { label } }
    private var groups: [Section] {
        let known = Set(DishCategories.all.map(\.value))
        var out = DishCategories.all.compactMap { c -> Section? in
            let list = shown.filter { $0.category == c.value }
            return list.isEmpty ? nil : Section(label: c.label, dishes: list)
        }
        let others = shown.filter { !known.contains($0.category) }
        if !others.isEmpty { out.append(Section(label: "Boshqa", dishes: others)) }
        return out
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Menyu").font(.system(size: 26, weight: .heavy))
                    Text("\(menu.dishes.count) ta taom").font(.system(size: 13)).foregroundStyle(Dt.textMuted)
                }
                Spacer()
                Button { creating = true } label: {
                    HStack(spacing: 6) { LgIcon(name: "ic_plus", color: Dt.onAccent, size: 18); Text("Taom").font(.system(size: 14, weight: .bold)).foregroundStyle(Dt.onAccent) }
                        .padding(.horizontal, 16).padding(.vertical, 12).background(Dt.green, in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, Dt.spaceScreen).padding(.top, 8)

            HStack(spacing: 10) {
                LgIcon(name: "ic_search", color: Dt.textMuted, size: 20)
                TextField("", text: $query, prompt: Text("Taom qidirish").foregroundStyle(Dt.textMuted)).submitLabel(.search)
            }
            .padding(.horizontal, 14).frame(height: 48).background(Dt.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Dt.line, lineWidth: 1))
            .padding(.horizontal, Dt.spaceScreen).padding(.vertical, 12)

            if let toast = menu.toast {
                Text(toast).font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.orange).frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12).background(Dt.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12)).padding(.horizontal, Dt.spaceScreen)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    if !menu.loaded && menu.dishes.isEmpty { ProgressView().tint(Dt.green).frame(maxWidth: .infinity).padding(.top, 30) }
                    else if shown.isEmpty {
                        VStack(spacing: 8) {
                            LgIcon(name: "ic_utensils", color: Dt.textMuted, size: 40)
                            Text(query.isEmpty ? "Menyu bo'sh" : "Hech narsa topilmadi").font(.system(size: 15, weight: .semibold))
                            Text(menu.error ?? (query.isEmpty ? "«+ Taom» tugmasi bilan birinchi taomni qo'shing" : "Boshqa nom bilan qidirib ko'ring"))
                                .font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textMuted).multilineTextAlignment(.center)
                        }.frame(maxWidth: .infinity).padding(.vertical, 40)
                    }
                    ForEach(groups) { group in
                        Text("\(group.label) · \(group.dishes.count)").font(.system(size: 13, weight: .bold)).foregroundStyle(Dt.textSecondary).padding(.top, 8)
                        ForEach(group.dishes) { d in row(d) }
                    }
                }
                .padding(Dt.spaceScreen)
            }
            .refreshable { await menu.refresh() }
        }
        .sheet(isPresented: $creating) { DishFormScreen(dish: nil) }
        .sheet(item: $editing) { DishFormScreen(dish: $0) }
        .alert("O'chirilsinmi?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), presenting: deleting) { d in
            Button("O'chirish", role: .destructive) { Task { await menu.delete(d) } }
            Button("Bekor qilish", role: .cancel) {}
        } message: { d in Text("\"\(d.name)\" menyudan butunlay o'chadi. Vaqtincha to'xtatish uchun STOP tugmasidan foydalaning.") }
    }

    private func row(_ d: Dish) -> some View {
        HStack(spacing: 12) {
            Button { editing = d } label: {
                HStack(spacing: 12) {
                    DishThumb(url: d.imageUrl, size: 64, grayscale: !d.available)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(d.name).font(.system(size: 16, weight: .bold)).multilineTextAlignment(.leading).lineLimit(2)
                        HStack(spacing: 8) {
                            Text(som(d.price)).font(.system(size: 14, weight: .semibold)).foregroundStyle(Dt.greenText)
                            if let old = d.oldPrice { Text(som(old)).font(.system(size: 12)).foregroundStyle(Dt.textMuted).strikethrough() }
                        }
                        if !d.available {
                            Text("STOP").font(.system(size: 10, weight: .heavy)).foregroundStyle(Dt.onAccent)
                                .padding(.horizontal, 8).padding(.vertical, 2).background(Dt.red, in: RoundedRectangle(cornerRadius: 6))
                        }
                    }
                    .opacity(d.available ? 1 : 0.55)
                    Spacer(minLength: 0)
                }.contentShape(Rectangle())
            }.buttonStyle(.plain)
            VStack(spacing: 2) {
                Toggle("", isOn: Binding(get: { d.available }, set: { _ in Task { await menu.toggleStop(d) } })).labelsHidden().tint(Dt.green)
                Text(d.available ? "Sotuvda" : "STOP").font(.system(size: 10, weight: .semibold)).foregroundStyle(d.available ? Dt.greenText : Dt.red)
            }
            Button { deleting = d } label: { LgIcon(name: "ic_trash", color: Dt.red, size: 20).frame(width: 36, height: 36) }.buttonStyle(.plain)
        }
        .padding(10).background(Dt.card, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
        .overlay(RoundedRectangle(cornerRadius: Dt.radiusCard).stroke(Dt.line, lineWidth: 1))
    }
}
