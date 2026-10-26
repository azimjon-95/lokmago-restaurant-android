# Audit: yangi buyurtma xabari kelmadi (ildiz sababi va tuzatish)

## Belgi
Admin panelga buyurtma keldi, telefonda xabar ham, ovoz ham bo'lmadi. Ilova qo'lda ochilgach buyurtma va ovoz keldi.

## Ildiz sabablari
1. **Serverda mobil push yo'q edi.** `lakmago-server` faqat brauzer paneliga Web Push (VAPID) yuborardi; Android (FCM) va iOS
   (APNs) uchun hech narsa yo'q edi.
2. **Ilova tokenni serverga yubormasdi.** `DeviceRegistrar` FCM tokenini faqat telefonda saqlardi (`register` / `unregister` bo'sh edi).
3. Natijada yagona kanal — **Socket.IO**. Android/iOS fonda va qulflangan ekranda soketni to'xtatadi (Doze, App Standby, iOS suspend),
   shuning uchun buyurtma faqat ilova ochilib soket qayta ulanganda kelardi — aynan ko'rilgan belgi.

## Tuzatish
| Qism | O'zgarish | Joy |
|---|---|---|
| Server | FCM HTTP v1 + APNs HTTP/2 push, yagona chiqish nuqtasidan (`emitOrderToRestaurant`), 1-QOIDA alohida tekshiriladi | `lakmago-server` `main` `fb3b300`, `docs/MOBIL-PUSH.md` |
| Server | Qabul qilinmaguncha qayta yuborish (30 s × 10), qabul/bekor bilan to'xtash, dublikat yo'q, o'lik tokenlarni tozalash, monitoring | `services/mobilePush.js` |
| Server | `POST/DELETE/GET /api/panel/devices` | `controllers/mobileDevices.js` |
| Server | Menyu STOP/qo'shish/o'chirish ham real vaqt hodisasi (PR #1) | `services/dishEvents.js` |
| Android | FCM tokeni serverga yuboriladi (ulanishda, yangi tokenda), chiqishda o'chiriladi | `push/DeviceRegistrar.kt` |
| iOS | Chiqishda APNs tokeni o'chiriladi (ro'yxatga olish oldin ham bor edi) | `AppModel.logout`, `APIClient` |

Avvalgi bosqichlarda qilingan (o'zgarmagan): Android doimiy listener xizmati, boot receiver, alarm kanalidagi takrorlanuvchi
signal (`OrderAlertService`), full-screen intent, tayyorlik tekshiruvi; iOS AlertCenter va fon eslatmalari.

## Ishlash tartibi
* **Android:** server → FCM (data, HIGH) → ilova jarayoni uyg'onadi (yopiq bo'lsa ham) → `OrderAlertService` alarm ovozi + vibratsiya,
  qabul qilinguncha. Qulflangan ekranda to'liq ekranli «Qabul qilish». Boshqa telefonda qabul qilinsa server `order_updated` yuboradi → signal to'xtaydi.
  Dublikat push bir buyurtmani ikki marta qo'shmaydi (`OrderQueue` id bo'yicha).
