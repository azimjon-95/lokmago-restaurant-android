# Release APK — production

## 1. Xavfsizlik modeli (o'qing)

* **APK ichidagi hamma narsa ochiq.** `API_GATEWAY_URL`, `SOCKET_URL` va `API_GATEWAY_PASSWORD` APK'dan
  1 daqiqada chiqarib olinadi. Shuning uchun ular **secret emas** va backend xavfsizligi ularga tayanmaydi.
  Gateway paroli — faqat qo'pol filtr (`GATEWAY_INBOUND_PASSWORD`, ixtiyoriy).
* **Haqiqiy himoya backendda:** JWT (`Authorization: Bearer`) + `restaurantId` **tokenning ichidan** (`rid`),
  klient hech qachon `restaurantId` yubormaydi. Har bir order/stats/device so'rovi shu `rid` bo'yicha
  filtrlanadi, boshqa restoran orderi → `404`. Backend testlari buni tekshiradi
  (`cross-restaurant isolation`, `concurrent accept`, socket xonalari tokendan aniqlanadi).
* **APK'ga tushmaydigan narsalar:** MongoDB URI, JWT signing secret, Click/Paynet private kalitlari,
  Cloudinary secret, FCM service-account, webhook siri — bular faqat backend `.env`da. Android build ularni
  o'qimaydi ham; `env.properties`da bunday kalit topilsa release build **to'xtaydi**.
* `google-services.json` (Firebase) APK ichiga tushadi — bu klient konfiguratsiyasi, secret emas.
* Release build'da HTTP logging o'chiq, `Log.v/d/i` ProGuard bilan olib tashlanadi, `Authorization` va gateway
  headerlari redakt qilinadi. Cleartext trafik manifestda o'chirilgan.

## 2. Bir martalik tayyorgarlik

1. **Keystore** (repo'dan tashqarida): `scripts/generate-keystore.sh` → `~/.lokmago-signing/lokmago-release.jks`.
   **Zaxira nusxa oling** (parol menejeri + oflayn). Yo'qotsangiz yangilanish chiqara olmaysiz.
   Play Console'da *Play App Signing* yoqing.
2. **Firebase:** `google-services.json`ni `app/` ga qo'ying (git-ignored). Firebase loyihasida **ikkala** ilova
   bo'lishi kerak: `uz.lokmago.restaurant` va `uz.lokmago.restaurant.debug` — aks holda debug build yiqiladi.
   Backend `.env`da `FIREBASE_SERVICE_ACCOUNT_*` to'ldirilgan bo'lishi kerak.
3. `cp env.properties.example env.properties` va to'ldiring (production qiymatlari allaqachon yozilgan;
   `VERSION_CODE`, `VERSION_NAME`, `KEYSTORE_*` ni kiriting). Fayl git-ignored.

## 3. Build

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease          # avval validateReleaseConfig ishlaydi
scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
```

Natija: `app/build/outputs/apk/release/app-release.apk` (imzolangan).

**Google Play uchun AAB:** `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`. Play Console'ga faqat AAB yuklanadi (*Play App Signing* yoqilgan bo'lsin). Xuddi shu release guard AAB'ga ham qo'llanadi.

**Release guard** (`gradle/release-validation.gradle.kts`) quyidagilar bo'lsa build'ni rad etadi:
API/Socket URL `https://restoran-api.lokma.uz` emas (http, boshqa host, port, localhost/10.0.2.2/example/test/staging),
`SOCKET_PATH`/`API_PATH_PREFIX` noto'g'ri, `VERSION_CODE`/`VERSION_NAME` aniq berilmagan, keystore yo'q yoki
parollar bo'sh, `env.properties`da server-secret nomli kalit bor. Debug build faqat `DEBUG_*` kalitlarini
o'qiydi — test server release'ga tushib qola olmaydi.

**GitHub Actions:** *Actions → Release APK → Run workflow* (`version_code`, `version_name`). Secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `GOOGLE_SERVICES_JSON_BASE64`
(+ ixtiyoriy `API_GATEWAY_PASSWORD`). Workflow imzo, host va secret-pattern tekshiruvidan o'tmasa artifact bermaydi.
Repo public bo'lgani uchun artifact ham ko'rinishi mumkin — shu sabab gateway parolini secret deb hisoblamang.

## 4. Qurilmada qabul tekshiruvi (qo'lda; emulyator/telefon kerak)

`adb install -r app-release.apk` va production backend bilan:

| # | Tekshiruv | Kutilgan natija |
|---|---|---|
| 1 | Login + parol (to'g'ri / noto'g'ri; ko'p xato) | to'g'ri → Bosh sahifa; noto'g'ri → «Login yoki parol noto'g'ri»; ko'p xatodan keyin «Kirish vaqtincha bloklandi» |
| 1b | Sessiya: ilovani yopib qayta oching, telefonni qayta yoqing | parol so'ralmaydi; chiqish (logout) qilsangiz login maydoni oldingi login bilan to'ladi |
| 2 | Restoran izolyatsiyasi: A restoran login qilib, B restoran orderini so'rash (`curl` bilan A tokeni + B order id) | `404`, ilovada B ma'lumoti yo'q |
| 3 | API: `Bosh sahifa`, `Buyurtmalar`, `Hisobot` yuklanadi | ma'lumot keladi, 401 yo'q |
| 4 | Socket.IO: ilova ochiq, yangi buyurtma yaratish | to'liq ekranli alert ≤ 2 s |
| 5 | FCM: ilovani yopib (swipe) yangi buyurtma yaratish | notification + ovoz; bosilsa order ochiladi |
| 6 | Order notification: 5–6 ta buyurtma bir vaqtda | navbat bilan, bittadan qabul qilinadi |
| 7 | Status: Qabul → Tayyorlanmoqda → Tayyor → Yetkazilmoqda; delivery'da «Yetkazildi» ≥30 daqiqadan keyin | har biri serverda o'zgaradi; erta bosilsa «Hali erta» xabari; ikkinchi qurilmada realtime ko'rinadi |
| 8 | Reminder: `REMINDER_DELAY_MINUTES`ni test uchun kichraytirib, "Yetkazilmoqda"da qoldirish | eslatma keladi; 🟡 Jarayonda orderni yopmaydi; 🟢 Yetkazildi yopadi |
| 9 | Offline: internetni o'chirib "Qabul qilish" | "qabul qilindi" deb yolg'on ko'rsatilmaydi |

**Loglarda secret yo'qligini tekshirish** (yuqoridagi barcha qadamlardan keyin):

```bash
adb logcat -d | grep -Ei "bearer |authorization|password|eyJ[A-Za-z0-9_-]{10,}\.|fcm.*token|x-gateway" && echo "LEAK!" || echo "clean"
```

## 5. Nima tekshirilgan, nima yo'q (halollik uchun)

Tekshirilgan (avtomatik): release guard (14 stsenariy, Gradle 8.9 ostida), `verify-apk.sh` (sintetik APK'lar
bilan), backend 26/26 test, Gradle wrapper (rasmiy checksum bilan).
**Tekshirilmagan:** haqiqiy lakmago-server bilan birga ishlash (faqat shartnomadan yozilgan soxta server bilan sinalgan), haqiqiy `assembleRelease` va qurilmadagi 1–9 qadamlar — bu yerda Android SDK / Google Maven
mavjud emas edi. Loyiha hali Android Studio'da birinchi marta yig'ilmagan, shuning uchun birinchi build'da
kompilyatsiya yoki bog'liqlik versiyasi xatolari chiqishi mumkin.
