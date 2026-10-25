import Foundation

/// Minimal Socket.IO v4 client (Engine.IO v4 over WebSocket) — exactly what the order feed needs, no third-party package.
///   server → "0{...}"            Engine.IO open       client → "40{\"token\":\"<JWT>\"}"  join the default namespace with the JWT
///   server → "40{...}"           connected            server → "2"  ping                   client → "3"  pong
///   server → "42[\"order:new\",{…}]"  event           server → "44{...}"  rejected (bad token)
/// The JWT travels in the handshake `auth` object; the server puts the socket into the restaurant's own room, so the
/// client never names a restaurant. The payload is used only as a SIGNAL — the app reloads the order list from the API.
final class RealtimeSocket {
    var onConnected: (() -> Void)?
    var onDisconnected: (() -> Void)?
    var onEvent: ((String) -> Void)?

    private var loop: Task<Void, Never>?
    private var token: String?
    private var current: URLSessionWebSocketTask?

    func connect(token: String) {
        if self.token == token, loop != nil { return }
        disconnect()
        self.token = token
        loop = Task { [weak self] in await self?.run(token: token) }
    }

    func disconnect() {
        loop?.cancel(); loop = nil
        current?.cancel(with: .goingAway, reason: nil); current = nil
        token = nil
    }

    /// Reconnects forever with backoff 1s → 10s (same as Android).
    private func run(token: String) async {
        var delay = 1.0
        while !Task.isCancelled {
            let reachedConnected = await session(token: token)
            onDisconnected?()
            if Task.isCancelled { break }
            delay = reachedConnected ? 1.0 : min(delay * 2, 10)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        }
    }

    /// One WebSocket lifetime. Returns true if the namespace was joined at least once.
    private func session(token: String) async -> Bool {
        let task = URLSession.shared.webSocketTask(with: AppConfig.socketURL)
        current = task
        task.resume()
        var joined = false
        do {
            while !Task.isCancelled {
                let message = try await task.receive()
                let text: String
                switch message {
                case .string(let s): text = s
                case .data(let d): text = String(decoding: d, as: UTF8.self)
                @unknown default: continue
                }
                if text.hasPrefix("0") {
                    let auth = try JSONSerialization.data(withJSONObject: ["token": token])
                    try await task.send(.string("40" + String(decoding: auth, as: UTF8.self)))
                } else if text == "2" {
                    try await task.send(.string("3"))
                } else if text.hasPrefix("40") {
                    joined = true; onConnected?()
                } else if text.hasPrefix("44") {
                    break                                           // token rejected: stop this socket, the REST layer will sign out on 401
                } else if text.hasPrefix("42"), let name = Self.eventName(text) {
                    onEvent?(name)
                }
            }
        } catch { /* network drop: fall through and reconnect */ }
        task.cancel(with: .goingAway, reason: nil)
        return joined
    }

    /// `42["order:new",{...}]` → "order:new"  (an optional numeric ack id after "42" is skipped)
    static func eventName(_ packet: String) -> String? {
        let body = packet.dropFirst(2).drop(while: { $0.isNumber })
        guard let data = String(body).data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [Any],
              let name = array.first as? String else { return nil }
        return name
    }
}
