# LokmaGo Restoran — Android

Restoran xodimlari uchun yangi buyurtmalarni real vaqtda qabul qiladigan ilova (Kotlin, Compose, Hilt, Socket.IO, FCM, Play In-App Updates).

## Tez boshlash

```bash
cp env.properties.example env.properties      # va qiymatlarni to'ldiring (git'ga tushmaydi)
# Firebase Console → Android app (uz.lokmago.restaurant) → google-services.json → app/google-services.json
gradle wrapper && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

`google-services.json` bo'lmasa ilova baribir yig'iladi, faqat FCM push o'chiq bo'ladi (Socket.IO ishlayveradi).

## Release APK

Production imzolangan APK, release guard va qabul tekshiruvi: **[docs/RELEASE.md](docs/RELEASE.md)**. Qisqacha: `./gradlew :app:assembleRelease` (yoki Actions → *Release APK*).

## Env (`env.properties` yoki CI environment)

| Kalit | Ma'nosi |
|---|---|
| `API_GATEWAY_URL` | API gateway manzili (HTTPS) |
| `API_GATEWAY_PASSWORD` | Gateway paroli — har so'rovda `API_GATEWAY_HEADER` sarlavhasi bilan yuboriladi |
| `API_GATEWAY_HEADER` | Sarlavha nomi (default `x-gateway-password`) |
| `API_PATH_PREFIX` | REST prefiksi (default `restaurant/v1/`) |
| `SOCKET_URL`, `SOCKET_PATH` | Socket.IO boshqa hostda bo'lsa (bo'sh = gateway bilan bir xil) |
| `VERSION_CODE`, `VERSION_NAME` | Har Play release'da `VERSION_CODE` +1 |
| `KEYSTORE_*`, `KEY_*` | Release imzosi |

> Mobil ilovadagi har qanday qiymat APK'dan chiqarib olinishi mumkin. Gateway paroli — faqat "eshik qo'ng'irog'i";
> haqiqiy himoya — foydalanuvchi JWT'si va backenddagi restoran avtorizatsiyasi (`restaurantId` clientdan hech qachon olinmaydi).

## Buyurtma navbati (5–6 ta buyurtma birdaniga kelganda)

`domain/OrderQueue.kt` — FIFO navbat. Bitta buyurtma to'liq ekranda ko'rsatiladi, qolganlari
"Navbatda yana N ta" bo'lib kutadi. **Bitta** signal butun navbatga xizmat qiladi (5 ta ovoz ustma-ust tushmaydi).
Qabul qilingach keyingisi avtomatik chiqadi; signal navbat bo'shaguncha davom etadi.
`Qabul qilish` so'rovlari `Mutex` bilan ketma-ket bajariladi; tarmoq bo'lmasa buyurtma "qabul qilindi" deb ko'rsatilmaydi.
Manbalar (Socket.IO, FCM, pending sync) `orderId` bo'yicha deduplikatsiya qilinadi.

## Buyurtmani yakunlashni nazorat qilish (delivery reminder)

Buyurtma `Yetkazilmoqda` holatiga o'tgach, agar backend belgilangan vaqt ichida "Yetkazildi" kelmasa,
serverdan eslatma keladi (Socket.IO `order:delivery-reminder` yoki FCM `order_delivery_reminder`) —
`OrderCoordinator.onDeliveryReminder()` oddiy (alarmsiz) notification ko'rsatadi, ikkita amal bilan:
**🟢 Yetkazildi** va **🟡 Jarayonda** (`alert/ReminderActionReceiver.kt` — ilova yopiq bo'lsa ham ishlaydi).
Xuddi shu ikki amal `OrderDetailScreen`da ham bor (order `Yetkazilmoqda` holatida bo'lganda).

Ikkalasi ham backendning **mavjud** completion yo'lini chaqiradi (`POST orders/:id/reminder/ack`) —
Android hech qachon orderni o'zicha "yakunlangan" deb belgilamaydi; server javobi kutiladi (mavjud
`accept()` bilan bir xil naqsh). Batafsil: backend repo README, "Buyurtmani yakunlashni nazorat qilish".

## Arxitektura

```
Socket.IO (ochiq) ─┐
FCM (fon/yopiq) ───┼─► OrderCoordinator ─► OrderQueue ─► AlertController ─► OrderAlertService (ovoz+vibratsiya)
GET /orders/pending┘  │                                 └► Notifier (kanal: orders_new_v1, HIGH)
                      └─► onDeliveryReminder ─► Notifier.notifyReminder (oddiy notification, 2 amal)
```

## Ikonkalar

`tools/icons/icons.py` — yagona manba (24px grid, 1.75 stroke). `python3 tools/icons/icons.py` →
`design/icons/*.svg` va `app/src/main/res/drawable/ic_*.xml` (VectorDrawable).

## Play Store

- `versionCode` har release'da +1. Update: flexible (oddiy), immediate (backend `forceUpdate` / `minimumVersion` yoki Play priority ≥ 4).
- In-app update faqat Play'dan o'rnatilgan build'da ishlaydi (internal testing track orqali sinang).
- `USE_FULL_SCREEN_INTENT` — Play Console'da "Foreground/Full-screen intent" deklaratsiyasi talab qilinishi mumkin.

## Ma'lum cheklovlar

- Kod hali Android Studio'da yig'ilmagan — birinchi yig'ishda kutubxona versiyalarini yangilash kerak bo'lishi mumkin.
- UI matnlari hozircha kodda; `strings.xml`ga ko'chirish (va ru/en) keyingi qadam.
- Delivery-reminder oqimi uchun hali instrumentation/unit test yozilmagan (backend tomoni 26 ta testda to'liq qoplangan); `OrderCoordinator.reminderAck`/`onDeliveryReminder` uchun test qo'shish keyingi qadam.
