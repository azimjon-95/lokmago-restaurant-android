package uz.lokmago.restaurant.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import uz.lokmago.restaurant.di.AppScope

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext ctx: Context,
    @AppScope scope: CoroutineScope,
) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    private fun now(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    val online: StateFlow<Boolean> = callbackFlow {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(now()) }
        }
        cm.registerDefaultNetworkCallback(cb)
        trySend(now())
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, now())
}
