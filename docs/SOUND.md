# Yangi buyurtma ovozi (mp3)

Ovoz **bitta** faylda turadi:

```
app/src/main/res/raw/order_alert.mp3
```

1. Mp3 faylni shu papkaga `order_alert.mp3` nomi bilan qo'ying.
2. Eski `order_alert.wav` faylini **o'chiring**. Ikkalasi bir nomda qolsa Android "Duplicate resources" xatosini beradi.
3. Ilovani qayta yig'ing. Kodga tegish shart emas.

Qoidalar:
* Fayl nomi faqat kichik harf, raqam va `_` bo'lishi kerak (`order_alert`), bo'sh joy va katta harf mumkin emas.
* Qisqa (2-5 soniya), 1 MB gacha, takrorlanganda "sakrab" qolmaydigan ovoz tanlang. Ilova ochiq paytda ovoz buyurtma qabul qilinguncha takrorlanadi, ilova yopiq paytda bildirishnoma ovozi bir marta chalinadi.
* Mualliflik huquqi: musiqani ruxsat bilan yoki bepul litsenziya (CC0) bilan oling.

**Ovozni keyin almashtirsangiz:** bildirishnoma kanalining ovozi telefon uni bir marta yaratgach qotib qoladi. Telefondagi eski ilovani o'chirib qayta o'rnating, yoki `Notifier.kt` dagi `CH_ORDERS = "orders_new_v2"` ni `v3` qiling.
