# Server: menyu o'zgarishlari uchun real vaqt hodisalari (3 ta joyga 1 qator)

Hozir `lakmago-server` faqat `updateDish` da `dish:stop` / `dish:update` yuboradi. Menyudagi **STOP almashtirgich** (`toggleStop`),
**taom qo'shish** (`createDish`, faqat `admin` xonasiga) va **o'chirish** (`deleteDish`) hech narsa yubormaydi. Ilova buni
`GET /panel/dishes/stopped/count` bilan har ~10 soniyada va har safar ochilganda qiyoslab to'g'rilaydi (son baribir aniq bo'ladi),
lekin boshqa telefon/panelda **bir zumda** ko'rinishi uchun serverga shu yordamchini qo'shing (`src/controllers/restaurantPanel.js`):

```js
// updateDish ichidagi bloknining o'zi — qayta ishlatiladi
async function announceDish(req, dish) {
  const stoppedCount = await Dish.countDocuments({ restaurantId: rid(req), isAvailable: false });
  const io = getIO();
  io?.to(`restaurant:${rid(req)}`).emit('dish:stop', { dishId: String(dish._id), isAvailable: dish.isAvailable, stoppedCount });
  io?.emit('dish:update', { restaurantId: String(rid(req)) });
}
```

`toggleStop`, `createDish` va `deleteDish` ichida javob yuborishdan oldin `await announceDish(req, dish);` chaqiring
(`deleteDish` da `findOneAndDelete` natijasi `dish`). Boshqa hech narsa o'zgarmaydi: ilova `dish:stop` ichidagi `stoppedCount`ni
darhol «Stop List» belgisiga qo'yadi va ro'yxatni 0.8 soniyada qayta yuklaydi. Admin panel `StopListPage` ham xuddi shu hodisani tinglaydi.
