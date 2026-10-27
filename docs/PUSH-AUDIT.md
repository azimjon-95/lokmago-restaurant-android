# Audit: yangi buyurtma xabari kelmadi (ildiz sababi va tuzatish)

## Belgi
Admin panelga buyurtma keldi, telefonda xabar ham, ovoz ham bo'lmadi. Ilova qo'lda ochilgach buyurtma va ovoz keldi.

## Ildiz sabablari
1. **Serverda mobil push yo'q edi.** `lakmago-server` faqat brauzer paneliga Web Push (VAPID) yuborardi; Android (FCM) va iOS
   (APNs) uchun hech narsa yo'q edi.
2. **Ilova tokenni serverga yubormasdi.** `DeviceRegistrar` FCM tokenini faqat telefonda saqlardi (`register` / `unregister` bo'sh edi).
3. Natijada yagona kanal — **Socket.IO**. Android/iOS fonda va qulflangan ekranda soketni to'xtatadi (Doze, App Standby, iOS suspend),
   shuning uchun buyurtma faqat ilova ochilib soket qayta ulanganda kelardi — aynan ko'rilgan belgi.

## Ikkinchi muammo: "ilovaga kirsam uyg'onmoqda" (Offline / "Internet yo'q", bo'sh ekran)
Skrinshotda telefonda LTE bor, lekin ilova **"Internet yo'q"** deb yozardi va ma'lumot bo'sh edi; bir necha soniyadan keyin hammasi chiqardi.
Sabablari (kodda tasdiqlangan):
1. **"Online" = soket ulangan**, internet emas (`online = socketJoined`). Soket hali ulanmagan paytda ham "Internet yo'q" chiqardi (Android'da ham: `net && socket`).
2. **iOS fonda ilovani muzlatadi.** Soket jimgina o'ladi, lekin eski `receive()` xato bermaydi; qayta ulanish `connect()` ichida
   `loop != nil` bo'lgani uchun o'tkazib yuborilardi va backoff 10 s gacha kutardi. Qotgan ulanishni aniqlovchi watchdog yo'q edi.
3. **Eski keep-alive HTTP ulanishi.** Uyg'onganda birinchi so'rovlar o'lik ulanishni qayta ishlatib `-1005 networkConnectionLost` bilan yiqilardi
   (Xcode logidagi `tcp_input … flags=[R.] … TIME_WAIT` — shu); qayta urinish yo'q edi → ekran bo'sh.
4. "Qabul qilish" tugmasi soketga bog'liq edi (qabul REST chaqiruv, soket kerak emas).
5. Ruxsatlar (bildirishnoma) tushuntirishsiz, jimgina so'ralardi.

Tuzatish: `NetworkMonitor` (haqiqiy internet) alohida; ulanish 3 holatli — **Online / Ulanmoqda… / Offline**, "Ulanmoqda" banneri faqat
2.5 s dan ortiq cho'zilsa chiqadi; foreground va tarmoq tiklanishida `reconnectNow()` (backoff kutilmaydi) + eski ulanishlarni yopish;
watchdog (server aytgan `pingInterval+pingTimeout` dan uzoq jimlik → ulanishni uzib qayta ulaydi); GET va idempotent so'rovlar
(`status`, `confirm-delivered`, STOP, qurilma) uzilgan ulanishda 2 marta qayta uriniladi; `URLError` "Xatolik" oynasini chiqarmaydi;
qabul tugmasi faqat internetga bog'liq. **Ruxsatlar:** birinchi kirishda tushuntirishli ketma-ket oyna (Android: bildirishnoma, kanal,
to'liq ekran, batareya + brend ko'rsatmasi; iOS: bildirishnoma, ovoz, bloklangan ekran, Time Sensitive, fonda yangilash, quvvat tejash).
Majburlash yo'q: "Keyinroq" bor, Home'dagi banner eslatib turadi.
`tools/ios-socket-check` — qo'lda yozilgan iOS Socket.IO mijozi protokolini haqiqiy `socket.io` bilan sinaydi (13/13).

## "Baribir uxlab qolyapti" — nima uchun va nima qilish kerak
**Haqiqat:** iPhone ilovani fonda DOIM to'xtatadi (suspend). Hech bir ilova «uxlamaslikka» majburlanmaydi; buning yo'llari
(ovozsiz audio, doimiy lokatsiya, VoIP) App Store'da rad etiladi va batareyani bir kunda bo'shatadi. Yopiq/qulflangan iPhone'ni
uyg'otishning **yagona haqiqiy yo'li — serverdan APNs push**. Android'da esa doimiy xizmat + FCM ishlaydi.

Push zanjiri 5 halqadan iborat; bittasi uzilsa jimgina ishlamaydi. Endi ilovaning o'zi tekshiradi:
**Profil → Ogohlantirish tayyorligi → «Push tekshiruvi»** (Android va iOS):
1. bildirishnoma ruxsati → 2. telefon push tokeni (iOS: APNs, Android: FCM) → 3. token serverda ro'yxatda →
4. serverda APNs/FCM sozlangan → 5. **«Test push yuborish»** (server provayderga haqiqiy xabar yuboradi, javobi aynan ko'rsatiladi:
`BadDeviceToken` → sandbox/production mos emas, `DeviceTokenNotForTopic` → Bundle ID, `InvalidProviderToken` → kalit/Team ID/Key ID ...).
Test bosilgach ilovani yoping va telefonni bloklang: 5-10 soniyada «Push ishlayapti ✓» kelishi kerak. Kelsa — yangi buyurtma ham keladi.

Bu bosqichda topilgan yana bir xato: oldingi build iOS'da `registerForRemoteNotifications()` ni har ishga tushirishda chaqirmay qo'ygan edi
(ruxsat allaqachon berilgan foydalanuvchilarda token serverga umuman bormasdi) — tuzatildi (`AlertCenter.registerForPushIfAllowed`).

**Kassa rejimi** (Sozlamalar → «Ekran o'chmasin»): ilova ochiq paytda ekran o'chmaydi — stolda zaryadda turadigan telefon uchun eng
ishonchli yo'l (ilova doim jonli, soket doim ulangan, signal darhol). iPhone'ni boshqa ilova ustiga chiqmaslik uchun «Guided Access» bilan qulflash mumkin.

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
