# LokmaGo Restoran — iOS

Android ilova bilan **bir xil dizayn va bir xil mantiq** (SwiftUI, iOS 17+): Login, Asosiy, Buyurtmalar (tab hisoblagichlari),
Buyurtma tafsiloti, Hisobot (grafiklar), Sozlamalar (+ «Ogohlantirish tayyorligi»), to'liq ekranli signal.

* Ranglar/o'lchamlar: `DesignTokens.swift` — **generatsiya qilingan** (`design/tokens.json` → `python3 tools/design/gen_tokens.py`),
  Android ham shu fayldan oladi. Ikonkalar: `Assets.xcassets/ic_*` (Android bilan bir xil SVG, `python3 tools/design/gen_ios_icons.py`).
* Server: lakmago-server bilan to'g'ridan-to'g'ri (`Config.swift`), admin panel bilan bir xil login/parol. Real vaqt: Socket.IO
  (`Realtime.swift`, paketsiz), zaxira sifatida har 10 s yangilash (Sozlamalar → «Avto yangilash»).
* Xcode: `LokmaRestaurant.xcodeproj` → Signing & Capabilities: **Push Notifications** va **Time Sensitive Notifications** (entitlement
  fayli ulangan). Haqiqiy qurilmada ishga tushiring.
* Yopiq ilovada ogohlantirish uchun serverdan APNs push kerak: [docs/ALERTS.md](../docs/ALERTS.md), [docs/SERVER-PUSH.md](../docs/SERVER-PUSH.md).
* Ovoz: `tools/sound/make_sounds.sh` (docs/SOUND.md).

Holat: kod Linux'da yozilgan, Xcode'da hali yig'ilmagan. Birinchi build'da kichik kompilyatsiya xatolari chiqishi mumkin —
xato matnini yuboring.
