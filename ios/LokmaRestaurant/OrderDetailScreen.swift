import SwiftUI

struct OrderDetailScreen: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var menu: MenuStore
    @Environment(\.dismiss) private var dismiss
    @State private var lightbox: LightboxItem?
    let orderId: String
    private var order: RestaurantOrder? { model.orders.first { $0.id == orderId } }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { dismiss() } label: { LgIcon(name: "ic_arrow_left") }.buttonStyle(.plain)
                Text("Buyurtma #\(order?.number ?? "")").font(.system(size: 20, weight: .bold))
                if let o = order { StatusPill(status: o.status) }
                Spacer()
            }.padding(16)

            if let o = order {
                ScrollView { content(o).padding(.horizontal, Dt.spaceScreen).padding(.bottom, 8) }
                if let a = o.nextAction {
                    let accepted = o.status.lowercased() == "accepted"
                    PrimaryButton(title: a.title, icon: accepted ? "ic_chef_hat" : "ic_check_circle", color: accepted ? Dt.orange : Dt.green, loading: model.busy) {
                        Task { await model.update(o, to: a.status) }
                    }.padding(16)
                }
            } else {
                Spacer(); Text("Buyurtma topilmadi").foregroundStyle(Dt.textMuted); Spacer()
            }
        }
        .lgScreen()
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(item: $lightbox) { ImageLightbox(url: $0.url) }
    }

    @ViewBuilder private func content(_ o: RestaurantOrder) -> some View {
        VStack(alignment: .leading, spacing: Dt.spaceGap) {
            Text(hhmmDate(o.createdAt)).font(.system(size: 13)).foregroundStyle(Dt.textSecondary)

            SectionCard {
                HStack(spacing: 10) {
                    LgIcon(name: "ic_user", color: Dt.textSecondary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Mijoz").font(.system(size: 12)).foregroundStyle(Dt.textSecondary)
                        Text(o.customerName ?? "—").font(.system(size: 15, weight: .semibold))
                    }
                }
                if let phone = o.customerPhone, let url = URL(string: "tel:" + phone.filter { $0.isNumber || $0 == "+" }) {
                    Link(destination: url) {
                        HStack(spacing: 10) {
                            LgIcon(name: "ic_phone", color: Dt.green)
                            Text(phone).font(.system(size: 15, weight: .semibold)).foregroundStyle(Dt.green)
                        }
                    }.padding(.top, 10)
                }
                HStack(spacing: 24) {
                    kv(icon: o.paymentMethod.lowercased() == "cash" ? "ic_cash" : "ic_card", key: "To'lov usuli", value: o.paymentTitle)
                    kv(icon: "ic_delivery", key: "Yetkazib berish", value: o.deliveryFee > 0 ? som(o.deliveryFee) : "—")
                }.padding(.top, 12)
            }

            Text("Buyurtma tarkibi").font(.system(size: Dt.fontSection, weight: .bold))
            SectionCard {
                ForEach(Array(o.items.enumerated()), id: \.offset) { i, item in
                    if i > 0 { Rectangle().fill(Dt.line).frame(height: 1).padding(.vertical, 10) }
                    HStack(spacing: 12) {
                        // Order items carry only the dish id: the photo comes from the live menu. Tap = full size.
                        let photo = item.imageUrl ?? menu.imageURL(forDish: item.dishId)
                        DishThumb(url: photo, size: 56) { if let photo { lightbox = LightboxItem(url: photo) } }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name).font(.system(size: 15, weight: .semibold))
                            Text("× \(item.quantity)").font(.system(size: 12)).foregroundStyle(Dt.textSecondary)
                        }
                        Spacer()
                        Text(som(item.price * Int64(item.quantity))).font(.system(size: 14))
                    }
                }
                Rectangle().fill(Dt.line).frame(height: 1).padding(.vertical, 12)
                sum("Taomlar summasi", som(o.subtotal)); sum("Yetkazib berish", som(o.deliveryFee))
                sum("Jami", som(o.total), big: true).padding(.top, 6)
            }

            if let address = o.address {
                SectionCard {
                    Text("Mijoz manzili").font(.system(size: 12)).foregroundStyle(Dt.textSecondary)
                    HStack(alignment: .top, spacing: 10) { LgIcon(name: "ic_pin", color: Dt.green); Text(address) }.padding(.vertical, 8)
                    if let url = mapURL(o) {
                        Link(destination: url) {
                            HStack(spacing: 8) {
                                LgIcon(name: "ic_map", color: Dt.green, size: 18)
                                Text("Xaritada ko'rish").font(.system(size: 15, weight: .semibold)).foregroundStyle(Dt.green)
                            }
                            .frame(maxWidth: .infinity).padding(12).background(Dt.green.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }
            }
        }
    }

    private func kv(icon: String, key: String, value: String) -> some View {
        HStack(spacing: 10) {
            LgIcon(name: icon, color: Dt.textSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(key).font(.system(size: 12)).foregroundStyle(Dt.textSecondary)
                Text(value).font(.system(size: 15, weight: .semibold))
            }
        }
    }

    private func sum(_ k: String, _ v: String, big: Bool = false) -> some View {
        HStack {
            Text(k).font(.system(size: big ? 17 : 13, weight: big ? .bold : .regular)).foregroundStyle(big ? Dt.text : Dt.textSecondary)
            Spacer()
            Text(v).font(.system(size: big ? 20 : 13, weight: big ? .heavy : .regular))
        }
    }

    private func mapURL(_ o: RestaurantOrder) -> URL? {
        if let lat = o.lat, let lng = o.lng { return URL(string: "http://maps.apple.com/?ll=\(lat),\(lng)&q=Mijoz") }
        guard let q = o.address?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return nil }
        return URL(string: "http://maps.apple.com/?q=\(q)")
    }
}
