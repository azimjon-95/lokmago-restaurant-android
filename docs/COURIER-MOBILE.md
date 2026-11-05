# Kuryer tomoni — mobil ilova (Android + iOS)

Server: `lakmago-server` `docs/COURIER-APP.md`. Admin panelda akkaunt yaratiladi: **Kuryer akkauntlari** (`lakmago-admin`).

## Qanday ishlaydi
* Ilova birinchi ochilganda **«Restoran boshqaruvi»** va **«Kuryer»** tugmalari chiqadi. Har biri o'z login/paroli, o'z tokeni (kuryer tokeni boshqa kalit bilan imzolanadi), o'z ekranlari.
  Tanlov eslab qolinadi; login ekranidagi havola bilan o'zgartiriladi. Restoran sifatida kirib turgan telefonlar tanlov ekranini ko'rmaydi (yangilanishda chiqib ketmaydi).
* **Ish** bo'limi: «Ishni boshladim» → smena ochiladi, joylashuv **har 5 soniyada** serverga (Redis) ketadi; yaqin atrofdagi takliflar ro'yxati (5 s da yangilanadi) + push;
  «Qabul qilish» → faol buyurtma (restoran, mijoz manzili va telefoni, xarita, qo'ng'iroq, olinadigan pul) → «Topshirdim». «Ishni yakunladim» — faol buyurtma bo'lmaganda.
* **Tarix**, **Hisobot** (bugun / 7 / 30 kun: yetkazishlar, yetkazish haqi — faqat ma'lumot, ish vaqti, masofa), **Profil** (push tekshiruvi, chiqish; smena ochiq bo'lsa chiqib bo'lmaydi).
* Kuryerga **alohida melodiya** (`courier_alert.wav`; restoran signalidan farqli): Android — alohida kanal `courier_offers_v1`, iOS — APNs `sound`.

## Android
* `courier/` (API, alohida OkHttp klienti, bildirishnoma, push tokeni, `CourierLocationService`), `ui/courier/` (ekranlar), `ui/AppEntry.kt` (rol).
* Joylashuv: **foreground xizmat** (`foregroundServiceType=location`), ruxsat: aniq/taxminiy joylashuv («ilova ishlatilganda»). `ACCESS_BACKGROUND_LOCATION` KERAK EMAS (xizmat ilova ko'rinib turganda boshlanadi).
  Platforma `LocationManager` (GPS + tarmoq), yangi bog'liqlik yo'q. Server «smena yopiq» (409) yoki 401 desa xizmat o'zi to'xtaydi.
* **Play Console:** foreground-service turi `location` uchun deklaratsiya va matn kerak («Kuryer smenasi davomida buyurtmani yetkazish uchun joylashuv»); Data safety'da joylashuv.

## iOS
* `CourierModel` / `CourierLocation` / `CourierViews` / `CourierModels`; `KeychainStore` kuryer tokeni alohida.
* Joylashuv: `CLLocationManager`, `UIBackgroundModes: location`, «ilova ishlatilganda» ruxsati + `allowsBackgroundLocationUpdates` (ekran tepasida ko'k belgi). «Har doim» ruxsati kerak emas.
* **App Store:** fon rejimidagi joylashuvni review'da asoslash kerak (kuryer smenasi, foydalanuvchi o'zi boshlaydi va to'xtatadi); `Info.plist` da matnlar bor.
* `courier_alert.wav` Xcode loyihasiga resurs sifatida qo'shilgan (≤30 s).

## Qurilmada sinash ro'yxati
1. Admin: kuryer akkaunti yarating. Ilovada «Kuryer» → login → bildirishnoma va joylashuv ruxsati.
2. Profil → **Push tekshiruvi** → test xabar (ilovani yoping, bloklang: kelishi kerak).
3. «Ishni boshladim» → serverda `GET`/Redis: `HGETALL courier:loc:<id>` har 5 s yangilanadi, TTL ≤ 90.
4. Restoran/bot orqali kuryer havolasi yarating → kuryerga push + ro'yxatda taklif (ovoz: kuryer melodiyasi).
5. Ikki kuryer: faqat BIRI qabul qila oladi; ikkinchisida taklif yo'qoladi / xato xabari.
6. Qabul → buyurtma «yo'lda» (restoran panelida kuryer nomi), mijoz kuzatuvida kuryer harakati; mijozga 150 m qolganda «yetib keldi».
7. «Topshirdim» → buyurtma «yetkazildi»; Tarix va Hisobotda ko'rinadi. Smenani yakunlash; ilovani majburan yopib qayta oching — smena/joylashuv serverdagi holatga ergashadi.
8. Telefonni bloklab/ilovani fonga olib 2 daqiqa yuring: nuqtalar Redis'da yangilanib turadi (Android: doimiy bildirishnoma; iOS: ko'k belgi).

## Dizayn va ovoz (mobile polish)
* **Kuryer tomoni — yorug' to'q sariq dizayn** (restoran tomoni qora bo'lib qoladi): Bosh sahifa (salomlashish, **Onlayn** kartasi = smena, bugungi daromad, GPS aniqligi va ulanish holati),
  Buyurtmalar (Faol / Tarix), Daromad (Bugun / Hafta / Oy, to'lovlar tarixi), Profil. Yangi taklif — telefon qo'ng'irog'iga o'xshash to'liq ekran modal: 30 soniyalik hisoblagich, «Rad etish» (faqat shu telefonda yashiradi) / «Qabul qilish».
  Kirish: **faqat login va parol** (SMS bilan telefon raqamni tasdiqlash YO'Q).
* **Birinchi ko'rinadigan ekran:** ilova birinchi ochilganda VA har chiqishdan keyin «Restoran boshqaruvi» / «Kuryer» tanlanadi, so'ng login/parol.
* **Tizim panellari:** qora ilovada soat/antenna/batareya yorug' ikonkalar bilan (telefon mavzusiga bog'liq emas); kuryer (yorug') ekranlarida qora ikonkalar. Buyurtma tafsiloti sarlavhasi status bar ostida, tugmalar navigatsiya paneli ustida.
* **Moslashuvchan:** telefonda o'zgarmaydi; planshet / katlanuvchi / landshaftda kontent markazdagi ustun (640 dp/pt).
* **Bloklangan ekran / boshqa ilova ustida** (Android): qo'ng'iroqqa o'xshash tepadagi bildirishnoma, ikki tugma: **Ovozni o'chirish** va **Ilovaga kirish** (alarm kategoriyasi — «Bezovta qilmang»dan o'tishi mumkin). iPhone: **Ilovaga kirish** va **Qabul qilish**.
* **Aqlli ovoz** (`AlertAttention`, Android'da 46 test): ilovaga kirdingiz → melodiya JUDA past (18%), tafsilotni ochdingiz → yanada past (8%), tafsilotdan hech narsa qilmay chiqdingiz → ~6 s dan keyin ko'tariladi (45%),
  ilovada 20 s tegmadingiz → 45%, ilovadan chiqdingiz/bloklandi → 3 s dan keyin to'liq. Hamma o'tish yumshoq; tebranish faqat ilova ko'rinmaganda. iPhone'da ilova fonda ovoz chalolmaydi — u yerda push (server takrorlaydi).
