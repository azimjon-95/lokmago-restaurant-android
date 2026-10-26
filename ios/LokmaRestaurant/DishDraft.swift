import Foundation

/*
 Taom formasining holati va serverga yuboriladigan JSON — admin panelning DishForm (MenuPage.jsx) va Android bilan AYNAN bir xil:
 bo'lim kategoriya nomidan olinadi, faqat to'ldirilgan maydonlar yuboriladi, hajm/qo'shimcha guruhlari faqat o'zgartirilgan bo'lsa
 yuboriladi (eski ma'lumot tasodifan buzilmasin).
*/
struct OptionDraft: Identifiable { var id = UUID(); var name = ""; var price = ""; var mandatory = false }
struct GroupDraft: Identifiable { var id = UUID(); var title = ""; var kind = "addon"; var options: [OptionDraft] = [OptionDraft()] }

func money(_ s: String) -> Int64 { Int64(s.filter(\.isNumber)) ?? 0 }
private func decimal(_ s: String) -> Double? { Double(s.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)) }

/// lib/dishPricing.js `isVariantGroup`: eski guruhlarda `kind` yo'q — nom va narxdan aniqlanadi.
func isVariantGroup(_ g: DishGroup, basePrice: Int64) -> Bool {
    if g.kind == "variant" { return true }
    if g.kind == "addon" { return false }
    guard g.options.count >= 2, basePrice > 0 else { return false }
    let words = "(hajm|razmer|o['‘ʻ’`]?lcham|porsiya|portsiya|gramm?|\\bgr\\b|size|litr|объ[её]м|размер|порци|вес|литр)"
    guard g.title.range(of: words, options: [.regularExpression, .caseInsensitive]) != nil else { return false }
    return g.options.allSatisfy { Double($0.price) >= Double(basePrice) * 0.5 }
}

struct DishDraft {
    var imageUrl = "", name = "", description = "", category = "milliy"
    var price = "", oldPrice = "", prepMinutes = "15"
    var weight = "", volume = "", drinkType = ""
    var calories = "", protein = "", fat = "", carbs = ""
    var priceMode = "sync", dineInPrice = ""
    var groups: [GroupDraft] = []
    var groupsDirty = false

    init() {}
    init(_ d: Dish) {
        imageUrl = d.imageUrl ?? ""; name = d.name; description = d.description; category = d.category
        price = String(d.price); oldPrice = d.oldPrice.map(String.init) ?? ""; prepMinutes = String(d.prepMinutes ?? 15)
        weight = d.weight; volume = d.volume; drinkType = d.drinkType
        calories = d.calories.map(String.init) ?? ""; protein = d.protein.map(String.init) ?? ""
        fat = d.fat.map(String.init) ?? ""; carbs = d.carbs.map(String.init) ?? ""
        priceMode = d.priceMode; dineInPrice = d.dineInPrice.map(String.init) ?? ""
        groups = d.groups.map { g in
            GroupDraft(title: g.title, kind: isVariantGroup(g, basePrice: d.price) ? "variant" : "addon",
                       options: g.options.map { OptionDraft(name: $0.name, price: $0.price == 0 ? "" : String($0.price), mandatory: $0.mandatory) })
        }
    }

    static func validateGroups(_ groups: [GroupDraft]) -> String? {
        for g in groups {
            if g.title.trimmingCharacters(in: .whitespaces).isEmpty { return "Guruh nomini kiriting" }
            let filled = g.options.filter { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty }
            if g.kind == "variant" {
                if filled.count < 2 { return "\"\(g.title)\": kamida 2 ta hajm kiriting" }
                if filled.contains(where: { money($0.price) <= 0 }) { return "\"\(g.title)\": har bir hajmga narx yozing" }
            } else if filled.isEmpty { return "\"\(g.title)\": kamida bitta qo'shimcha kiriting" }
        }
        return nil
    }

    func validate() -> String? {
        if name.trimmingCharacters(in: .whitespaces).isEmpty { return "Taom nomini kiriting" }
        if money(price) <= 0 { return "Narxni kiriting" }
        if groupsDirty, let e = Self.validateGroups(groups) { return e }
        return nil
    }

    /// [editing] = taom tahrirlanayotgan bo'lsa (matn maydonlarini va rasmni tozalash mumkin bo'lishi uchun).
    func json(editing: Dish? = nil) -> [String: Any] {
        let edit = editing != nil
        var j: [String: Any] = [
            "section": DishCategories.label(category), "category": category,
            "name": name.trimmingCharacters(in: .whitespaces), "description": description.trimmingCharacters(in: .whitespacesAndNewlines),
            "price": money(price), "prepMinutes": min(max(Int(prepMinutes) ?? 15, 1), 240), "priceMode": priceMode,
        ]
        if money(oldPrice) > 0 { j["oldPrice"] = money(oldPrice) }
        if edit || !weight.isEmpty { j["weight"] = weight.trimmingCharacters(in: .whitespaces) }
        if edit || !volume.isEmpty { j["volume"] = volume.trimmingCharacters(in: .whitespaces) }
        if edit || !drinkType.isEmpty { j["drinkType"] = drinkType }
        for (key, value) in [("calories", calories), ("protein", protein), ("fat", fat), ("carbs", carbs)] {
            if let v = decimal(value), v > 0 { j[key] = v }
        }
        if priceMode == "custom", money(dineInPrice) > 0 { j["dineInPrice"] = money(dineInPrice) }
        if !imageUrl.isEmpty { j["imageUrl"] = imageUrl; j["images"] = [imageUrl] }
        else if editing?.imageUrl != nil { j["imageUrl"] = ""; j["images"] = [String]() }       // rasm olib tashlandi
        if groupsDirty {
            j["optionGroups"] = groups.map { g -> [String: Any] in
                ["title": g.title.trimmingCharacters(in: .whitespaces), "kind": g.kind,
                 "options": g.options.filter { !$0.name.trimmingCharacters(in: .whitespaces).isEmpty }.map { o -> [String: Any] in
                    var d: [String: Any] = ["name": o.name.trimmingCharacters(in: .whitespaces), "price": money(o.price)]
                    if g.kind == "addon" { d["mandatory"] = o.mandatory }           // faqat qo'shimchada ma'noli
                    return d
                }]
            }
        }
        return j
    }
}
