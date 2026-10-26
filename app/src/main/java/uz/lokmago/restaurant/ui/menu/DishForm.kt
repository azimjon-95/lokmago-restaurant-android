package uz.lokmago.restaurant.ui.menu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.domain.Dish
import uz.lokmago.restaurant.domain.DishCategories
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.cdn

/**
 * Yangi taom / tahrirlash — admin panelning DishForm'i bilan bir xil maydonlar va qoidalar:
 * rasm, nom, tavsif, kategoriya, narx / eski narx, tayyorlanish vaqti, og'irlik yoki hajm (ichimlikda), ichimlik turi,
 * KBJU (ichimlikda yo'q), zal narxi, hajm va qo'shimchalar guruhlari.
 */
@Composable
fun DishFormDialog(dish: Dish?, onDismiss: () -> Unit, upload: suspend (Uri) -> String, save: suspend (String?, JsonObject) -> Dish) {
    var draft by remember { mutableStateOf(dish?.toDraft() ?: DishDraft()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val drink = DishCategories.isDrink(draft.category)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true; error = null
            try { draft = draft.copy(imageUrl = upload(uri)) } catch (e: AppError) { error = e.message } finally { uploading = false }
        }
    }

    fun submit() {
        draft.validate()?.let { error = it; return }
        scope.launch {
            saving = true; error = null
            try { save(dish?.id, draft.toJson(dish)); onDismiss() } catch (e: AppError) { error = e.message } finally { saving = false }
        }
    }

    Dialog(onDismissRequest = { if (!saving && !uploading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Lg.Bg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = !saving && !uploading, onClick = onDismiss), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_close, Lg.Text) }
                    Text(if (dish == null) "Yangi taom" else "Taomni tahrirlash", color = Lg.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. rasm
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp)).background(Lg.Card).border(1.dp, Lg.Line, RoundedCornerShape(18.dp))
                            .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.imageUrl.isNotBlank()) AsyncImage(draft.imageUrl.cdn(800), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LgIcon(R.drawable.ic_camera, Lg.Muted, 38.dp)
                            Text("Rasm tanlash", color = Lg.TextSecondary, fontWeight = FontWeight.SemiBold)
                            Text("JPG, PNG — 5 MB gacha", color = Lg.Muted, fontSize = 11.sp)
                        }
                        if (uploading) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = Lg.Green); Text("Yuklanmoqda…", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (draft.imageUrl.isNotBlank() && !uploading) Text("Rasmni olib tashlash", color = Lg.Red, fontSize = 12.sp, modifier = Modifier.clickable { draft = draft.copy(imageUrl = "") })

                    // 2. nom, tavsif, kategoriya
                    LgField("Taom nomi *", draft.name, { draft = draft.copy(name = it) })
                    LgField("Tavsif", draft.description, { draft = draft.copy(description = it) }, singleLine = false, minLines = 3)
                    LgDropdown("Kategoriya *", DishCategories.label(draft.category), DishCategories.all) { v ->
                        // ichimlikdan oddiy taomga o'tilsa ichimlik turi tozalanadi
                        draft = draft.copy(category = v, drinkType = if (DishCategories.isDrink(v)) draft.drinkType else "")
                    }

                    // 3. narx
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LgField("Narx (so'm) *", draft.price, { draft = draft.copy(price = it.filter(Char::isDigit)) }, Modifier.weight(1f), KeyboardType.Number)
                        LgField("Eski narx", draft.oldPrice, { draft = draft.copy(oldPrice = it.filter(Char::isDigit)) }, Modifier.weight(1f), KeyboardType.Number)
                    }
                    LgField("Tayyorlanish vaqti (daqiqa)", draft.prepMinutes, { draft = draft.copy(prepMinutes = it.filter(Char::isDigit).take(3)) }, keyboard = KeyboardType.Number)

                    // 4. og'irlik yoki hajm (ichimlikda)
                    if (drink) {
                        LgField("Hajm", draft.volume, { draft = draft.copy(volume = it) }, hint = "Masalan: 0.5 l · 1 l · 330 ml")
                        LgDropdown("Ichimlik turi", DishCategories.drinkTypes.firstOrNull { it.first == draft.drinkType }?.second ?: "Tanlanmagan", listOf("" to "Tanlanmagan") + DishCategories.drinkTypes) { draft = draft.copy(drinkType = it) }
                    } else {
                        LgField("Og'irlik", draft.weight, { draft = draft.copy(weight = it) }, hint = "Bitta taom: 150 г · Assorti: 150/30/30/20 г")
                        Text("Oziqlanish qiymati (ixtiyoriy)", color = Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LgField("Kaloriya", draft.calories, { draft = draft.copy(calories = it) }, Modifier.weight(1f), KeyboardType.Decimal)
                            LgField("Oqsil, g", draft.protein, { draft = draft.copy(protein = it) }, Modifier.weight(1f), KeyboardType.Decimal)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LgField("Yog', g", draft.fat, { draft = draft.copy(fat = it) }, Modifier.weight(1f), KeyboardType.Decimal)
                            LgField("Uglevod, g", draft.carbs, { draft = draft.copy(carbs = it) }, Modifier.weight(1f), KeyboardType.Decimal)
                        }
                    }

                    // 5. zal (dine-in) narxi
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Zal uchun alohida narx", color = Lg.Text, fontWeight = FontWeight.SemiBold)
                            Text("O'chiq bo'lsa zal narxi yetkazish narxi bilan bir xil", color = Lg.Muted, fontSize = 12.sp)
                        }
                        Switch(draft.priceMode == "custom", { draft = draft.copy(priceMode = if (it) "custom" else "sync") },
                            colors = SwitchDefaults.colors(checkedTrackColor = Lg.Green, checkedThumbColor = Dt.onAccent, uncheckedTrackColor = Lg.CardHigh, uncheckedThumbColor = Lg.Muted, uncheckedBorderColor = Lg.Line))
                    }
                    if (draft.priceMode == "custom") LgField("Zal narxi (so'm)", draft.dineInPrice, { draft = draft.copy(dineInPrice = it.filter(Char::isDigit)) }, keyboard = KeyboardType.Number)

                    // 6. hajm va qo'shimchalar
                    GroupsEditor(draft.groups) { draft = draft.copy(groups = it, groupsDirty = true) }

                    error?.let { Text(it, color = Lg.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(4.dp))
                }

                Box(Modifier.padding(16.dp)) { PrimaryButton("Saqlash", icon = R.drawable.ic_check, loading = saving, enabled = !uploading) { submit() } }
            }
        }
    }
}

