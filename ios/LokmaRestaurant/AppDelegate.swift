import UIKit
import UserNotifications

/// Push plumbing. SwiftUI's App has no delegate callbacks for APNs, so this is attached with @UIApplicationDelegateAdaptor.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { @MainActor in AppModel.shared?.registerPushToken(hex) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) { /* no push (simulator / no entitlement): the app still works while open */ }

    /// Push while the app is open: the in-app alarm screen takes over, so no banner — just reload the orders.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        await MainActor.run { AppModel.shared?.pushArrived() }
        return []
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let orderId = response.notification.request.content.userInfo["orderId"] as? String
        let action = response.actionIdentifier
        await MainActor.run {
            if action == "ORDER_ACCEPT", let orderId { AppModel.shared?.handleOrderNotificationAction(orderId: orderId, status: "accepted") }
            else if action == "ORDER_CANCEL", let orderId { AppModel.shared?.handleOrderNotificationAction(orderId: orderId, status: "cancelled") }
            else { AppModel.shared?.pushArrived() }
        }
    }

    /// Silent / content-available push: refresh quickly so the list is current when the user opens the app.
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        await MainActor.run { AppModel.shared?.pushArrived() }
        return .newData
    }
}
