import SwiftUI
import PhotosUI

/// Yangi taom / tahrirlash — admin panelning DishForm'i bilan bir xil maydonlar va qoidalar:
/// rasm, nom, tavsif, kategoriya, narx / eski narx, tayyorlanish vaqti, og'irlik yoki hajm (ichimlikda), ichimlik turi,
/// KBJU (ichimlikda yo'q), zal narxi, hajm va qo'shimchalar guruhlari.
struct DishFormScreen: View {
    let dish: Dish?
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var menu: MenuStore
    @Environment(\.dismiss) private var dismiss
    @State private var draft: DishDraft
    @State private var picked: PhotosPickerItem?
    @State private var uploading = false
    @State private var saving = false
    @State private var error: String?

    init(dish: Dish?) { self.dish = dish; _draft = State(initialValue: dish.map(DishDraft.init) ?? DishDraft()) }

    private var drink: Bool { DishCategories.isDrink(draft.category) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { dismiss() } label: { LgIcon(name: "ic_close").frame(width: 36, height: 36) }.buttonStyle(.plain).disabled(saving || uploading)
                Text(dish == nil ? "Yangi taom" : "Taomni tahrirlash").font(.system(size: 20, weight: .bold))
                Spacer()
            }.padding(16)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    photoBlock
                    field("Taom nomi *", $draft.name)
                    field("Tavsif", $draft.description, lines: 3)
                    picker("Kategoriya *", DishCategories.label(draft.category), DishCategories.all.map { ($0.value, $0.label) }) { v in
                        draft.category = v
                        if !DishCategories.isDrink(v) { draft.drinkType = "" }          // ichimlikdan oddiy taomga: tur tozalanadi
                    }
                    HStack(spacing: 10) {
                        field("Narx (so'm) *", digits($draft.price), keyboard: .numberPad)
                        field("Eski narx", digits($draft.oldPrice), keyboard: .numberPad)
                    }
                    field("Tayyorlanish vaqti (daqiqa)", digits($draft.prepMinutes), keyboard: .numberPad)

