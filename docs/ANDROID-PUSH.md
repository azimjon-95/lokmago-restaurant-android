# Android push — audit, holat va qurilmada sinash ro'yxati

Talab: yangi buyurtma ochiq / fondagi / qulflangan / Recent Apps'dan yopilgan ilovaga yetib borsin; ishlab turgan LokmaGo buzilmasin.
Server: `lakmago-server` `main` (`docs/MOBIL-PUSH.md`). **Production deploy qilinmagan.**

## Zanjir (kod bilan tasdiqlangan)
`buyurtma to'landi/yaratildi` → `emitOrderToRestaurant` (yagona nuqta, `awaiting_payment` filtri) → `mobilePush.onOrderEvent` → FCM HTTP v1
(`data` + `priority: HIGH`, `collapse_key`, ttl 600 s) → `LokmaFcmService` → `PushPayload.parse` (tekshiruv) → `OrderCoordinator.onPush`
(bildirishnoma + signal darhol; buyurtma tafsiloti **faqat** avtorizatsiyali API'dan) → bildirishnomani bosish → buyurtma ekrani.

## Talab → holat
| Talab | Holat |
|---|---|
| Token login'dan keyin serverga (`POST /api/panel/devices {token, platform}`), restaurantId klientdan emas | ✓ JWT'dan. Login, JWT rotatsiyasi, har soket ulanishi, `onNewToken` → `DeviceRegistration` |
| Muvaffaqiyatsiz bo'lsa «ro'yxatda» deb belgilama | ✓ faqat server 2xx qaytargach (test: «failed request is NOT marked registered») |
| Xavfsiz qayta urinish, so'rov bo'ronisiz | ✓ 5 s→15 s→45 s→2 min→5 min, ko'pi bilan 8 urinish, bir vaqtda bitta so'rov (Mutex), yangi chaqiruv eskisini bekor qiladi |
| Chiqishda DELETE faqat shu akkaunt tokeni | ✓ `devices.unregister()` JWT hali bor paytda; server `restaurantId` bo'yicha cheklaydi |
| Bir restoranda bir nechta qurilma / xodim | ✓ server testida (2 telefon, token boshqa restoranga ko'chadi, begona o'chira olmaydi) |
| `onMessageReceived` da og'ir ish yo'q | ✓ tarmoq va kutish yo'q; yuklash `OrderCoordinator` scope'ida; xato push jarayonni yiqitmaydi (try/catch, logga faqat sinf nomi) |
| Dublikat push | ✓ `queue.contains || inflight` (server har 30 s takrorlasa ham signal ko'payib ketmaydi) |
| Qabul / bekor → signal to'xtaydi | ✓ `order_updated` / `order_cancelled` → `onRemoteChange` |
| Login bo'lmaganda push | ✓ e'tiborsiz |
| Payload ishonchsiz kirish sifatida | ✓ `orderId` 24-hex bo'lmasa tashlanadi (API yo'liga tushmaydi), matn uzunligi cheklangan |
| POST_NOTIFICATIONS (13+), kanal (8+), FGS turi (14+) | ✓ tushuntirishli oyna; `orders_new_v2` yuqori ahamiyat; `specialUse` / `mediaPlayback` e'lon qilingan |
| Doimiy soket xizmati push o'rnini bosmaydi, ixtiyoriy | ✓ FCM asosiy. Sozlamalar → «Fon ulanishi» bilan o'chiriladi (ilova boshlanishi va qayta yoqilishda ham hurmat qilinadi) |
| Ortiqcha ruxsat / xizmat qo'shilmadi | ✓ yangi ruxsat yo'q (`POST_NOTIFICATIONS` so'rovi jimgina emas, tushuntirib so'raladi) |

## Cheklovlar va Play siyosati (yashirmasdan)
* **Force stop:** foydalanuvchi Sozlamalar → Ilova → «To'xtatish» bossa Android ilovani «to'xtatilgan» holatga qo'yadi va ilova o'zi ochilmaguncha FCM
  ham, xizmat ham, BootReceiver ham ishlamaydi. Bunda kafolat YO'Q. Ilova qayta ochilganda: soket + `syncPending()` o'tkazib yuborilgan buyurtmalarni oladi.
  Xiaomi/Huawei/Oppo/Vivo kabilarda «Avto-ishga tushirish» o'chiq bo'lsa Recent Apps'dan tozalash ham xuddi shunday ishlashi mumkin — ruxsatlar oynasi buni ko'rsatadi.
* **`USE_FULL_SCREEN_INTENT`:** Android 14+ da Google uni asosan qo'ng'iroq/budilnik ilovalariga avtomatik beradi; boshqalarda foydalanuvchi Sozlamalardan yoqadi
  (ilova «tayyorlik» ro'yxatida ko'rsatadi). Play Console'da «Full-screen intent» deklaratsiyasi talab qilinishi mumkin — ILOVA unga tayanmaydi: asosiy yo'l oddiy yuqori ahamiyatli bildirishnoma + alarm ovozi.
* **`FOREGROUND_SERVICE_SPECIAL_USE`** (doimiy ulanish) va **`MEDIA_PLAYBACK`** (alarm): Play Console'da foreground-service deklaratsiyasi va matn kerak
  («Restoran xodimiga yangi buyurtma haqida takrorlanuvchi ovozli signal berish / jonli buyurtma oqimini ushlab turish»). Doimiy xizmatni foydalanuvchi o'chira oladi.
* **Doze / App Standby:** `HIGH` priority FCM ularni teshib o'tadi; `ttl` 600 s — telefon undan uzoq o'chiq bo'lsa push eskiradi, ilova ochilganda sinxron baribir oladi.
* Push `data`-only bo'lgani uchun bildirishnomani ilova o'zi ko'rsatadi: jarayon o'ldirilgan bo'lsa ham FCM uni uyg'otadi (Force stop bundan mustasno).

## Tekshiruv natijalari
| Nima | Natija |
|---|---|
| **Bajarilgan (bu muhitda, haqiqiy)** | |
| Android toza mantiq testlari — `tools/kotlin-check/run.sh` (kotlinc 2.0.21 + coroutines) | **33/33 o'tdi**: OrderQueue 13, PushPayload 8, DeviceRegistration 12 |
| Testlar yiqila oladimi (ataylab buzib) | ✓ ro'yxatni «oldindan belgilash» buzilishi 3 testni, `orderId` tekshiruvini olib tashlash 1 testni yiqitdi |
| Server testlari (FerretDB bilan) | `test:mobile-push` **74/74**; boshqa 46 to'plamdan baza muhiti sababli yiqiladiganlar (coords, pickup, support-thread, db-backup, order-socket, paynet-amount) o'zgarishsiz `main`da ham xuddi shunday |
| **Bajarilmagan** | |
| `./gradlew testDebugUnitTest assembleDebug` (Android SDK yo'q) | **ishga tushirilmagan**: kotlinx.serialization / Hilt / Retrofit / Compose'ga tegadigan kod kompilyatsiya qilinmagan |
| Haqiqiy FCM/APNs bilan yetkazish, haqiqiy telefon | **tekshirilmagan** (Firebase kaliti va qurilma kerak) |
| Production serverdagi FCM sozlamasi | **tekshirib bo'lmadi** (kirish yo'q). Tekshirish usuli: server logi + `GET /api/panel/devices → check` + ilovada «Push tekshiruvi» |

## Telefonda sinash ro'yxati (Android)
Old shart: serverda `FCM_SERVICE_ACCOUNT_BASE64` (ilovaning `google-services.json` i bilan bir loyiha), server qayta ishga tushirilgan (sizning tasdig'ingiz bilan),
ilovada `app/google-services.json` (paket `uz.lokmago.restaurant`, debug: `.debug`), bildirishnoma ruxsati berilgan.
0. Profil → **Push tekshiruvi**: hamma qadam yashil; «Test push yuborish».
1. Ilova ochiq: yangi buyurtma → to'liq ekranli signal, «Qabul qilish» → signal to'xtaydi, server statusi `accepted`.
2. Home bilan fonga → buyurtma yarating → bildirishnoma + ovoz.
3. Ekran bloklangan → ekran yonadi/bildirishnoma, ovoz qabul qilinguncha takrorlanadi.
4. Recent Apps'dan ilovani yopib (suring) → buyurtma → bildirishnoma keladi; bosilsa shu buyurtma ochiladi.
5. Samolyot rejimi 1 daqiqa → o'chirish → ochiq buyurtmalar sinxron bo'ladi; token qayta ro'yxatdan o'tgan (`GET /api/panel/devices` → `lastSeen`).
6. **Force stop** → buyurtma keladi (kutilgani: kelmaydi) → ilovani oching → buyurtma darhol chiqadi (sinxron).
7. Bir vaqtda 3 ta buyurtma → hammasi navbatda, hech biri yo'qolmaydi, takroriy signal yo'q.
8. Ikki telefon, bitta restoran akkaunti → ikkalasiga keladi; birida «Qabul qilish» → ikkinchisida signal to'xtaydi.
9. Chiqish → buyurtma yarating → bu telefonga signal kelmaydi; boshqa restoranga kirsangiz eski restoran buyurtmalari kelmaydi.
10. Telefonni qayta yoqing → (doimiy ulanish yoqilgan bo'lsa) bildirishnoma qaytadi; FCM baribir ishlaydi.

## Qolgan xavflar / keyingi qadamlar
* Gradle build va `testDebugUnitTest` ni Android Studio'da ishga tushirish (Hilt/Compose kodi bu yerda kompilyatsiya qilinmagan).
* `onMessageReceived` dan keyin jarayon qisqa vaqtda muzlatilsa (ovoz o'chiq bo'lsa FGS yo'q) buyurtma tafsiloti keyingi ochilishda yuklanadi — bildirishnoma baribir ko'rinadi.
  Agar buni qurilmada kuzatsangiz, `expedited WorkManager` qo'shish mumkin (yangi bog'liqlik; alohida baholanadi).
* Chiqishda DELETE tarmoq yo'qligidan o'tmasa, token serverda qoladi: ilova push'ni e'tiborsiz qoldiradi, boshqa restoranga kirilsa token ko'chadi.
* Firebase/APNs kalitlarini aylantirish (rotation) — `.env` o'zgartirib, restart (uzilishsiz: push o'chiq holatda ham buyurtma tizimi ishlaydi).

## 2026-11-18: "ba'zan keladi, ba'zan jim" — ildiz sabablar va tuzatishlar

**1. Firebase'siz APK (asosiy sabab).** `app/build.gradle.kts` `google-services.json` bo'lmasa Firebase'ni JIMGINA o'chirardi, fayl esa `.gitignore`da edi.
Shu APK'da push tokeni hech qachon olinmaydi → server ro'yxatida telefon yo'q («Serverda 0 ta qurilma») → buyurtma faqat ilova ochiq va socket ulangan
paytda keladi. Tuzatish: `app/google-services.json` repoda (ikkala paket: `uz.lokmago.restaurant` va `.debug`); fayl yo'q bo'lsa yig'ish **xato bilan to'xtaydi**.
Bu fayl sir emas (Firebase mijoz konfiguratsiyasi). Tavsiya: Google Cloud → Credentials'da undagi API kalitni Android ilovalari (paket + SHA-1) bilan cheklang.
Service-account kaliti (`*firebase-adminsdk*.json`) hech qachon repoga tushmaydi — faqat server `.env`da.

**2. Tarmoq qaytganda darhol qayta ulanmasdi.** Socket.IO o'z taymerini kutardi (uxlayotgan telefonda ishlamaydi). Endi `NetworkMonitor.available` →
`SocketManager.reconnectNow()` + kutilayotgan buyurtmalarni qayta o'qish (Wi-Fi ↔ mobil almashganda ham).

**3. O'ldirilgan xizmatni tiklovchi zaxira yo'q edi.** `OrderWatchdog`: tizimga kirilgan paytda har ~15 daqiqada (AlarmManager.setAndAllowWhileIdle, maxsus ruxsatsiz)
socket'ni tekshiradi, kutilayotgan buyurtmalarni o'qiydi (bor bo'lsa odatdagi signal), «Fon ulanishi» yoqilgan bo'lsa xizmatni qayta yoqadi. Qayta yuklanishdan keyin BootReceiver yoqadi.

**4. Batareya cheklovi va Avto-ishga tushirish.** Ruxsatlar oynasida batareya cheklovini bir tugma bilan olib tashlash (tizim oynasi
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), Xiaomi/Huawei/Honor/Oppo/Vivo uchun «Avto-ishga tushirish» sahifasiga to'g'ridan-to'g'ri o'tish, va «Push xizmati (Firebase)» bandi.
Play siyosati: bu ruxsat ilovaning asosiy vazifasi (buyurtma signali) fonda ishlashga bog'liq bo'lgani uchun so'raladi — Play Console'da shuni yozing.

### Xiaomi / Redmi / POCO (MIUI / HyperOS) — telefonda bir marta
1. Sozlamalar → Ilovalar → LGO Business → **Avto-ishga tushirish: YOQ**.
2. Batareya tejash → **Cheklovsiz** (No restrictions).
3. Bildirishnomalar → «Yangi buyurtma» kanali → ovoz, qulf ekranida ko'rsatish, «Suzuvchi bildirishnoma».
4. So'nggi ilovalar ro'yxatida ilova kartasini pastga torting → **qulf** belgisi (tozalashda o'ldirilmasin).
5. Profil → **Push tekshiruvi**: hamma bandlar yashil bo'lsin, «Test push» → ilovani yopib, telefonni bloklang.

## «Telefonni sozlash» — yagona ekran (2026-11-18)
Birinchi kirish, Bosh sahifadagi «sozlanmagan» banneri va Profil → «Telefonni sozlash» — hammasi bitta ekran (`ui/permissions/PhoneSetupScreen.kt`).
* **Bitta tugma «Hammasini sozlash»**: kerakli tizim oynalarini ketma-ket o'zi ochadi (bildirishnoma ruxsati → buyurtma ovozi → «ilova uxlab qolmasin»
  (bitta «Ruxsat berish» oynasi) → qulflangan ekran (Android 14+) → avto-ishga tushirish (faqat shu sahifa telefonda bor bo'lsa: Xiaomi, Huawei, Honor,
  Oppo/Realme/OnePlus, Vivo/iQOO, Asus, Tecno/Infinix)). Foydalanuvchi ruxsat berib orqaga qaytadi — keyingisi ochiladi; rad etilgani qayta-qayta ochilmaydi.
* **Oddiy til**: ekranda Firebase/FCM/token/server so'zlari yo'q (testda tekshiriladi). Faqat foydalanuvchi qila oladigan ish ko'rsatiladi.
* **Ulanishni ilova o'zi hal qiladi** (3 marta urinadi); foydalanuvchiga faqat qo'lidan keladigan narsa: internetni yoqish, ilovani yangilash, qayta urinish.
* Texnik tafsilot (yordam xizmati uchun) ekranning pastida yig'ilgan holda.
* Mantiq: `alert/PhoneSetup.kt` (toza Kotlin, `PhoneSetupTest` — 7 test), telefon holati va tizim oynalari: `alert/PhoneSetupProbe.kt`.
