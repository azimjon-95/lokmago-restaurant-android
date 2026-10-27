/*
 * iOS Socket.IO mijozi (ios/LokmaRestaurant/Realtime.swift) QO'LDA yozilgan — Swift'ni bu yerda ishga tushirib bo'lmaydi.
 * Bu skript AYNAN o'sha protokol mantiqini (open paketi → "40{token}" → ping "2"/pong "3" → "42[event,payload]",
 * idleLimit, parseEvent) HAQIQIY socket.io serveriga qarshi sinaydi: qo'lda yozilgan mijoz kutubxona bilan gaplasha oladimi?
 * Haqiqiy lakmago-server bilan emas (u JWT va bazani talab qiladi) — shu kutubxonaning o'zi bilan.  npm i && npm run check
 */
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import { WebSocket } from 'ws';

let fails = 0;
const ok = (c, m) => { console.log(`${c ? '  ✓' : '  ✗ FAIL:'} ${m}`); if (!c) fails++; };

// ---- Realtime.swift'dan ko'chirilgan yordamchilar (bir xil mantiq) ----
const idleLimit = (packet) => { try { const j = JSON.parse(packet.slice(1)); if (typeof j.pingInterval === 'number' && typeof j.pingTimeout === 'number') return (j.pingInterval + j.pingTimeout) / 1000 + 5; } catch {} return 50; };
const parseEvent = (packet) => {
  const body = packet.slice(2).replace(/^\d+/, '');
  try { const a = JSON.parse(body); return typeof a[0] === 'string' ? { name: a[0], payload: a.length > 1 && a[1] && typeof a[1] === 'object' && !Array.isArray(a[1]) ? a[1] : null } : null; } catch { return null; }
};

// ---- server: lakmago-server (sockets/io.js) kabi: handshake `auth.token`, har restoran o'z xonasida ----
const http = createServer();
const io = new Server(http, { pingInterval: 300, pingTimeout: 400 });   // tez: ping/pong va watchdog'ni sekundlarda sinash uchun
io.use((s, next) => (s.handshake.auth?.token === 'JWT-R1' ? (s.data.rid = 'r1', next()) : next(new Error('unauthorized'))));
io.on('connection', (s) => s.join(`restaurant:${s.data.rid}`));
await new Promise((r) => http.listen(0, '127.0.0.1', r));
const url = `ws://127.0.0.1:${http.address().port}/socket.io/?EIO=4&transport=websocket`;

/** Swift `session()` ning aynan o'zi: open → 40{token} → ping/pong → 42 */
function client(token, { answerPing = true } = {}) {
  const c = { events: [], joined: false, rejected: false, pings: 0, limit: 0, ws: new WebSocket(url) };
  c.ws.on('message', (raw) => {
    const t = raw.toString();
    if (t.startsWith('0')) { c.limit = idleLimit(t); c.ws.send('40' + JSON.stringify({ token })); }
    else if (t === '2') { c.pings++; if (answerPing) c.ws.send('3'); }
    else if (t.startsWith('40')) c.joined = true;
    else if (t.startsWith('44')) c.rejected = true;
    else if (t.startsWith('42')) { const e = parseEvent(t); if (e) c.events.push(e); }
  });
  return c;
}
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (f, ms = 3000) => { const t = Date.now(); while (Date.now() - t < ms) { if (f()) return true; await wait(20); } return false; };

console.log('\n1) Handshake: open paketi, 40{token}, xonaga qo\'shilish');
const a = client('JWT-R1');
ok(await until(() => a.joined), 'qo\'lda yozilgan mijoz namespace\'ga qo\'shildi (40)');
ok(Math.abs(a.limit - ((300 + 400) / 1000 + 5)) < 1e-9, `idleLimit server aytgan pingInterval+pingTimeout dan olinadi (${a.limit} s)`);
ok(idleLimit('0{"sid":"x"}') === 50 && idleLimit('0garbage') === 50, 'open paketi tushunarsiz bo\'lsa 50 s zaxira qiymati');
const bad = client('NOT-A-TOKEN');
ok(await until(() => bad.rejected), 'noto\'g\'ri token: server 44 (connect error) qaytaradi — mijoz buni rad deb biladi');

console.log('\n2) Hodisalar: faqat O\'Z restoran xonasiga, payload to\'g\'ri o\'qiladi');
const other = client('JWT-R1'); await until(() => other.joined);
io.to('restaurant:r1').emit('dish:stop', { dishId: 'd1', isAvailable: false, stoppedCount: 3 });
io.to('restaurant:r2').emit('order:new', { _id: 'foreign' });
io.to('restaurant:r1').emit('order:update', { _id: 'o1', status: 'accepted' });
ok(await until(() => a.events.length >= 2), 'o\'z xonasidagi 2 hodisa keldi');
const stop = a.events.find((e) => e.name === 'dish:stop');
ok(stop?.payload?.stoppedCount === 3 && stop.payload.isAvailable === false, 'dish:stop payload (stoppedCount=3) Int sifatida o\'qildi — badge shu bilan darhol yangilanadi');
ok(!a.events.some((e) => e.payload?._id === 'foreign'), 'boshqa restoran xonasining hodisasi KELMADI');
ok(parseEvent('42["order:new"]')?.payload === null && parseEvent('42/ns,["x",{}]') === null && parseEvent('42123["ack",{"a":1}]')?.payload?.a === 1, 'payload yo\'q / noma\'lum namespace / ack-id — xatosiz');

console.log('\n3) Ping/pong va watchdog');
ok(await until(() => a.pings >= 2, 2500), 'server ping (2) yuboradi, mijoz pong (3) bilan javob beradi');
const stable = a.pings; await wait(1500);
ok(a.ws.readyState === WebSocket.OPEN && a.pings > stable, 'pong berib turgan ulanish uzilmaydi (1.5 s davomida ochiq)');
const mute = client('JWT-R1', { answerPing: false }); await until(() => mute.joined);
ok(await until(() => mute.ws.readyState === WebSocket.CLOSED, 3000), 'pong BERMAYDIGAN (qotgan) ulanishni server yopadi → mijoz receive() xato oladi va qayta ulanadi');

console.log('\n4) Server qayta ishga tushganda');
const before = a.ws; io.disconnectSockets(true);
ok(await until(() => before.readyState === WebSocket.CLOSED), 'server uzdi — mijoz uzilishni ko\'radi (Swift: receive() throw → onDisconnected → 0.5 s da qayta ulanish)');
const again = client('JWT-R1');
ok(await until(() => again.joined), 'qayta ulanish (yangi websocket + 40{token}) darhol ishlaydi');

for (const c of [a, bad, other, mute, again]) c.ws.terminate();
io.close(); http.close();
console.log(fails ? `\n✗ ${fails} ta xato` : '\n✓ hammasi o\'tdi');
process.exit(fails ? 1 : 0);
