import SwiftUI

@main struct LokmaRestaurantApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(model).environmentObject(model.menu) }
    }
}
