package uz.lokmago.restaurant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.ui.alert.AlertScreen
import uz.lokmago.restaurant.ui.components.BarItem
import uz.lokmago.restaurant.ui.components.Conn
import uz.lokmago.restaurant.ui.permissions.PermissionsDialog
import uz.lokmago.restaurant.ui.components.FloatingTabBar
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.detail.OrderDetailScreen
import uz.lokmago.restaurant.ui.home.HomeScreen
import uz.lokmago.restaurant.ui.login.LoginScreen
import uz.lokmago.restaurant.ui.menu.MenuScreen
import uz.lokmago.restaurant.ui.menu.StopListScreen
import uz.lokmago.restaurant.ui.reviews.ReviewsScreen
import uz.lokmago.restaurant.ui.orders.OrdersScreen
import uz.lokmago.restaurant.ui.report.ReportScreen
import uz.lokmago.restaurant.ui.settings.SettingsScreen
import uz.lokmago.restaurant.ui.theme.Lg
import androidx.compose.runtime.saveable.rememberSaveable

data class OpenRequest(val orderId: String?, val fromAlert: Boolean, val nonce: Long = System.nanoTime())

@Composable
fun LokmaGoRoot(
    openRequest: StateFlow<OpenRequest?>,
    updateReady: StateFlow<Boolean>,
    updateBlocking: StateFlow<Boolean>,
    onInstallUpdate: () -> Unit,
    onRetryUpdate: () -> Unit,
    vm: AppViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val session by vm.session.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val readinessItems by vm.readinessItems.collectAsStateWithLifecycle()
    val showPermissions by vm.showPermissions.collectAsStateWithLifecycle()
    val alertVisible by vm.alertVisible.collectAsStateWithLifecycle()
    val silenced by vm.silenced.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val stoppedCount by vm.stoppedCount.collectAsStateWithLifecycle()
    val req by openRequest.collectAsStateWithLifecycle()
    val ready by updateReady.collectAsStateWithLifecycle()
    val blocking by updateBlocking.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    val notificationsOn = readinessItems.all { it.ok }                       // true until the first check
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshReadiness() }   // back from system settings → recheck
    // First login on this device: walk through the permissions ONCE, with an explanation (never silently)
    LaunchedEffect(session != null) { if (session != null && !vm.onboarded) { vm.refreshReadiness(); vm.showPermissions.value = true } }
    if (showPermissions) PermissionsDialog(readinessItems, onChanged = vm::refreshReadiness, onDismiss = vm::permissionsFinished)

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(ready) {
        if (ready && snack.showSnackbar("Yangilanish tayyor", "Qayta ishga tushirish", duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) onInstallUpdate()
    }
    LaunchedEffect(session) {
        val route = nav.currentDestination?.route
        if (session == null && route != "login") nav.navigate("login") { popUpTo(0) }
        if (session != null && route == "login") nav.navigate("main") { popUpTo(0) }
    }
    // Notification / full-screen-intent taps
    LaunchedEffect(req, session) {
        val r = req ?: return@LaunchedEffect
        if (session == null) return@LaunchedEffect
        if (r.fromAlert) vm.showAlert()
        else if (r.orderId != null) { vm.minimize(); nav.navigate("order/${r.orderId}") }
    }

    Scaffold(containerColor = Lg.Bg, snackbarHost = { SnackbarHost(snack) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            NavHost(nav, startDestination = if (session == null) "login" else "main") {
                composable("login") { LoginScreen() }
                composable("main") {
                    MainTabs(
                        queue = queue, connection = connection, notificationsOn = notificationsOn, stoppedCount = stoppedCount,
                        onOpenReport = { nav.navigate("report") }, onOpenReviews = { nav.navigate("reviews") },
                        onOpenOrder = { nav.navigate("order/$it") }, onLogout = vm::logout,
                        onFixNotifications = { vm.refreshReadiness(); vm.showPermissions.value = true },
                    )
                }
                composable("order/{id}") { OrderDetailScreen(onBack = { nav.popBackStack() }) }
                composable("report") { Box(Modifier.statusBarsPadding()) { ReportScreen(onBack = { nav.popBackStack() }) } }
                composable("reviews") { Box(Modifier.statusBarsPadding()) { ReviewsScreen(onBack = { nav.popBackStack() }) } }
            }
            if (alertVisible) AlertScreen(
                state = queue, silenced = silenced, busy = busy, online = online,
                onAccept = vm::acceptHead, onSilence = vm::silence, onDefer = vm::deferHead,
                onOpen = { id -> vm.minimize(); nav.navigate("order/$id") }, onMinimize = vm::minimize,
            )
            if (blocking) UpdateGate(onRetryUpdate)
        }
    }
}

private enum class Tab(val label: String, val icon: Int) {
    HOME("Asosiy", R.drawable.ic_home), ORDERS("Buyurtmalar", R.drawable.ic_orders),
    MENU("Menyu", R.drawable.ic_book), STOP("Stop List", R.drawable.ic_ban), PROFILE("Profil", R.drawable.ic_profile),
}

@Composable
private fun MainTabs(
    queue: uz.lokmago.restaurant.domain.OrderQueue.State, connection: Conn, notificationsOn: Boolean,
    stoppedCount: Int, onOpenReport: () -> Unit, onOpenReviews: () -> Unit,
    onOpenOrder: (String) -> Unit, onLogout: () -> Unit, onFixNotifications: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> HomeScreen(queue, connection, notificationsOn, onOpenOrder, { tab = Tab.ORDERS }, onFixNotifications, { tab = Tab.ORDERS })
                Tab.ORDERS -> OrdersScreen(onOpenOrder, queue.size, { })
                Tab.MENU -> MenuScreen()
                Tab.STOP -> StopListScreen(onOpenMenu = { tab = Tab.MENU })
                Tab.PROFILE -> SettingsScreen(onLogout, onOpenReport, onOpenReviews)
            }
        }
        FloatingTabBar(
            items = Tab.entries.map { BarItem(it.icon, it.label, when (it) { Tab.ORDERS -> queue.size; Tab.STOP -> stoppedCount; else -> 0 }) },
            selected = tab.ordinal, onSelect = { tab = Tab.entries[it] }, modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun UpdateGate(onUpdate: () -> Unit) =
    Column(Modifier.fillMaxSize().background(Lg.Bg).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LgIcon(R.drawable.ic_download, Lg.Green, 56.dp)
        Spacer(Modifier.height(16.dp))
        Text("Yangi versiya kerak", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Ilovani davom ettirish uchun yangi versiyani o'rnating.", color = Lg.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Yangilash", onClick = onUpdate)
    }
