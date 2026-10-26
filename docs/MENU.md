# Menyu, Stop List va mijoz izohlari

Admin panel (`lakmago-admin`, `MenuPage.jsx` / `StopListPage.jsx`) bilan **bir xil** endpointlar, maydonlar va qoidalar.
Android (`ui/menu`, `ui/reviews`) va iOS (`MenuScreen`, `DishFormScreen`, `StopListScreen`, `ReviewsScreen`) bir xil ishlaydi.

| Amal | Endpoint (restoran JWT bilan; `restaurantId` tokendan olinadi) |
|---|---|
| Ro'yxat (STOPdagilar ham) | `GET /api/panel/dishes` |
| Qo'shish | `POST /api/panel/dishes` |
| Tahrirlash | `PATCH /api/panel/dishes/:id` |
| STOP / qaytarish | `PATCH /api/panel/dishes/:id/stop` `{stop: true\|false}` |
| O'chirish | `DELETE /api/panel/dishes/:id` |
| Rasm imzosi | `GET /api/upload/signature?folder=dishes` → rasm **to'g'ridan Cloudinary'ga** |

**Forma maydonlari** (admin DishForm bilan bir xil): rasm (4:3, ≤5 MB), nom*, tavsif, kategoriya* (22 ta, `section` shundan olinadi),
narx*, eski narx, tayyorlanish vaqti, og'irlik — yoki ichimlikda hajm + ichimlik turi, KBJU (ichimlikda yo'q), zal narxi
(`priceMode` sync/custom), hajm va qo'shimcha guruhlari (variant: kamida 2 ta va har biriga narx; addon: «shart» belgisi).
Guruhlar faqat o'zgartirilganda yuboriladi (eski ma'lumot buzilmasin). Tekshiruv matnlari serverniki bilan bir xil.

**Real vaqt:** server `dish:stop` (restoran xonasiga) va `dish:update` (hammaga) hodisalarini yuboradi. Ilova ularni tinglaydi
(`MenuStore`), 0.8 s ichida bir nechta o'zgarishni bitta yangilashga birlashtiradi. Soket qayta ulanganda ham yangilanadi.
O'z amallari (STOP, tahrir, o'chirish) ro'yxatda darhol ko'rinadi, keyin server bilan tenglashadi. «Stop List» tabidagi
qizil son — to'xtatilgan taomlar soni.

**Rasm yuklash xavfsizligi:** Cloudinary'ga so'rov **alohida** HTTP klient bilan ketadi — ilovaning login tokeni (`Authorization`)
uchinchi tomonga hech qachon yuborilmaydi. Cloudinary sirlari (`apiSecret`) telefonga tushmaydi, faqat serverda hisoblangan imzo keladi.

**Buyurtmadagi taom rasmi:** buyurtma elementida faqat `dishId` bor; rasm jonli menyudan olinadi (Cloudinary `w_…,c_fill` bilan kichik).
Rasm bosilsa to'liq ekranda ochiladi (ikki barmoq bilan kattalashtirish, ikki marta bosish = zoom, ✕ yoki bosish = yopish).

**Mijoz izohlari** (Profil → Biznes → Mijozlar izohlari): server alohida endpoint bermaydi — baho va izoh buyurtmaning o'zida
(`rating`, `comment`, `ratedAt`). Ilova oxirgi 120 kun tarixini (`GET /api/panel/orders/history`, cursor bilan, ≤12 sahifa) o'qib,
bahosi yoki izohi borlarini ko'rsatadi: o'rtacha baho, 1–5 yulduz taqsimoti, filtrlar («Hammasi», 1–5 ★, «Faqat izohli»).
*Tavsiya (serverda):* tezroq bo'lishi uchun `GET /api/panel/reviews?cursor=` qo'shish (faqat `rating`/`comment` borlari).

**Pastki menyu** (Instagram / iPhone uslubi): ekran chetlaridan ajralgan suzuvchi kapsula, faqat ikonkalar, tanlangani yumshoq
tabletka bilan. iOS 26+ da haqiqiy «Liquid Glass» (kontent shisha ostidan o'tadi), eskisida blur material; Android'da yarim shaffof
kapsula. Tanlashda yengil tebranish.

**Menyudagi sonlar (qizil nishon) — serverdan, aniq va jonli:**
- *Buyurtmalar* = serverdagi barcha **yangi (pending)** buyurtmalar. Ro'yxat `?status=pending` bilan alohida olinadi (umumiy ro'yxat
  faqat oxirgi 80 ta, shuning uchun uzoq kutayotgan buyurtma tushib qolmasin). Soket (`order:new/update`) bilan bir zumda, qo'shimcha
  har ~10 soniyada va har ochilganda qayta tekshiriladi.
- *Stop List* = to'xtatilgan taomlar soni. `dish:stop` hodisasidagi serverning o'z `stoppedCount`i darhol qo'yiladi, so'ng ro'yxat
  qayta yuklanadi; arzon `GET /panel/dishes/stopped/count` har ~10 soniyada serverniki bilan qiyoslab, farq bo'lsa ro'yxatni yangilaydi.
  Server hamma o'zgarishda hodisa yubormaydi — to'liq real vaqt uchun: [SERVER-PATCH-realtime.md](SERVER-PATCH-realtime.md).
- 99 dan oshsa «99+».

**Navigatsiya:** Asosiy · Buyurtmalar · **Menyu** · **Stop List** · Profil. «Hisobot» Profil → Biznes ichiga ko'chdi
(5 tabdan ko'p qulay emas).

**Server talabi:** Cloudinary sozlangan bo'lishi kerak (`CLOUDINARY_*` env), aks holda `GET /upload/signature` `503` qaytaradi
va rasm yuklanmaydi (qolgan hamma narsa ishlayveradi).
