package uz.lokmago.restaurant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.ui.AppViewModel
import uz.lokmago.restaurant.ui.LokmaGoRoot
import uz.lokmago.restaurant.ui.OpenRequest
import uz.lokmago.restaurant.ui.theme.LokmaGoTheme
import uz.lokmago.restaurant.update.UpdateManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var settings: uz.lokmago.restaurant.data.local.SettingsStore
    private val vm: AppViewModel by viewModels()
    private val openRequest = MutableStateFlow<OpenRequest?>(null)
    private var policy: uz.lokmago.restaurant.data.remote.dto.VersionDto.AndroidVersion? = null

    // Cancelled/failed Play flows are re-evaluated in onResume(); a mandatory update keeps the gate up.
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    private val updater by lazy { UpdateManager(this, updateLauncher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // "Kassa rejimi": while the app is open the screen stays on (a restaurant phone on the counter, on a charger)
        androidx.lifecycle.lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                settings.state.map { it.keepAwake }.distinctUntilChanged().collect {
                    if (it) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        handle(intent)
        // POST_NOTIFICATIONS is requested by the permission walk-through (ui/permissions), with an explanation, not silently here.
        updater.register()
        setContent {
            LokmaGoTheme {
                LokmaGoRoot(
                    openRequest = openRequest,
                    updateReady = updater.readyToInstall,
                    updateBlocking = updater.blocking,
                    onInstallUpdate = updater::completeFlexible,
                    onRetryUpdate = { updater.retryBlocking(policy) },
                    vm = vm,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.sync()                                           // pending-orders recovery on every foreground
        vm.refreshSession()                                 // keep the login alive (at most once a day)
        lifecycleScope.launch { policy = vm.updatePolicy(); updater.check(policy) }
    }

    override fun onDestroy() { updater.unregister(); super.onDestroy() }

    private fun handle(i: Intent?) {
        val id = i?.getStringExtra(EXTRA_ORDER_ID)
        val fromAlert = i?.getBooleanExtra(EXTRA_FROM_ALERT, false) == true
        if (id == null && !fromAlert) return
        if (fromAlert) { setShowWhenLocked(true); setTurnScreenOn(true) }   // wake the phone for a new-order alert
        openRequest.value = OpenRequest(id, fromAlert)
    }

    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_FROM_ALERT = "from_alert"
    }
}
