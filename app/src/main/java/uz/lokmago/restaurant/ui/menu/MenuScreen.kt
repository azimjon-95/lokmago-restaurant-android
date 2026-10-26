package uz.lokmago.restaurant.ui.menu

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.MenuRepository
import uz.lokmago.restaurant.data.repo.MenuStore
import uz.lokmago.restaurant.domain.Dish
import uz.lokmago.restaurant.domain.DishCategories
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

/** Menyu va Stop List shu bitta view-model'ni baham ko'radi (ikkalasi ham MenuStore'ning jonli ro'yxatini ko'rsatadi). */
@HiltViewModel
class MenuViewModel @Inject constructor(private val repo: MenuRepository, private val store: MenuStore) : ViewModel() {
    val dishes = store.dishes
    val loaded = store.loaded
    val error = store.error
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun refresh() = viewModelScope.launch { store.refresh() }

    /** admin panel: `toggleStop(d._id, d.isAvailable)` — hozir sotuvda bo'lsa STOPga tushadi, aks holda qaytadi. */
    fun toggleStop(d: Dish) = viewModelScope.launch {
        store.put(d.copy(available = !d.available))                               // darhol ko'rinsin
        try { store.put(repo.setStopped(d.id, stopped = d.available)) }
        catch (e: AppError) { store.put(d); messages.tryEmit(e.message ?: "Bajarib bo'lmadi") }
    }

    fun delete(d: Dish) = viewModelScope.launch {
        try { repo.delete(d.id); store.drop(d.id) } catch (e: AppError) { messages.tryEmit(e.message ?: "O'chirib bo'lmadi") }
    }

    suspend fun upload(uri: Uri): String = repo.uploadImage(uri)
    suspend fun save(id: String?, body: JsonObject): Dish = repo.save(id, body).also { store.put(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(vm: MenuViewModel = hiltViewModel()) {
    val dishes by vm.dishes.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Dish?>(null) }
    var deleting by remember { mutableStateOf<Dish?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { toast = it; delay(3500); toast = null } }

    val shown = remember(dishes, query) { dishes.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) } }
    // Kategoriyalar tartibi admin panel bilan bir xil; ro'yxatda yo'q kategoriya oxirida "Boshqa"
    val groups = remember(shown) {
        val known = DishCategories.all.map { it.first }.toSet()
        val byCat = DishCategories.all.mapNotNull { (v, l) -> shown.filter { it.category == v }.takeIf { it.isNotEmpty() }?.let { l to it } }
        byCat + listOfNotNull(shown.filter { it.category !in known }.takeIf { it.isNotEmpty() }?.let { "Boshqa" to it })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = Dt.spaceScreen, end = Dt.spaceScreen, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Menyu", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${dishes.size} ta taom", color = Lg.Muted, fontSize = 13.sp)
            }
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Lg.Green).clickable { creating = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { LgIcon(R.drawable.ic_plus, Dt.onAccent, 18.dp); Text("Taom", color = Dt.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = Dt.spaceScreen, vertical = 12.dp), singleLine = true,
            placeholder = { Text("Taom qidirish", color = Lg.Muted) }, leadingIcon = { LgIcon(R.drawable.ic_search, Lg.Muted, 20.dp) },
            shape = RoundedCornerShape(16.dp), colors = lgFieldColors(), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        )
        toast?.let { Text(it, color = Lg.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Dt.spaceScreen).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lg.Orange.copy(alpha = 0.12f)).padding(12.dp)) }

        PullToRefreshBox(false, { vm.refresh() }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!loaded && dishes.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green) }
                else if (shown.isEmpty()) item {
                    EmptyState(R.drawable.ic_utensils, if (query.isBlank()) "Menyu bo'sh" else "Hech narsa topilmadi",
                        error ?: if (query.isBlank()) "«+ Taom» tugmasi bilan birinchi taomni qo'shing" else "Boshqa nom bilan qidirib ko'ring")
                }
                groups.forEach { (label, list) ->
                    item(key = "h-$label") { Text("$label · ${list.size}", color = Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                    items(list, key = { it.id }) { d -> DishRow(d, { editing = d }, { vm.toggleStop(d) }, { deleting = d }) }
                }
            }
        }
    }

    if (creating || editing != null) {
        DishFormDialog(editing, onDismiss = { creating = false; editing = null }, upload = vm::upload, save = vm::save)
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = Lg.Surface,
            title = { Text("\"${d.name}\" o'chirilsinmi?", color = Lg.Text) },
            text = { Text("Taom menyudan butunlay o'chadi. Vaqtincha to'xtatish uchun STOP tugmasidan foydalaning.", color = Lg.Muted) },
            confirmButton = { TextButton({ vm.delete(d); deleting = null }) { Text("O'chirish", color = Lg.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Bekor qilish", color = Lg.Muted) } },
        )
    }
}

@Composable
private fun DishRow(d: Dish, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(Dt.radiusCard)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Lg.Card).border(1.dp, Lg.Line, shape).clickable(onClick = onEdit).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DishThumb(d.imageUrl, 64.dp, grayscale = !d.available)
        Column(Modifier.weight(1f).alpha(if (d.available) 1f else 0.55f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(d.name, color = Lg.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.price.som(), color = Lg.GreenText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                d.oldPrice?.let { Text(it.som(), color = Lg.Muted, fontSize = 12.sp, textDecoration = TextDecoration.LineThrough) }
            }
            if (!d.available) Text("STOP", color = Dt.onAccent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Lg.Red).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(
                d.available, { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = Lg.Green, checkedThumbColor = Dt.onAccent, checkedBorderColor = Lg.Green,
                    uncheckedTrackColor = Lg.CardHigh, uncheckedThumbColor = Lg.Muted, uncheckedBorderColor = Lg.Line),
            )
            Text(if (d.available) "Sotuvda" else "STOP", color = if (d.available) Lg.GreenText else Lg.Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onDelete), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_trash, Lg.Red, 20.dp) }
    }
}