@Composable
private fun LgField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, minLines: Int = 1, hint: String? = null,
) {
    Column(modifier) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines,
            shape = RoundedCornerShape(16.dp), colors = lgFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        )
        hint?.let { Text(it, color = Lg.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp)) }
    }
}

@Composable
private fun LgDropdown(label: String, valueLabel: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).border(1.dp, Lg.Line, RoundedCornerShape(16.dp)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { Text(label, color = Lg.Muted, fontSize = 11.sp); Text(valueLabel, color = Lg.Text, fontSize = 15.sp) }
            LgIcon(R.drawable.ic_chevron_down, Lg.Muted, 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l, color = Lg.Text) }, onClick = { onPick(v); open = false }) }
        }
    }
}

/** Hajm (narxni ALMASHTIRADI, kamida 2 ta) va qo'shimcha (narxga QO'SHILADI) guruhlari — OptionGroupsEditor.jsx. */
@Composable
private fun GroupsEditor(groups: List<GroupDraft>, onChange: (List<GroupDraft>) -> Unit) {
    Text("Hajm va qo'shimchalar", color = Lg.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    Text("Masalan: «Hajmi» (33 sm / 40 sm — narx almashadi) yoki «Qo'shimcha» (pishloq, sous — narx qo'shiladi).", color = Lg.Muted, fontSize = 12.sp)

    groups.forEachIndexed { gi, g ->
        fun setGroup(n: GroupDraft) = onChange(groups.mapIndexed { i, old -> if (i == gi) n else old })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.CardStat).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LgField("Guruh nomi", g.title, { setGroup(g.copy(title = it)) }, Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(groups.filterIndexed { i, _ -> i != gi }) }, contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_trash, Lg.Red, 20.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("variant" to "Hajm", "addon" to "Qo'shimcha").forEach { (k, l) ->
                    Text(l, color = if (g.kind == k) Dt.onAccent else Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (g.kind == k) Lg.Green else Lg.CardHigh).clickable { setGroup(g.copy(kind = k)) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            g.options.forEachIndexed { oi, o ->
                fun setOption(n: OptionDraft) = setGroup(g.copy(options = g.options.mapIndexed { i, old -> if (i == oi) n else old }))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LgField(if (g.kind == "variant") "Hajm (33 sm)" else "Nomi", o.name, { setOption(o.copy(name = it)) }, Modifier.weight(1.4f))
                    LgField("Narx", o.price, { setOption(o.copy(price = it.filter(Char::isDigit))) }, Modifier.weight(1f), KeyboardType.Number)
                    if (g.kind == "addon") Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkbox(o.mandatory, { setOption(o.copy(mandatory = it)) }, colors = CheckboxDefaults.colors(checkedColor = Lg.Green, uncheckedColor = Lg.Muted))
                        Text("Shart", color = Lg.Muted, fontSize = 9.sp)
                    }
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { setGroup(g.copy(options = g.options.filterIndexed { i, _ -> i != oi })) }, contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_close, Lg.Muted, 16.dp) }
                }
            }
            Text(if (g.kind == "variant") "+ Hajm qo'shish" else "+ Qo'shimcha qo'shish", color = Lg.GreenText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { setGroup(g.copy(options = g.options + OptionDraft())) }.padding(vertical = 4.dp))
        }
    }
    Text("+ Guruh qo'shish", color = Lg.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Lg.Line, RoundedCornerShape(14.dp)).clickable { onChange(groups + GroupDraft()) }.padding(14.dp))
}
