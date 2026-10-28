> **Holat:** server tomoni amalga oshirilgan — `lakmago-server` `main` (`src/services/mobilePush.js`, `docs/MOBIL-PUSH.md`, `POST /api/panel/devices/test`). Audit va sinov holati: [PUSH-AUDIT.md](PUSH-AUDIT.md).

# iOS buyurtma pushlari

`lakmago-server` da APNs endpointi va pending buyurtmalarni 30 soniyada takrorlab ogohlantirish mexanizmi tayyor. iOS ilova restoran JWT bilan `POST /api/panel/devices` orqali APNs tokenini yuboradi; server restoran doirasini faqat JWT'dan oladi. Chiqishda `DELETE /api/panel/devices` qurilma tokenini ajratadi.

## APNs server sozlamalari

Apple Developer hisobida APNs uchun `.p8` Auth Key yarating va uni serverga repo tashqarisida xavfsiz joylashtiring. Quyidagi qiymatlarni server muhitiga qo‘shing; maxfiy kalitni gitga yoki chatga yubormang:

```env
APNS_KEY_PATH=/secure/path/AuthKey_XXXXXXXXXX.p8
APNS_KEY_ID=XXXXXXXXXX
APNS_TEAM_ID=8A38QL5HR2
APNS_TOPIC=uz.lokmago.restaurant.ios
```

Xcode debug tokeni avtomatik `development`, Release/TestFlight/App Store tokeni `production` sifatida ro‘yxatdan o‘tadi. Sozlama kiritilib API server qayta ishga tushmaguncha qulflangan telefonga push kelmaydi.

## Fon ovozi va to‘xtatish

Server order pending bo‘lib turganida APNs alertni har 30 soniyada takrorlaydi. `orderFlow` buyurtmani `accepted` yoki `cancelled` holatiga o‘tkazib bildirishnomani yopgach, takrorlash to‘xtaydi. iOS bildirishnomasi ilova ichidagi 28 soniyalik `order_alert_long.wav` faylini ishlatadi. Lock-screen’dan “Qabul qilish” yoki “Bekor qilish” tugmasi API’dagi mavjud order status endpointiga boradi.

iOS ilovaga fon rejimida cheksiz audio ijro etishga ruxsat bermaydi. Shu bois push ovozi har safar ko‘pi bilan 30 soniya chalinadi va navbatdagi push 30 soniyadan keyin keladi. iPhone’dagi notification sounds, Focus va tarmoq holati yetkazilish/ovozga ta’sir qiladi. Critical Alerts alohida Apple ruxsatisiz ishlatilmaydi.
