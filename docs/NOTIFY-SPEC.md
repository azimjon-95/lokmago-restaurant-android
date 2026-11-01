# «Smart Order Alert System» ТЗ ↔ amalda (LokmaGo)

Har bir band: **✓ bajarilgan**, **◐ boshqacha, sababi bilan**, **✗ qilinmadi** (sababi), **qurilma** = faqat telefonda tekshiriladi.
Eslatma: bu ТЗ Capacitor/React deb yozilgan, lekin LokmaGo restoran ilovalari **native** (Android: Kotlin/Compose, iOS: SwiftUI) — Capacitor bandlari qo'llanilmaydi.
Server: `lakmago-server` `docs/MOBIL-PUSH.md`. Admin panel: `lakmago-admin` → «Bildirishnomalar».

| ТЗ | Holat |
|---|---|
| §3 Order → push (APNs / FCM), Socket.IO faqat ochiq ilova uchun | ✓ `emitOrderToRestaurant` → `mobilePush`; push soketdan mustaqil |
| §3 Redis + BullMQ navbat | ✗ **qo'shilmadi** (ataylab). Mongo'dagi `OrderPushState` + atomik «egallash» + 10 s taymer bir xil talablarni bajaradi: restartdan keyin tiklanadi, bir nechta server nusxasi takror yubormaydi, dublikat yo'q. BullMQ yangi production bog'liqligi (Redis uzilsa push to'xtaydi) va deploy tasdig'ini talab qiladi. Qayta ko'rib chiqish sharti: bir necha ming buyurtma/daqiqa |
| §4 iOS: APNs, ruxsatlar (alert/sound/badge), custom sound, Time Sensitive | ✓ `order_alert.wav` (≤30 s), `interruption-level: time-sensitive`, tushuntirishli ruxsat oynasi. ◐ fayl nomi `order_new.caf` emas — `.wav` ham to'g'ri format |
| §4.3 Silent / DND / Critical Alerts | ◐ Time Sensitive mute tugmasini yorib o'tmaydi. **Critical Alerts** — Apple'dan alohida ariza (entitlement) kerak; olingach `sound.critical` qo'shiladi. Ariza: developer.apple.com/contact/request/notifications-critical-alerts-entitlement. Kod hali yo'q (entitlement'siz build yiqiladi) |
| §5 Android: yuqori ahamiyatli kanal, ovoz, tebranish, Lock Screen public, POST_NOTIFICATIONS | ✓ kanal `orders_new_v2` (HIGH, PUBLIC). ◐ ID `lokmago_new_orders` emas — mavjud o'rnatilgan telefonlarda kanal sozlamasi saqlanishi uchun o'zgartirilmadi |
| §5.2 Android payload `notification` + `data` | ◐ **data-only, HIGH priority** — ilova bildirishnomani o'zi ko'rsatadi (takrorlanuvchi alarm, dublikat nazorati, qabul/bekorda to'xtatish shunga bog'liq). `notification` bloki ilova yopiq paytda ilova kodini chaqirmasdi |
| §5.3 Full Screen Intent asosiy echim emas | ✓ asosiy yo'l emas (oddiy bildirishnoma + alarm ovozi); mavjud bo'lsa — qo'shimcha, foydalanuvchi ruxsat bergan taqdirda |
| §6 Smart Repeat: 0 / 30 / 60 / 90 s, server boshqaradi, faqat pending, qabul/bekorda to'xtaydi, har retry unikal notificationId, parallel retry yo'q, limit sozlanadi | ✓ interval **30/60/90**, limit **1–20**, takror yoqish/o'chirish (admin panel). `notificationId = orderId:attempt`; atomik egallash; sozlama buyurtma kelgan paytda muzlatiladi. ◐ standart 30 s × 10 marta (ТЗ misoli 4 ta) — adminda o'zgartiriladi. ✗ «eskalatsiya» (masalan Telegram'ga xabar) — ТЗ aniq qoida bermagan, qo'shilmadi |
| §6 iOS ovozni loop qilolmaydi → server qayta yuboradi | ✓ aynan shunday; har push o'z ovozi bilan, bitta xabar almashinadi (`apns-collapse-id`) |
| §7 Token: qurilma boshiga, yangilanganda, logout'da bekor, bir nechta qurilma/xodim | ✓ `POST/DELETE /api/panel/devices`, `onNewToken`, chiqishda DELETE, restoran boshiga ko'p qurilma |
| §8 `deviceTokens` / `notificationLogs` | ◐ `mobiledevices` (+`deviceId`, `appVersion`, `notificationsEnabled`, `soundEnabled`, `lastSeen`) va `notificationlogs` (SENT/FAILED/RECEIVED/OPENED, attempt, provider, xato, TTL 7 kun, unikal indeks). `userId` har qurilmada, `isActive` o'rniga token yaroqsiz bo'lsa o'chiriladi |
| §8 Token shifrlash | ✗ qilinmadi: token o'zi maxfiy kalit emas (faqat shu ilovaga xabar yuboradi, yuborish uchun server kaliti kerak); jurnalda umuman saqlanmaydi, API'da `…abc123` ko'rinadi. Talab bo'lsa — field-level shifrlash alohida baholanadi |
| §9 Queue worker: backoff, timeout, yaroqsiz token, DLQ, restartdan tiklanish, dublikat | ✓ timeout (10 s), vaqtinchalik xato ≠ yaroqsiz token (FCM UNREGISTERED / APNs 410 / 5 ketma-ket xato), holat bazada. ◐ DLQ o'rniga: `FAILED` jurnal + `failCount/lastError` |
| §10 Admin panel: ovoz, tebranish, takror, interval 30/60/90, ovoz turi, test, qurilmalar | ✓ `lakmago-admin` → **Bildirishnomalar** (vite build o'tgan). Android ovoz turi kanal sozlamasi bilan (Android 8+ da dastur o'zgartira olmaydi) |
| §10 Ilovada ruxsat holati | ✓ Home banner, «tayyorlik» ro'yxati, «Push tekshiruvi»; holat serverga yuboriladi va admin panelda «Ruxsat: O'chiq/Yoqilgan» |
| §11 Xavfsizlik: faqat o'z restorani, payloadda mijoz ma'lumoti yo'q, orderId qayta tekshiriladi | ✓ testlangan (izolyatsiya, ack, loglar sirsiz); payloadda faqat raqam va summa |
| §12 Testlar #1–#14 | Quyida |
| §13 Qabul mezonlari | Server tomoni ✓ avtomatik testlarda; telefonda yetkazish — **qurilma** |

## ТЗ §12 — 14 stsenariy
| # | Test | Natija |
|---|---|---|
| 1–3 | iPhone ochiq / fon / bloklangan | server yuboradi ✓ (test); **qurilma** |
| 4 | iPhone force quit | alert push OS orqali keladi (Apple hujjati); **qurilma** |
| 5 | iPhone Silent Mode | ovoz chiqmasligi MUMKIN (OS cheklovi); banner ko'rinadi |
| 6 | Critical Alert | entitlement yo'q — qilinmagan |
| 7–8 | Android fon / bloklangan | server ✓; **qurilma** |
| 9 | Android swipe | FCM data uyg'otadi; **qurilma** |
| 10 | Android Force Stop | **kafolat yo'q** (OS cheklovi); qayta ochilganda sinxron ✓ |
| 11 | Internet uzilishi | qayta ulanishda `syncPending` ✓ (kod + test), **qurilma** |
| 12 | Qabul qilindi → retry to'xtaydi | ✓ avtomatik test (server) |
| 13 | Ikkita qurilma | ✓ avtomatik test (server); **qurilma** |
| 14 | Duplicate push | ✓ avtomatik test (server: bir buyurtma = bir «yangi»; ilova: `OrderQueue`) |
