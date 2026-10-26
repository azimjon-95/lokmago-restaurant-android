import SwiftUI

/// Small dish photo (or a plain icon when there is none). Tap = [onTap] (used to open the big picture).
struct DishThumb: View {
    let url: String?
    var size: CGFloat = 56
    var grayscale = false
    var onTap: (() -> Void)? = nil
    var body: some View {
        Group {
            if let url, !url.isEmpty, let link = URL(string: url.cdn(Int(size * 3))) {
                AsyncImage(url: link) { phase in
                    switch phase {
                    case .success(let image): image.resizable().scaledToFill()
                    default: Dt.cardHigh
                    }
                }
            } else {
                ZStack { Dt.cardHigh; LgIcon(name: "ic_utensils", color: Dt.textMuted, size: size / 2.4) }
            }
        }
        .frame(width: size, height: size).clipShape(RoundedRectangle(cornerRadius: 12))
        .saturation(grayscale ? 0 : 1)
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture { if url?.isEmpty == false { onTap?() } }
    }
}

struct LightboxItem: Identifiable { let id = UUID(); let url: String }

/// Full-screen photo. Pinch to zoom, double-tap to zoom in/out, tap or ✕ to close.
struct ImageLightbox: View {
    let url: String
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var base: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var dragBase: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(0.96).ignoresSafeArea().onTapGesture { dismiss() }
            AsyncImage(url: URL(string: url.cdnFit(1400))) { phase in
                switch phase {
                case .success(let image): image.resizable().scaledToFit()
                case .failure: LgIcon(name: "ic_image", color: Dt.textMuted, size: 48)
                default: ProgressView().tint(.white)
                }
            }
            .scaleEffect(scale).offset(offset)
            .gesture(
                MagnificationGesture()
                    .onChanged { scale = min(max(base * $0, 1), 5) }
                    .onEnded { _ in base = scale; if scale == 1 { offset = .zero; dragBase = .zero } }
                    .simultaneously(with: DragGesture().onChanged { if scale > 1 { offset = CGSize(width: dragBase.width + $0.translation.width, height: dragBase.height + $0.translation.height) } }
                        .onEnded { _ in dragBase = offset })
            )
            .onTapGesture(count: 2) { withAnimation { scale = scale > 1 ? 1 : 2.5; base = scale; if scale == 1 { offset = .zero; dragBase = .zero } } }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Button { dismiss() } label: {
                LgIcon(name: "ic_close", color: .white, size: 22).frame(width: 44, height: 44).background(Color.white.opacity(0.16), in: Circle())
            }.buttonStyle(.plain).padding(16)
        }
        .preferredColorScheme(.dark)
    }
}
