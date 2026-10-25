# Maxfiylik siyosati — Lokma Go Restoran (nashrga tayyorlash loyihasi)

> **Nashr qilishdan oldin to‘ldiring:** `[LEGAL_OPERATOR_NAME]`, `[PRIVACY_EMAIL]`, `[SUPPORT_URL]`, `[EFFECTIVE_DATE]`. Backend egasi bilan saqlash muddati, o‘chirish va ulashish amaliyotini tasdiqlang. Bu matn shablon; tasdiqlanmagan amaliyotlar haqida va’da bermang.

**Kuchga kirish sanasi:** [EFFECTIVE_DATE]

Lokma Go Restoran restoran xodimlariga Lokma Go tizimidagi buyurtmalarni boshqarishga yordam beradi. Ushbu siyosatda ilova orqali qayta ishlanishi mumkin bo‘lgan ma’lumotlar tasvirlangan.

## Ma’lumotlar

- **Hisob va autentifikatsiya:** xodim login/paroli kirish uchun serverga yuboriladi; kirishdan so‘ng olingan session token iOS Keychain’da saqlanadi.
- **Buyurtma va mijoz ma’lumotlari:** buyurtmani bajarish uchun API’dan buyurtma tarkibi, buyurtma holati, mijoz ismi, telefon raqami, yetkazib berish manzili, to‘lov turi va buyurtma summalari olinishi mumkin. Ilova bu ma’lumotlarni vakolatli restoran xodimlariga ko‘rsatadi.
- **Texnik ma’lumotlar:** server va tarmoq xatolarini qayta ishlashda so‘rov va qurilma tarmoq ma’lumotlari xizmat ko‘rsatuvchi tizimlarda qayd etilishi mumkin. Aniq loglar, muddat va provayderlar server egasi tomonidan tasdiqlanishi shart.

Ilova kodi tahlil qilinganda reklama, uchinchi tomon analytics yoki tracking SDK aniqlanmadi. Bu server tomoni logging yoki boshqa xizmatlar yo‘qligini anglatmaydi; App Store Connect maxfiylik javoblari backend auditi bilan tasdiqlanishi kerak.

## Foydalanish va ulashish

Ma’lumotlar loginni tekshirish, restoran buyurtmalarini ko‘rsatish va buyurtma holatini yangilash uchun Lokma Go API’ga yuboriladi. Mijoz telefon raqami ilovadan telefon qilish uchun ishlatilishi mumkin. Boshqa xizmat ko‘rsatuvchilar bilan ulashish, qonuniy talablar va ma’lumotlarni saqlash/yo‘q qilish tartibi: **[BACKEND_OPERATOR_TO_CONFIRM]**.

## Saqlash va xavfsizlik

Tarmoq trafigi HTTPS orqali uzatiladi; session token iOS Keychain’da saqlanadi. Serverdagi ma’lumotlarni saqlash muddati, zaxira nusxalari, o‘chirish jarayoni va xavfsizlik choralarini [LEGAL_OPERATOR_NAME] tasdiqlashi lozim.

## Huquqlar va murojaatlar

Hisob, ma’lumotga kirish, tuzatish yoki o‘chirish bo‘yicha restoran administratori yoki [PRIVACY_EMAIL] orqali murojaat qiling. Hisobni o‘chirish server tomonda qanday bajarilishi va muddatlari nashrdan oldin belgilanadi.

## Bolalar

Ilova restoran xodimlari uchun mo‘ljallangan, bolalarga qaratilmagan.

## Siyosatdagi o‘zgarishlar

Yangilangan siyosat [PUBLIC_PRIVACY_URL] manzilida, kuchga kirish sanasi bilan e’lon qilinadi.

## Operator va aloqa

Operator: [LEGAL_OPERATOR_NAME]
Privacy: [PRIVACY_EMAIL]
Yordam: [SUPPORT_URL]

---

## Tasdiqlash ro‘yxati (nashr qilinadigan sahifadan olib tashlang)

- [ ] Yuridik operator nomi, privacy email va support manzilini kiriting.
- [ ] Backend/API egasidan server loglari, ma’lumotlarni oluvchilar va saqlash muddatini yozma tasdiqlating.
- [ ] Hisob va buyurtma ma’lumotlarini o‘chirish/tuzatish jarayonini tasdiqlang.
- [ ] App Store Connect’dagi data type, linked-to-user, tracking va purpose javoblarini shu amaliyotga mos kiriting.
- [ ] Matnni privacy URL’da ommaga ochiq HTTPS sahifa sifatida joylang.