* **iOS:** server → APNs: OS o'zi ovozli (≤30 s) «Time Sensitive» xabar ko'rsatadi; ilova o'zi fonda ovoz chala olmaydi, shuning uchun
  server har 30 s qayta yuboradi (bitta xabar almashinadi, to'planib qolmaydi). Ilova ochiq bo'lsa — to'liq ekranli takrorlanuvchi signal.
* **Jim / Bezovta qilmang:** Android alarm kanali jim rejimda ham chaladi (DND «faqat signallar» rejimida tizim sozlamasiga bog'liq).
  iOS'da jim tugmani yorib o'tish faqat Apple **Critical Alerts** entitlement bilan mumkin (alohida ariza; tasdiqlansa `sound.critical`).

## Sinov holati (16 ta stsenariy)
Bu muhitda Android SDK, Xcode va haqiqiy telefon yo'q. Shuning uchun:

| # | Stsenariy | Holat |
|---|---|---|
| 1 | Ilova ochiq — yangi buyurtma | Server tomoni avtomatik testda ✓ (push + soket). Qurilmada **tekshirilmagan** |
| 2 | Qulflangan ekran | Qurilmada **tekshirilmagan** (Android: FCM HIGH + full-screen intent; iOS: Time Sensitive) |
| 3 | Fon rejimi | Qurilmada **tekshirilmagan** |
| 4 | Video / boshqa ilova ochiq | Qurilmada **tekshirilmagan**; Android alarm audio fokusni oladi, iOS banner + ovoz (Focus sozlamasiga bog'liq) |
| 5 | Ilova qo'lda ochilmagan — push | Server yuborish yo'li avtomatik testda ✓ (soxta FCM/APNs). Telefonga yetishi **tekshirilmagan** |
| 6 | Qabul qilinguncha signal davom etadi | Server qayta yuborishi ✓ testda. Android takrorlanuvchi signal kodi bor, qurilmada **tekshirilmagan** |
| 7 | Qabul qilinganda to'xtaydi | Server: takror to'xtaydi + `order_updated` ✓ testda. Qurilmada **tekshirilmagan** |
| 8 | Bekor qilinganda to'xtaydi | Server: `order_cancelled` ✓ testda. Qurilmada **tekshirilmagan** |
| 9 | Internet uzilib-tiklanishi | Ilova: qayta ulanish + pending sinxroni (kod bor); FCM TTL 10 daqiqa. Qurilmada **tekshirilmagan** |
| 10 | Server qayta ishga tushishi | Qayta yuborish holati bazada ✓ (testda). Soket qayta ulanishi — qurilmada **tekshirilmagan** |
| 11 | Ilova majburan yopilgan | Android: FCM data ilovani uyg'otadi, LEKIN ba'zi brendlar (Xiaomi/Huawei/Oppo) «Force stop»dan keyin FCM'ni ham bloklaydi — **OS cheklovi**. iOS: majburan yopilgan ilovaga ham alert push keladi |
| 12 | Telefon qayta yoqilgandan keyin | Android: BootReceiver listenerni tiklaydi, FCM baribir ishlaydi. Qurilmada **tekshirilmagan** |
| 13 | Bir vaqtda bir nechta buyurtma | Server: har biri alohida, dublikatsiz ✓ testda. Ilova navbati (`OrderQueue`) unit testlari bor, bu yerda ishga tushirilmagan |
| 14 | Push ruxsati berilmagan | Ilova buni «Ogohlantirish tayyorligi»da ko'rsatadi; push kelmaydi, soket va ochilganda sinxron ishlaydi — **OS cheklovi** |
| 15 | Ovoz / bildirishnoma o'chirilgan | Tayyorlik tekshiruvi ko'rsatadi va sozlamaga olib boradi — **OS cheklovi** |
| 16 | Android va iOS alohida | Ikkalasi ham qurilmada **tekshirilmagan** |

## Sozlash ro'yxati (push ishlashi uchun SHART)
1. Serverda `FCM_SERVICE_ACCOUNT_BASE64` (Firebase → Service accounts; ilovaning `google-services.json`i bilan bir loyiha).
2. Serverda `APNS_KEY_BASE64`, `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_BUNDLE_ID`, `APNS_PRODUCTION`.
3. Server: `git pull && npm ci && pm2 restart lakmago-server`; logda `[mobilePush] Android(FCM): yoqilgan · iOS(APNs): yoqilgan`.
4. Xcode: Signing & Capabilities → **Push Notifications**, **Time Sensitive Notifications** (entitlement fayli ulangan).
5. Telefon: bildirishnoma ruxsati; Android — «Bloklangan ekranda to'liq ekran» (14+), batareya «Cheklovsiz», Xiaomi/Huawei'da «Avto-ishga tushirish».
6. Tekshirish: `GET /api/panel/devices` — telefon ro'yxatda va `lastSentAt` yangilanadi.
