import Foundation

/// Minimal Socket.IO v4 client (Engine.IO v4 over WebSocket) — exactly what the order feed needs, no third-party package.
///   server → "0{...}"            Engine.IO open       client → "40{\"token\":\"<JWT>\"}"  join the default namespace with the JWT
///   server → "40{...}"           connected            server → "2"  ping                   client → "3"  pong
///   server → "42[\"order:new\",{…}]"  event           server → "44{...}"  rejected (bad token)
/// The JWT travels in the handshake `auth` object; the server puts the socket into the restaurant's own room, so the
/// client never names a restaurant. The payload is used only as a SIGNAL — the app reloads the order list from the API.
///
/// WHY it needs more than a plain reconnect loop: while the app is in the background iOS FREEZES it. The TCP connection then
/// dies silently (the server drops the socket after its ping timeout) but the old `receive()` never throws. So on every
/// return to the foreground and every network change the app calls [reconnectNow], and a watchdog kills a connection
/// that has heard nothing for longer than the server's ping interval + timeout.
@MainActor
final class RealtimeSocket {
    var onConnected: (() -> Void)?
    var onDisconnected: (() -> Void)?
    /// (event name, JSON object payload if there is one)
    var onEvent: ((String, [String: Any]?) -> Void)?

    private var loop: Task<Void, Never>?
    private var token: String?
    private var current: URLSessionWebSocketTask?
    private var generation = 0                 // callbacks of an OLD connection must never overwrite the state of the new one
    private var lastActivity = Date()
    private var idleLimit: TimeInterval = 50   // pingInterval + pingTimeout + slack; the server states the real values in its open packet

    func connect(token: String, force: Bool = false) {
        if !force, self.token == token, loop != nil { return }
        stop()
        self.token = token
        generation += 1
        let gen = generation
        loop = Task { [weak self] in await self?.run(token: token, gen: gen) }
    }

    /// The app is back in the foreground / the network came back: the old socket is probably dead — do not wait for a backoff.
    func reconnectNow() { if let token { connect(token: token, force: true) } }

    func disconnect() { stop(); token = nil }

    private func stop() {
        generation += 1                         // invalidates callbacks of the connection being torn down
        loop?.cancel(); loop = nil
        current?.cancel(with: .goingAway, reason: nil); current = nil
    }

    /// Reconnects forever: first retry after 0.5 s, then backoff up to 8 s.
    private func run(token: String, gen: Int) async {
        var delay = 0.5
        while !Task.isCancelled, gen == generation {
            let joined = await session(token: token, gen: gen)
            if gen == generation { onDisconnected?() }
            if Task.isCancelled || gen != generation { break }
            delay = joined ? 0.5 : min(delay * 2, 8)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        }
    }

    /// One WebSocket lifetime. Returns true if the namespace was joined at least once.
    private func session(token: String, gen: Int) async -> Bool {
        let task = URLSession.shared.webSocketTask(with: AppConfig.socketURL)
        current = task
        lastActivity = Date()
        task.resume()
        var joined = false

        // Watchdog: a half-open connection (frozen app, dead radio) never throws — it just goes silent.
        let watchdog = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                guard let self, gen == self.generation else { return }
                if Date().timeIntervalSince(self.lastActivity) > self.idleLimit { task.cancel(with: .goingAway, reason: nil); return }
            }
        }
        defer { watchdog.cancel() }

        do {
            while !Task.isCancelled {
                let message = try await task.receive()
                lastActivity = Date()
                let text: String
                switch message {
                case .string(let s): text = s
                case .data(let d): text = String(decoding: d, as: UTF8.self)
                @unknown default: continue
                }
                if text.hasPrefix("0") {
                    idleLimit = Self.idleLimit(fromOpenPacket: text)
                    let auth = try JSONSerialization.data(withJSONObject: ["token": token])
                    try await task.send(.string("40" + String(decoding: auth, as: UTF8.self)))
                } else if text == "2" {
                    try await task.send(.string("3"))
                } else if text.hasPrefix("40") {
                    joined = true
                    if gen == generation { onConnected?() }
                } else if text.hasPrefix("44") {
                    break                                           // token rejected: stop this socket, the REST layer will sign out on 401
                } else if text.hasPrefix("42"), let event = Self.parseEvent(text) {
                    if gen == generation { onEvent?(event.name, event.payload) }
                }
            }
        } catch { /* network drop / watchdog: fall through and reconnect */ }
        task.cancel(with: .goingAway, reason: nil)
        return joined
    }

    /// `0{"sid":"…","pingInterval":25000,"pingTimeout":20000}` → (25 + 20) s + 5 s slack. Falls back to 50 s.
    static func idleLimit(fromOpenPacket packet: String) -> TimeInterval {
        guard let data = String(packet.dropFirst()).data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let interval = json["pingInterval"] as? Double, let timeout = json["pingTimeout"] as? Double else { return 50 }
        return (interval + timeout) / 1000 + 5
    }

    /// `42["dish:stop",{"stoppedCount":3}]` → ("dish:stop", ["stoppedCount": 3])  (an optional numeric ack id after "42" is skipped)
    static func parseEvent(_ packet: String) -> (name: String, payload: [String: Any]?)? {
        let body = packet.dropFirst(2).drop(while: { $0.isNumber })
        guard let data = String(body).data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [Any],
              let name = array.first as? String else { return nil }
        return (name, array.count > 1 ? array[1] as? [String: Any] : nil)
    }
}