                    if drink {
                        field("Hajm", $draft.volume, hint: "Masalan: 0.5 l · 1 l · 330 ml")
                        picker("Ichimlik turi", DishCategories.drinkTypes.first { $0.value == draft.drinkType }?.label ?? "Tanlanmagan",
                               [("", "Tanlanmagan")] + DishCategories.drinkTypes.map { ($0.value, $0.label) }) { draft.drinkType = $0 }
                    } else {
                        field("Og'irlik", $draft.weight, hint: "Bitta taom: 150 г · Assorti: 150/30/30/20 г")
                        Text("Oziqlanish qiymati (ixtiyoriy)").font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.textSecondary)
                        HStack(spacing: 10) { field("Kaloriya", $draft.calories, keyboard: .decimalPad); field("Oqsil, g", $draft.protein, keyboard: .decimalPad) }
                        HStack(spacing: 10) { field("Yog', g", $draft.fat, keyboard: .decimalPad); field("Uglevod, g", $draft.carbs, keyboard: .decimalPad) }
                    }

                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Zal uchun alohida narx").font(.system(size: 15, weight: .semibold))
                            Text("O'chiq bo'lsa zal narxi yetkazish narxi bilan bir xil").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                        }
                        Spacer()
                        Toggle("", isOn: Binding(get: { draft.priceMode == "custom" }, set: { draft.priceMode = $0 ? "custom" : "sync" })).labelsHidden().tint(Dt.green)
                    }.padding(14).background(Dt.card, in: RoundedRectangle(cornerRadius: 16))
                    if draft.priceMode == "custom" { field("Zal narxi (so'm)", digits($draft.dineInPrice), keyboard: .numberPad) }

                    groupsEditor

                    if let error { Text(error).font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.red) }
                }.padding(.horizontal, 16).padding(.bottom, 12)
            }

            PrimaryButton(title: "Saqlash", icon: "ic_check", loading: saving, enabled: !uploading, action: submit).padding(16)
        }
        .lgScreen().preferredColorScheme(.dark)
        .onChange(of: picked) { _, item in if let item { Task { await upload(item) } } }
    }

    // ---- photo ----
    private var photoBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            PhotosPicker(selection: $picked, matching: .images) {
                ZStack {
                    Dt.card
                    if !draft.imageUrl.isEmpty, let url = URL(string: draft.imageUrl.cdn(800)) {
                        AsyncImage(url: url) { phase in if case .success(let img) = phase { img.resizable().scaledToFill() } else { ProgressView().tint(Dt.green) } }
                    } else {
                        VStack(spacing: 4) {
                            LgIcon(name: "ic_camera", color: Dt.textMuted, size: 38)
                            Text("Rasm tanlash").font(.system(size: 15, weight: .semibold)).foregroundStyle(Dt.textSecondary)
                            Text("JPG, PNG — 5 MB gacha").font(.system(size: 11)).foregroundStyle(Dt.textMuted)
                        }
                    }
                    if uploading {
                        Color.black.opacity(0.6)
                        VStack(spacing: 8) { ProgressView().tint(Dt.green); Text("Yuklanmoqda…").font(.system(size: 12)).foregroundStyle(.white) }
                    }
                }
                .frame(maxWidth: .infinity).aspectRatio(4.0 / 3.0, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Dt.line, lineWidth: 1))
            }.disabled(uploading)
            if !draft.imageUrl.isEmpty && !uploading {
                Button("Rasmni olib tashlash") { draft.imageUrl = "" }.font(.system(size: 12)).foregroundStyle(Dt.red)
            }
        }
    }

    private func upload(_ item: PhotosPickerItem) async {
        uploading = true; error = nil
        defer { uploading = false; picked = nil }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else { throw APIError.http(0, "Rasmni o'qib bo'lmadi") }
            draft.imageUrl = try await CloudinaryUploader.upload(image, api: model.api)
        } catch { self.error = error.localizedDescription }
    }

    private func submit() {
        if let e = draft.validate() { error = e; return }
        saving = true; error = nil
        Task {
            defer { saving = false }
            do { try await menu.save(id: dish?.id, json: draft.json(editing: dish)); dismiss() }
            catch { self.error = error.localizedDescription }
        }
    }

    // ---- hajm va qo'shimchalar (OptionGroupsEditor.jsx) ----
    @ViewBuilder private var groupsEditor: some View {
        Text("Hajm va qo'shimchalar").font(.system(size: 16, weight: .bold)).padding(.top, 6)
        Text("Masalan: «Hajmi» (33 sm / 40 sm — narx almashadi) yoki «Qo'shimcha» (pishloq, sous — narx qo'shiladi).").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
        ForEach($draft.groups) { $g in
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    field("Guruh nomi", $g.title, onEdit: { draft.groupsDirty = true })
                    Button { draft.groups.removeAll { $0.id == g.id }; draft.groupsDirty = true } label: { LgIcon(name: "ic_trash", color: Dt.red, size: 20).frame(width: 40, height: 40) }.buttonStyle(.plain)
                }
                HStack(spacing: 8) {
                    ForEach([("variant", "Hajm"), ("addon", "Qo'shimcha")], id: \.0) { kind, label in
                        Button { g.kind = kind; draft.groupsDirty = true } label: {
                            Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(g.kind == kind ? Dt.onAccent : Dt.textSecondary)
                                .padding(.horizontal, 14).padding(.vertical, 8).background(g.kind == kind ? Dt.green : Dt.cardHigh, in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain)
                    }
                }
                ForEach($g.options) { $o in
                    HStack(spacing: 6) {
                        field(g.kind == "variant" ? "Hajm (33 sm)" : "Nomi", $o.name, onEdit: { draft.groupsDirty = true })
                        field("Narx", digits($o.price), keyboard: .numberPad, onEdit: { draft.groupsDirty = true }).frame(maxWidth: 110)
                        if g.kind == "addon" {
                            VStack(spacing: 2) {
                                Toggle("", isOn: $o.mandatory).labelsHidden().tint(Dt.green).scaleEffect(0.8)
                                Text("Shart").font(.system(size: 9)).foregroundStyle(Dt.textMuted)
                            }
                        }
                        Button { g.options.removeAll { $0.id == o.id }; draft.groupsDirty = true } label: { LgIcon(name: "ic_close", color: Dt.textMuted, size: 16).frame(width: 32, height: 32) }.buttonStyle(.plain)
                    }
                }
                Button { g.options.append(OptionDraft()); draft.groupsDirty = true } label: {
                    Text(g.kind == "variant" ? "+ Hajm qo'shish" : "+ Qo'shimcha qo'shish").font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.greenText)
                }.buttonStyle(.plain)
            }
            .padding(12).background(Dt.cardStat, in: RoundedRectangle(cornerRadius: 16))
        }
        Button { draft.groups.append(GroupDraft()); draft.groupsDirty = true } label: {
            Text("+ Guruh qo'shish").font(.system(size: 14, weight: .semibold)).frame(maxWidth: .infinity).padding(14)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Dt.line, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    // ---- small helpers ----
    private func digits(_ b: Binding<String>) -> Binding<String> { Binding(get: { b.wrappedValue }, set: { b.wrappedValue = $0.filter(\.isNumber) }) }

    private func field(_ label: String, _ text: Binding<String>, lines: Int = 1, keyboard: UIKeyboardType = .default, hint: String? = nil, onEdit: (() -> Void)? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(.system(size: 11)).foregroundStyle(Dt.textMuted)
                if lines > 1 { TextField("", text: text, axis: .vertical).lineLimit(lines...lines + 3).keyboardType(keyboard) }
                else { TextField("", text: text).keyboardType(keyboard) }
            }
            .font(.system(size: 16)).foregroundStyle(Dt.text)
            .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 56)
            .background(Dt.card, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Dt.line, lineWidth: 1))
            .onChange(of: text.wrappedValue) { _, _ in onEdit?() }
            if let hint { Text(hint).font(.system(size: 11)).foregroundStyle(Dt.textMuted).padding(.leading, 6) }
        }
    }

    private func picker(_ label: String, _ value: String, _ options: [(String, String)], onPick: @escaping (String) -> Void) -> some View {
        Menu {
            ForEach(options, id: \.0) { v, l in Button(l) { onPick(v) } }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text(label).font(.system(size: 11)).foregroundStyle(Dt.textMuted)
                    Text(value).font(.system(size: 16)).foregroundStyle(Dt.text)
                }
                Spacer()
                LgIcon(name: "ic_chevron_down", color: Dt.textMuted, size: 18)
            }
            .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 56)
            .background(Dt.card, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Dt.line, lineWidth: 1))
        }
    }
}
