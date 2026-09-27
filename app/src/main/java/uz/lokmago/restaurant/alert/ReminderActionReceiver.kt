package uz.lokmago.restaurant.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.di.AppScope
import uz.lokmago.restaurant.realtime.OrderCoordinator

/**
 * Handles the two direct actions on a delivery-reminder notification ("🟢 Yetkazildi" /
 * "🟡 Jarayonda") without opening the app — same as tapping the equivalent Telegram button.
 * Both actions go through [OrderCoordinator.reminderAck], the same completion path the in-app
 * OrderDetailScreen button uses; nothing here decides completion on its own (TZ §18/§20/§21).
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: OrderCoordinator
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: return
        val delivered = intent.action == ACTION_DELIVERED
        val pending = goAsync()
        scope.launch {
            try { coordinator.reminderAck(orderId, delivered) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DELIVERED = "uz.lokmago.restaurant.action.REMINDER_DELIVERED"
        const val ACTION_IN_PROGRESS = "uz.lokmago.restaurant.action.REMINDER_IN_PROGRESS"
        const val EXTRA_ORDER_ID = "orderId"
    }
}
