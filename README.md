# LokmaGo Restoran — Android

Restoran xodimlari uchun yangi buyurtmalarni real vaqtda qabul qiladigan ilova (Kotlin, Compose, Hilt, Socket.IO, FCM, Play In-App Updates).

## Tez boshlash

```bash
cp env.properties.example env.properties      # va qiymatlarni to'ldiring (git'ga tushmaydi)
# Firebase Console → Android app (uz.lokmago.restaurant) → google-services.json → app/google-services.json
gradle wrapper && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

`google-services.json` bo'lmasa ilova baribir yig'iladi, faqat FCM push o'chiq bo'ladi (Socket.IO ishlayveradi).

## Server va kirish

Ilova **lakmago-server**ga to'g'ridan-to'g'ri ulanadi — admin panel ishlatadigan o'sha API (`/api/...`) va o'sha
Socket.IO. Alohida gateway/BFF **yo'q**.

Restoran **admin paneldagi o'sha login + parol** bilan kiradi (`POST /api/auth/login`). Admin yoki xodim akkaunti
kira olmaydi — faqat restoranga bog'langan akkaunt. Token shifrlangan xotirada (Android Keystore) saqlanadi va
kuniga ko'pi bilan bir marta yangilanadi (`POST /api/panel/session/refresh`), shuning uchun ishlatilayotgan ilova
parol so'ramaydi. Parol telefonda **saqlanmaydi**. Akkaunt bloklansa yoki parol o'zgarsa — keyingi so'rovda 401 va
ilova login ekraniga qaytadi. `restaurantId` clientdan hech qachon yuborilmaydi: server uni tokendan oladi.

| Ilova | Server endpointi |
|---|---|
| Yangi buyurtmalar | `GET /api/panel/orders?status=pending` + Socket.IO `order:new` / `order:update` |
| Qabul / holat | `PATCH /api/panel/orders/:id/status` (admin panel bilan bir xil qoidalar) |
| Yetkazildi (yetkazib berish) | `POST /api/panel/orders/:id/confirm-delivered` (server vaqt shartini tekshiradi) |
| Hisobot | `GET /api/panel/stats`, `GET /api/panel/orders/history` |
| Eslatma | Socket.IO `order:delivery-reminder` |

Socket.IO: handshake'da `auth: { token }` — server restoranni faqat **o'zining** `restaurant:<id>` xonasiga qo'shadi.

Ovoz: [docs/SOUND.md](docs/SOUND.md).

> **Push (FCM):** lakmago-server hozircha FCM yubormaydi. Buyurtmalar Socket.IO orqali keladi; ilova ochilganda va
> qayta ulanganda kutilayotganlar serverdan sinxronlanadi. Ilova butunlay yopiq bo'lsa signal chalinmaydi (Telegram bot
> xabarlari avvalgidek ishlaydi). FCM server tomonida qo'shilsa, `push/DeviceRegistrar.kt` tokenni shu yerdan yuboradi.

## Release APK

Production imzolangan APK, release guard va qabul tekshiruvi: **[docs/RELEASE.md](docs/RELEASE.md)**. Qisqacha: `./gradlew :app:assembleRelease` (yoki Actions → *Release APK*).

## Env (`env.properties` yoki CI environment)

| Kalit | Ma'nosi |
|---|---|
| `API_URL` | lakmago-server manzili (HTTPS, faqat origin), masalan `https://apilokma.poppolizol.uz` |
| `PROD_API_HOST` | Release qabul qiladigan yagona host (default `apilokma.poppolizol.uz`) |
| `API_PATH_PREFIX` | REST prefiksi (default `api/`) |
| `SOCKET_URL`, `SOCKET_PATH` | Socket.IO boshqa hostda bo'lsa (bo'sh = `API_URL` bilan bir xil) |
| `VERSION_CODE`, `VERSION_NAME` | Har Play release'da `VERSION_CODE` +1 |
| `KEYSTORE_*`, `KEY_*` | Release imzosi |

> Mobil ilovadagi har qanday qiymat APK'dan chiqarib olinishi mumkin — ilovada hech qanday sir yo'q.
> Haqiqiy himoya — restoran JWT'si va serverda tokendan olinadigan `restaurantId`.
> Server domenini lakmago-serverning `J_ROUTE_HOSTS` ro'yxatiga qo'shmang (u yerda `/api` 404 qaytaradi).

## Buyurtma navbati (5–6 ta buyurtma birdaniga kelganda)

`domain/OrderQueue.kt` — FIFO navbat. Bitta buyurtma to'liq ekranda ko'rsatiladi, qolganlari
"Navbatda yana N ta" bo'lib kutadi. **Bitta** signal butun navbatga xizmat qiladi (5 ta ovoz ustma-ust tushmaydi).
Qabul qilingach keyingisi avtomatik chiqadi; signal navbat bo'shaguncha davom etadi.
`Qabul qilish` so'rovlari `Mutex` bilan ketma-ket bajariladi; tarmoq bo'lmasa buyurtma "qabul qilindi" deb ko'rsatilmaydi.
Manbalar (Socket.IO, FCM, pending sync) `orderId` bo'yicha deduplikatsiya qilinadi.

## Buyurtmani yakunlashni nazorat qilish (delivery reminder)

Buyurtma `Yetkazilmoqda` holatiga o'tgach, agar backend belgilangan vaqt ichida "Yetkazildi" kelmasa,
serverdan eslatma keladi (Socket.IO `order:delivery-reminder`, services/restaurantReminders.js) —
`OrderCoordinator.onDeliveryReminder()` oddiy (alarmsiz) notification ko'rsatadi, ikkita amal bilan:
**🟢 Yetkazildi** va **🟡 Jarayonda** (`alert/ReminderActionReceiver.kt` — ilova yopiq bo'lsa ham ishlaydi).
Xuddi shu ikki amal `OrderDetailScreen`da ham bor (order `Yetkazilmoqda` holatida bo'lganda).

**Yetkazildi** — Telegram botdagi "✅ Yakunlandi" bilan bir xil yo'l (`POST /api/panel/orders/:id/confirm-delivered`);
**Jarayonda** — serverda hech narsa o'zgarmaydi, keyingi eslatma jadval bo'yicha keladi (botdagi "⏳ Jarayonda" kabi).
Android hech qachon orderni o'zicha "yakunlangan" deb belgilamaydi; server javobi kutiladi.

## Arxitektura

```
Socket.IO (ochiq) ──────────┐
GET /api/panel/orders      ├─► OrderCoordinator ─► OrderQueue ─► AlertController ─► OrderAlertService (ovoz+vibratsiya)
  ?status=pending (sinxron) ┘  │                                 └► Notifier (kanal: orders_new_v1, HIGH)
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
