import SwiftUI

@main struct LokmaRestaurantApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()
    @StateObject private var courier = CourierModel()
    var body: some Scene {
        WindowGroup { EntryView().environmentObject(model).environmentObject(model.menu).environmentObject(courier) }
    }
}
