import SwiftUI

// Shared building blocks — one-to-one with ui/components/Components.kt on Android.

struct LgIcon: View {
    let name: String
    var color: Color = Dt.text
    var size: CGFloat = 22
    var body: some View {
        Image(name).renderingMode(.template).resizable().scaledToFit().frame(width: size, height: size).foregroundStyle(color)
    }
}

struct StatusPill: View {
    let status: String
    var body: some View {
        Text(StatusStyle.title(status))
            .font(.system(size: Dt.fontPill, weight: .semibold)).foregroundStyle(StatusStyle.pillFg(status))
            .padding(.horizontal, 14).padding(.vertical, 6).background(StatusStyle.pillBg(status), in: Capsule())
    }
}

/// "● Online ⌄" (header) — text, not a filled pill.
extension ConnectionState {
    var color: Color { self == .online ? Dt.greenText : (self == .connecting ? Dt.orange : Dt.red) }
    var title: String { self == .online ? "Online" : (self == .connecting ? "Ulanmoqda…" : "Offline") }
}

struct ConnectionLabel: View {
    let state: ConnectionState
    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(state.color).frame(width: 8, height: 8)
            Text(state.title).font(.system(size: 14, weight: .semibold)).foregroundStyle(state.color)
            LgIcon(name: "ic_chevron_down", color: Dt.textSecondary, size: 16)
        }
    }
}

struct OnlineChip: View {
    let state: ConnectionState
    var body: some View {
        let c = state.color
        HStack(spacing: 6) {
            Circle().fill(c).frame(width: 8, height: 8)
            Text(state.title).font(.system(size: 12, weight: .semibold)).foregroundStyle(c)
        }
        .padding(.horizontal, 10).padding(.vertical, 5).background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct BellButton: View {
    let count: Int
    var action: () -> Void = {}
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: action) {
                LgIcon(name: count > 0 ? "ic_bell_ring" : "ic_bell", size: 22)
                    .frame(width: Dt.sizeBell, height: Dt.sizeBell).background(Dt.cardHigh, in: Circle())
            }.buttonStyle(.plain).padding(.top, 8).padding(.trailing, 4)
            if count > 0 {
                Text("\(count)").font(.system(size: 11, weight: .bold)).foregroundStyle(Dt.onAccent)
                    .padding(.horizontal, 6).padding(.vertical, 2).background(Dt.red, in: Capsule())
            }
        }
    }
}

struct OrderRow: View {
    let order: RestaurantOrder
    var highlight = false
    var compact = false
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Dt.radiusCard)
        HStack(spacing: 0) {
            Rectangle().fill(StatusStyle.bar(order.status)).frame(width: Dt.spaceBar)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("#\(order.number)").font(.system(size: Dt.fontOrderNo, weight: .heavy))
                    Spacer()
                    StatusPill(status: order.status)
                }
                HStack(spacing: 6) {
                    LgIcon(name: "ic_clock", color: Dt.textSecondary, size: 14)
                    Text("\(hhmm(order.createdAt)) • \(som(order.total))").font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textSecondary)
                }
                if !compact {
                    HStack(spacing: 6) {
                        LgIcon(name: "ic_utensils", color: Dt.textSecondary, size: 14)
                        Text("\(order.itemsCount) ta taom").font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textSecondary)
                        Text("•").foregroundStyle(Dt.textSecondary)
                        LgIcon(name: order.paymentMethod.lowercased() == "cash" ? "ic_cash" : "ic_card", color: Dt.textSecondary, size: 14)
                        Text(order.paymentTitle).font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textSecondary)
                    }
                }
            }
            .padding(.horizontal, Dt.spaceCardPad).padding(.vertical, 12)
            if !compact { LgIcon(name: "ic_chevron_right", color: Dt.blue.opacity(0.45), size: 16).padding(.trailing, 10) }
        }
        .background(Dt.card)
        .clipShape(shape)
        .overlay(shape.stroke(highlight || order.status.lowercased() == "pending" ? Dt.red.opacity(0.55) : Dt.line, lineWidth: 1))
    }
}

struct IconBox: View {
    let icon: String; let bg: Color; let tint: Color
    var body: some View {
        LgIcon(name: icon, color: tint, size: 24).frame(width: Dt.sizeIconBox, height: Dt.sizeIconBox)
            .background(bg, in: RoundedRectangle(cornerRadius: Dt.radiusIconBox))
    }
}

struct StatCard<Trailing: View>: View {
    let label: String; let value: String; var delta: String? = nil
    @ViewBuilder var trailing: () -> Trailing
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.system(size: Dt.fontMeta)).foregroundStyle(Dt.textSecondary)
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(value).font(.system(size: Dt.fontBig, weight: .heavy))
                    if let delta { Text(delta).font(.system(size: Dt.fontLabel)).foregroundStyle(Dt.greenText) }
                }
            }
            Spacer(minLength: 8)
            trailing()
        }
        .padding(Dt.spaceCardPad).frame(maxWidth: .infinity, alignment: .leading)
        .background(Dt.cardStat, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
    }
}
extension StatCard where Trailing == EmptyView {
    init(label: String, value: String, delta: String? = nil) { self.init(label: label, value: value, delta: delta, trailing: { EmptyView() }) }
}

struct TabChip: View {
    let label: String; let count: Int; let selected: Bool; let color: Color; let badge: Color
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: Dt.fontTab, weight: .semibold)).foregroundStyle(Dt.text)
                .padding(.horizontal, 18).padding(.vertical, 14)
                .background(selected ? color : Dt.cardHigh, in: RoundedRectangle(cornerRadius: Dt.radiusTab))
        }
        .buttonStyle(.plain)
        .overlay(alignment: .topTrailing) {
            if count > 0 {
                Text("\(count)").font(.system(size: 11, weight: .bold)).foregroundStyle(selected ? color : Dt.onAccent)
                    .padding(.horizontal, 6).padding(.vertical, 2).background(selected ? Color.white : badge, in: Capsule())
                    .offset(x: 4, y: -6)
            }
        }
    }
}

struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var color: Color = Dt.green
    var loading = false
    var enabled = true
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading { ProgressView().tint(Dt.onAccent) }
                else {
                    if let icon { LgIcon(name: icon, color: Dt.onAccent, size: 20) }
                    Text(title).font(.system(size: 16, weight: .bold)).foregroundStyle(Dt.onAccent)
                }
            }
            .frame(maxWidth: .infinity).frame(height: Dt.sizeButton)
            .background(color.opacity(enabled && !loading ? 1 : 0.4), in: RoundedRectangle(cornerRadius: Dt.radiusButton))
        }
        .buttonStyle(.plain).disabled(!enabled || loading)
    }
}

struct SectionCard<Content: View>: View {
    var bg: Color = Dt.card
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(Dt.spaceCardPad).frame(maxWidth: .infinity, alignment: .leading)
            .background(bg, in: RoundedRectangle(cornerRadius: Dt.radiusCard))
    }
}

struct BrandHeader: View {
    var size: CGFloat = 22
    var body: some View {
        HStack(spacing: 10) {
            LgIcon(name: "ic_chef_hat", color: Dt.orange, size: size + 18)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 0) {
                    Text("Lokma").foregroundStyle(Dt.text); Text("Go").foregroundStyle(Dt.orange)
                }.font(.system(size: size, weight: .heavy))
                Text("Restoran").font(.system(size: 13)).foregroundStyle(Dt.textMuted)
            }
        }
    }
}
