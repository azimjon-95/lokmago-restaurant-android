# Statik tekshiruv (kompilyatorsiz, soniyalarda)

Gradle / Xcode kerak emas. Kompilyatsiya xatolarining eng keng tarqalgan turlarini ushlaydi: yetishmayotgan `import`, mavjud bo'lmagan resurs / rasm / dizayn tokeni,
takroriy e'lon (masalan bir xil nomli ikki `val`), noto'g'ri a'zo nomi (`model.xyz`), noto'g'ri komponent yorlig'i. **Kompilyatsiyani almashtirmaydi** — build baribir CI'da.

```
python3 tools/static-check/kt_check.py     # Kotlin: import, resurs, Lg/Dt tokenlari, takroriy xossa, manifest komponentlari
python3 tools/static-check/kt_imports.py   # Kotlin: unutilgan Compose/AndroidX importlari
python3 tools/static-check/sw_check.py     # Swift: Dt tokenlari, rasm aktivlari, AppModel/MenuStore/CourierModel a'zolari, APIClient chaqiruvlari
tools/kotlin-check/run.sh                  # toza Kotlin mantiq testlari (kotlinc bilan)
```
Skriptlar ataylab buzilgan fayllarda sinab ko'rilgan (har bir xato turi ushlanadi).
