package uz.lokmago.restaurant.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uz.lokmago.restaurant.data.local.SessionStore

/** After a reboot or an app update the listener must come back by itself — the restaurant should not have to open the app. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var session: SessionStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (session.token != null) OrderListenerService.start(context)
        }
    }
}
