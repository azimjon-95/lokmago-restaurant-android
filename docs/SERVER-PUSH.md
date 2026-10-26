> **Holat:** serverda amalga oshirildi — `lakmago-server` `main` `fb3b300` (`src/services/mobilePush.js`, `docs/MOBIL-PUSH.md`). Quyidagi shartnoma o'sha kod bilan bir xil. Audit: [PUSH-AUDIT.md](PUSH-AUDIT.md).

# Server push shartnomasi (lakmago-server uchun)

Hozir lakmago-serverda push yo'q: yopiq iOS ilova va «uxlab qolgan» Android ilova yangi buyurtmani bilmaydi.
Quyidagilar qo'shilsa ikkala platforma ham SMS kabi uyg'otadi. (Avvalgi BFF'dagi `push.js`/`devices.js` namuna sifatida
zaxira arxivda: `lokmago-restaurant-backend.bundle`.)

## 1. Qurilma ro'yxati

`POST /api/panel/devices`  (restoran JWT bilan; `restaurantId` tokendan olinadi, bodyda yo'q)
```json
{ "token": "<FCM token yoki APNs device token (hex)>", "platform": "android" | "ios" }
```
`204`. Bir token faqat bitta restoranda: boshqa restoranga kirilsa eski bog'lanish o'chadi. `DELETE /api/panel/devices`
(body: `{token}`) — chiqishda. Yaroqsiz token (`UNREGISTERED`, APNs `410`) avtomatik o'chiriladi.
Ilova iOS tokenini allaqachon yuboradi (`APIClient.registerDevice`); Android FCM tokenini yuborish
`DeviceRegistrar.register` ichida bir qator (endpoint tayyor bo'lgach yoqiladi).

## 2. Yangi buyurtma → push (restoranning barcha qurilmalariga)

**Android** (FCM HTTP v1) — faqat `data`, `android.priority = "high"` (ilova yopiq bo'lsa ham uyg'otadi):
```json
{ "message": { "token": "…", "android": { "priority": "high", "ttl": "120s" },
  "data": { "type": "order_new", "orderId": "…", "title": "🔔 Yangi buyurtma", "body": "#125 • 128 000 so'm" } } }
```
**iOS** (FCM `apns` bloki yoki to'g'ridan-to'g'ri APNs; `apns-push-type: alert`, `apns-priority: 10`):
```json
{ "aps": { "alert": { "title": "🔔 Yangi buyurtma", "body": "#125 • 128 000 so'm" },
           "sound": "order_alert.wav", "interruption-level": "time-sensitive", "thread-id": "orders" },
  "orderId": "…" }
```
`sound` — ilova ichidagi fayl nomi (≤30 s). Critical Alerts ruxsati kelsa: `"sound": {"critical": 1, "name": "order_alert.wav", "volume": 1.0}`.

## 3. Qabul qilinguncha takrorlash (iOS uchun majburiy)

iOS o'zi takrorlay olmaydi. Server har yangi `pending` buyurtma uchun: **30 soniyada bir marta** (eng ko'pi bilan ~10 marta)
shu push'ni qayta yuborsin va buyurtma `pending` dan chiqishi bilan (qabul / bekor / boshqa qurilma) to'xtatsin.
Android'da takror kerak emas (ilova o'zi chaladi), lekin zararsiz: `OrderQueue` takrorni tashlaydi.

## 4. Yopilgan buyurtma push'i (ixtiyoriy)

`type: "order_updated"` / `"order_cancelled"` (Android data) — ilova ro'yxatni yangilaydi va eski bildirishnomani olib tashlaydi.
iOS uchun `content-available: 1` bilan jim push.
