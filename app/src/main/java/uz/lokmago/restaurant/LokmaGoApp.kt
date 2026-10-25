package uz.lokmago.restaurant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.alert.Notifier
import uz.lokmago.restaurant.alert.OrderListenerService
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.di.AppScope

@HiltAndroidApp
class LokmaGoApp : Application() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var session: SessionStore
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        // Logged in => the listener runs (screen off, app swiped away, after reboot). Logged out => it stops.
        scope.launch {
            session.session.map { it != null }.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) OrderListenerService.start(this@LokmaGoApp) else OrderListenerService.stop(this@LokmaGoApp)
            }
        }
    }
}
