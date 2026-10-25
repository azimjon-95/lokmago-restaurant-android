# LokmaGo Restaurant for iOS

Native SwiftUI companion app for the restaurant staff Android app. It uses the same restaurant API contract and bundle identifier `uz.lokmago.restaurant.ios`.

Open `LokmaRestaurant.xcodeproj` in Xcode, select the `LokmaRestaurant` scheme, select the connected iPhone, then Run. The API base URL is configured in `Config.swift`; it must present a valid TLS certificate for its hostname before the app can connect.

The app includes login, secure token storage in Keychain, order list/detail, order status actions, today's stats, and foreground order polling. Remote push notifications, background delivery reminders, and App Store signing still require APNs/backend integration and an Apple Developer provisioning profile.
