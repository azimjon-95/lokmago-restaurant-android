import SwiftUI

/// Full-screen new-order alert (mockup screen 3). Shows the OLDEST waiting order; the rest wait their turn.
/// "Signalni to'xtatish" only mutes — it never accepts. The alarm keeps going until the queue is empty.
struct AlertScreen: View {
    @EnvironmentObject private var model: AppModel
    let openOrder: (String) -> Void
    @State private var pulse = false

    var body: some View {
        if let head = model.queue.first { content(head) }
    }

    private func content(_ head: RestaurantOrder) -> some View {
        let waiting = Array(model.queue.dropFirst())
        return VStack(spacing: 0) {
            HStack {
                if model.queue.count > 1 { Text("Navbat: 1 / \(model.queue.count)").font(.system(size: 13, weight: .semibold)).foregroundStyle(Dt.textMuted) }
                Spacer()
                Button { model.minimize() } label: { LgIcon(name: "ic_close", color: Dt.textMuted).frame(width: 40, height: 40) }.buttonStyle(.plain)
            }
            BrandHeader(size: 20)

            Spacer()
            LgIcon(name: "ic_cloche", color: Dt.orange, size: 84)
            Text("YANGI BUYURTMA").font(.system(size: 13, weight: .heavy)).tracking(1).foregroundStyle(.white)
                .padding(.horizontal, 18).padding(.vertical, 8).background(Dt.red, in: Capsule())
                .opacity(model.silenced ? 1 : (pulse ? 0.55 : 1)).padding(.top, 16)
            Text("#\(head.number)").font(.system(size: 60, weight: .heavy)).padding(.top, 14)
            Text(som(head.total)).font(.system(size: 26, weight: .semibold))
            HStack(spacing: 20) {
                meta("ic_utensils", "\(head.itemsCount) ta taom")
                meta(head.paymentMethod.lowercased() == "cash" ? "ic_cash" : "ic_card", "\(head.paymentTitle) to'lov")
                meta(head.fulfillment == "delivery" ? "ic_delivery" : "ic_bag", head.fulfillment == "delivery" ? "Yetkazib berish" : "Olib ketish")
            }.padding(.top, 14)

            if !waiting.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        LgIcon(name: "ic_queue", color: Dt.orange, size: 18)
                        Text("Navbatda yana \(waiting.count) ta").font(.system(size: 14, weight: .semibold))
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(waiting) { o in
                                Text("#\(o.number) • \(som(o.total))").font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                                    .padding(.horizontal, 12).padding(.vertical, 6).background(Dt.header, in: Capsule())
                            }
                        }
                    }
                }
                .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Dt.card, in: RoundedRectangle(cornerRadius: 16)).padding(.top, 22)
            }
            Spacer()

            LgIcon(name: model.silenced ? "ic_volume_off" : "ic_volume", color: Dt.red, size: 30)
                .frame(width: 64, height: 64).background(Dt.red.opacity(0.22), in: Circle())
            Text(model.hasInternet ? (model.silenced ? "Signal o'chirilgan. Buyurtma hali qabul qilinmagan." : "Buyurtmani qabul qilmaguncha signal davom etadi…") : "Internet yo'q — qabul qilish uchun ulanish kerak")
                .font(.system(size: 13)).foregroundStyle(model.hasInternet ? Dt.textMuted : Dt.orange).multilineTextAlignment(.center).padding(.top, 10)

            HStack(alignment: .bottom, spacing: 14) {
                Button { model.silence() } label: {
                    VStack(spacing: 6) {
                        LgIcon(name: "ic_close", color: Dt.onAccent, size: 30).frame(width: 64, height: 64)
                            .background(Dt.red.opacity(model.silenced ? 0.35 : 1), in: Circle())
                        Text("Signalni to'xtatish").font(.system(size: 12)).multilineTextAlignment(.center)
                    }.frame(width: 96)
                }.buttonStyle(.plain).disabled(model.silenced)

                Button { Task { await model.update(head, to: "accepted") } } label: {
                    Group {
                        if model.busy { ProgressView().tint(Dt.onAccent) }
                        else {
                            VStack(spacing: 6) {
                                LgIcon(name: "ic_check_circle", color: Dt.onAccent, size: 36)
                                Text("Qabul qilish").font(.system(size: 17, weight: .heavy)).foregroundStyle(Dt.onAccent)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity).frame(height: 104)
                    .background(LinearGradient(colors: [Dt.green, Dt.greenEnd], startPoint: .top, endPoint: .bottom).opacity(model.hasInternet && !model.busy ? 1 : 0.4),
                                in: RoundedRectangle(cornerRadius: 28))
                }.buttonStyle(.plain).disabled(!model.hasInternet || model.busy)
            }.padding(.top, 14)

            Button("Tarkibini ko'rish") { openOrder(head.id) }
                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Dt.green).padding(12)
        }
        .padding(.horizontal, 24).padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .lgScreen()
        .onAppear { withAnimation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true)) { pulse = true } }
    }

    private func meta(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 6) { LgIcon(name: icon, color: Dt.textMuted, size: 18); Text(text).font(.system(size: 13)) }
    }
}
