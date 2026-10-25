package uz.lokmago.restaurant.alert

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.realtime.OrderCoordinator

/**
 * Keeps the process and the Socket.IO connection alive while a restaurant is logged in — also when the
 * screen is off, the phone is in a pocket, or the user swiped the app away. Without it Android may freeze
 * the app and a new order would only be noticed when someone opens it. A new order then goes through the
 * normal path (OrderCoordinator → OrderAlertService: alarm sound + vibration + full-screen alert).
 *
 * It is a plain "specialUse" foreground service with a quiet, permanent notification.
 */
@AndroidEntryPoint
class OrderListenerService : Service() {
    @Inject lateinit var coordinator: OrderCoordinator
    @Inject lateinit var session: SessionStore
    @Inject lateinit var notifier: Notifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (session.token == null) { stopSelf(); return START_NOT_STICKY }   // logged out
        ServiceCompat.startForeground(this, Notifier.LISTENER_ID, notifier.listenerNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        coordinator.start()                                                // idempotent: (re)connects the socket
        return START_STICKY                                                 // the system recreates it after a kill
    }

    companion object {
        /** Never throws: starting a foreground service from the background can be refused by the OS. */
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, OrderListenerService::class.java)) }
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OrderListenerService::class.java)) }
    }
}
