package uz.lokmago.restaurant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.alert.Notifier
import uz.lokmago.restaurant.alert.OrderListenerService
import uz.lokmago.restaurant.alert.OrderWatchdog
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.di.AppScope

@HiltAndroidApp
class LokmaGoApp : Application() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var courierNotifier: uz.lokmago.restaurant.courier.CourierNotifier
    @Inject lateinit var session: SessionStore
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        courierNotifier.ensureChannels()
        // Logged in => the listener runs (screen off, app swiped away, after reboot). Logged out => it stops.
        // Backup watchdog: while logged in, every ~15 min reconnect + re-read pending orders (alert/OrderWatchdog.kt)
        scope.launch { session.session.map { it != null }.distinctUntilChanged().collect { if (it) OrderWatchdog.schedule(this@LokmaGoApp) else OrderWatchdog.cancel(this@LokmaGoApp) } }
        scope.launch {
            // logged in AND the user did not switch it off in Sozlamalar (FCM keeps working either way)
            combine(session.session.map { it != null }.distinctUntilChanged(), session.persistentListener) { loggedIn, on -> loggedIn && on }
                .distinctUntilChanged().collect { run -> if (run) OrderListenerService.start(this@LokmaGoApp) else OrderListenerService.stop(this@LokmaGoApp) }
        }
    }
}
