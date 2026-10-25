# Yangi buyurtma ogohlantirishi: SMS kabi, bloklangan telefonda ham

Maqsad: telefon cho'ntakda, ekrani o'chiq bo'lsa ham yangi buyurtma ovoz + vibratsiya bilan xabar bersin va
qabul qilinmaguncha to'xtamasin.

## Android — hozir ishlaydi (serverda push bo'lmasa ham)

| Qatlam | Nima qiladi | Fayl |
|---|---|---|
| **Doimiy ulanish** | Kirgan restoran uchun `OrderListenerService` (foreground, «specialUse») Socket.IO ulanishini ushlab turadi: ekran o'chiq, ilova «swipe» qilingan, telefon qayta yoqilgan bo'lsa ham | `alert/OrderListenerService.kt`, `BootReceiver.kt` |
| **Signal** | Yangi buyurtma → `OrderAlertService` (mediaPlayback): ovoz **alarm kanalida** (jim rejimda ham eshitiladi), vibratsiya, CPU uxlamaydi, buyurtma qabul qilinguncha takrorlanadi | `alert/OrderAlertService.kt` |
| **Bloklangan ekran** | Full-screen intent: ekran o'zi yonadi va «Qabul qilish» ekrani chiqadi | `alert/Notifier.kt`, `MainActivity` |
| **Ovoz balandligi** | Sozlamalar → «Ovozni sozlash» (Past/O'rta/Baland) alarm balandligini ko'taradi va keyin qaytaradi | `OrderAlertService.applyVolume` |
| **Tayyorlik tekshiruvi** | Sozlamalar → «Ogohlantirish tayyorligi»: bildirishnoma, kanal ovozi, bloklangan ekranda to'liq ekran (Android 14+), batareya cheklovi. Har biri bir bosishda tuzatiladi | `alert/AlertReadiness.kt` |

**Telefonda bir marta qilinadigan sozlamalar** (Android hech bir ilovaga buni o'zi berishga ruxsat bermaydi):
1. Bildirishnomalarga ruxsat.
2. «Bloklangan ekranda to'liq ekran» (Android 14+).
3. Batareya → LokmaGo → **Cheklovsiz** (Xiaomi/Huawei/Oppo/Samsung'da qo'shimcha «Avto-ishga tushirish» va «Fon rejimi»ni yoqing).

Google Play: `FOREGROUND_SERVICE_SPECIAL_USE` uchun Play Console'dagi «Foreground service» deklaratsiyasida shuni yozing:
*«Restaurant staff receive new customer orders in real time over a persistent socket and are alerted immediately,
even when the screen is locked.»* `USE_FULL_SCREEN_INTENT` uchun ham deklaratsiya so'raladi.

**Cheklov:** doimiy ulanish batareyani biroz ko'proq ishlatadi. Eng ishonchli yo'l — serverdan FCM push (pastga qarang):
u Doze rejimida ham telefonni uyg'otadi. Ikkalasi birga ishlaydi, takror buyurtma ikki marta jiringlamaydi
(`OrderQueue` id bo'yicha takrorni tashlaydi).

## iOS — nima mumkin, nima yo'q

iOS ilovaga fonda o'zicha ovoz chalishga **ruxsat bermaydi**: ilova yopilsa/bloklansa tizim uni to'xtatadi va soket uziladi.
Shuning uchun:

| Holat | Natija |
|---|---|
| Ilova ochiq | To'liq ekranli signal: takrorlanuvchi ovoz (jim tugma yoqilgan bo'lsa ham eshitiladi) + vibratsiya, qabul qilinguncha |
| Ilova fonda, qabul qilinmagan buyurtma bor | Tizim 6 marta (har ~25 s) ovozli «Time Sensitive» bildirishnoma ko'rsatadi (`AlertCenter.scheduleBackgroundAlerts`) |
| Ilova yopiq / telefon bloklangan | **Faqat serverdan APNs push** uyg'otadi. Push ovozi ≤30 s. Takrorlash — serverning ishi (qabul qilinguncha har 30 s qayta yuboradi) |

Server push'i hozir yo'q → iOS'da yopiq ilova yangi buyurtmani bilmaydi. Shartnoma: [SERVER-PUSH.md](SERVER-PUSH.md).

**«Bezovta qilmang» / Fokus rejimini ham yorib o'tish:** `time-sensitive` (kodda tayyor) yetarli bo'lishi mumkin; to'liq
yorib o'tish (jim tugmani ham) uchun Apple'dan **Critical Alerts** entitlement so'rash kerak
(developer.apple.com/contact/request/notifications-critical-alerts-entitlement). Tasdiqlansa serverda `sound.critical = 1`,
Xcode'da entitlement qo'shiladi.
