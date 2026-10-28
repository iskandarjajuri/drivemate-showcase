// Idempotent demo data for the DriveMate showcase.
//
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:9398 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9399 \
//     node seed.mjs [--sop-pending]
//
// Everything here is FICTIONAL: names, e-mail addresses (@example.com), places, chat.
// The script refuses to run unless it is pointed at the Firebase emulators and a `demo-*`
// project, wipes the emulator data, and re-creates it. Re-running it is the "retake" button:
// today's attendance / checklist / safety documents are deliberately NOT seeded, so the
// driver can submit them on camera (they are limited to one per day by document id).
//
// Seeded translations: the chat translation Cloud Function needs an external LLM API key, so
// the demo stores both sides of each message (original + translation) directly. The document
// shape is exactly what the function writes (see backend/functions/src/translate).

import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";

const PROJECT_ID = process.env.GCLOUD_PROJECT ?? "demo-drivemate";
const DATABASE_ID = "drivemate-database";
const FS_HOST = process.env.FIRESTORE_EMULATOR_HOST;
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const SOP_PENDING = process.argv.includes("--sop-pending");

// ── Safety: never touch anything that is not an emulator ────────────────────────────────
if (!FS_HOST || !AUTH_HOST || !PROJECT_ID.startsWith("demo-")) {
  console.error(
    "Refusing to run: set FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST and use a demo-* project.\n" +
    `  project=${PROJECT_ID} firestore=${FS_HOST ?? "(unset)"} auth=${AUTH_HOST ?? "(unset)"}`
  );
  process.exit(2);
}
console.log(`Seeding EMULATOR  project=${PROJECT_ID}  firestore=${FS_HOST}  auth=${AUTH_HOST}`);

const app = initializeApp({projectId: PROJECT_ID});
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID); // named database: admin.firestore() would silently use "(default)"

// ── Time helpers (Asia/Jakarta is the single source of truth for epoch days) ───────────
const DAY_MS = 86_400_000;
const WIB_OFFSET_MS = 7 * 3_600_000;
const nowMs = Date.now();
const epochDayOf = (ms) => Math.floor((ms + WIB_OFFSET_MS) / DAY_MS);
const today = epochDayOf(nowMs);
const dayOfWeek = (epochDay) => (epochDay + 4) % 7; // 0 = Sunday (1970-01-01 was a Thursday)
const isWeekday = (epochDay) => dayOfWeek(epochDay) >= 1 && dayOfWeek(epochDay) <= 5;
const localMs = (epochDay, hh, mm) => epochDay * DAY_MS - WIB_OFFSET_MS + (hh * 60 + mm) * 60_000;
const epochDayFromIso = (iso) => Math.floor(Date.parse(`${iso}T00:00:00Z`) / DAY_MS);

// Deterministic jitter so a re-seed produces the same history.
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ── People (fictional) ──────────────────────────────────────────────────────────────────
const PASSWORD = "Demo-Pass-1234";
const EXPAT = {
  uid: "demo-expat-tanaka-0000001",
  email: "expat@example.com",
  name: "Kenichi Tanaka",
  type: "expat",
  lang: "ja",
};
const DRIVERS = [
  {uid: "demo-driver-agus-00000001", email: "driver@example.com", name: "Agus Pratama", lang: "id"},
  {uid: "demo-driver-rina-00000002", email: "driver2@example.com", name: "Rina Wulandari", lang: "id"},
  {uid: "demo-driver-joko-00000003", email: "driver3@example.com", name: "Joko Prasetyo", lang: "id"},
  {uid: "demo-driver-sari-00000004", email: "driver4@example.com", name: "Sari Anggraini", lang: "id"},
  {uid: "demo-driver-dimas-0000005", email: "driver5@example.com", name: "Dimas Kurniawan", lang: "id"},
].map((d) => ({...d, type: "driver"}));
const MAIN = DRIVERS[0];
// An admin account exists ONLY to verify that the showcase (which has no admin UI) signs such a session out.
// It is deliberately absent from user_directory so it never shows up in chat contact lists.
const ADMIN = {
  uid: "demo-admin-0000000000007", email: "admin@example.com", name: "Demo Admin", type: "admin", lang: "en",
};
// A brand-new driver with no history and no chat: used to capture honest empty states.
const NEWBIE = {
  uid: "demo-driver-putri-000000006", email: "driver.new@example.com", name: "Putri Maharani",
  lang: "id", type: "driver",
};

// Public landmarks only (never a home, office or plant).
const PLACES = [
  {name: "Monumen Nasional, Jakarta Pusat", lat: -6.175392, lng: 106.827153},
  {name: "Bundaran HI, Jakarta Pusat", lat: -6.195044, lng: 106.823027},
  {name: "Stasiun Gambir, Jakarta Pusat", lat: -6.176651, lng: 106.830615},
];

// ── 0. Wipe emulator state (endpoints exist only on the emulators) ─────────────────────
async function wipe() {
  const fs = await fetch(
    `http://${FS_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/${DATABASE_ID}/documents`,
    {method: "DELETE"}
  );
  const au = await fetch(`http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts`, {method: "DELETE"});
  if (!fs.ok || !au.ok) throw new Error(`wipe failed: firestore=${fs.status} auth=${au.status}`);
}

// ── 1. Auth + users ─────────────────────────────────────────────────────────────────────
async function seedUsers() {
  const people = [EXPAT, ...DRIVERS, NEWBIE];
  await auth.createUser({uid: ADMIN.uid, email: ADMIN.email, password: PASSWORD, displayName: ADMIN.name, emailVerified: true});
  for (const p of people) {
    await auth.createUser({
      uid: p.uid,
      email: p.email,
      password: PASSWORD,
      displayName: p.name,
      emailVerified: true,
    });
  }
  const batch = db.batch();
  batch.set(db.doc(`users/${EXPAT.uid}`), {
    type: "expat", name: EXPAT.name, email: EXPAT.email, lang: "ja", createdAt: nowMs - 200 * DAY_MS,
  });
  for (const d of [...DRIVERS, NEWBIE]) {
    const accepted = d.uid !== NEWBIE.uid && !(SOP_PENDING && d.uid === MAIN.uid); // newcomer has not accepted the SOP yet
    batch.set(db.doc(`users/${d.uid}`), {
      type: "driver", name: d.name, email: d.email, lang: d.lang, managerUid: EXPAT.uid,
      sopAccepted: accepted,
      ...(accepted ? {sopAcceptedAtMillis: nowMs - 120 * DAY_MS} : {}),
      createdAt: nowMs - 150 * DAY_MS,
    });
  }
  batch.set(db.doc(`users/${ADMIN.uid}`), {
    type: "admin", name: ADMIN.name, email: ADMIN.email, lang: ADMIN.lang, createdAt: nowMs - 300 * DAY_MS,
  });
  // user_directory is what the Cloud Function mirrors from users/* (no e-mail, no tokens).
  for (const p of people) {
    batch.set(db.doc(`user_directory/${p.uid}`), {displayName: p.name, role: p.type, lang: p.lang, active: true});
  }
  await batch.commit();
}

// ── 2. Work calendar (2026) ─────────────────────────────────────────────────────────────
async function seedCalendar() {
  const year = new Date(nowMs).getUTCFullYear();
  const holidays = [{iso: `${year}-08-17`, note: "Independence Day (demo data)"}];
  await db.doc(`work_calendars/${year}`).set({
    year,
    timeZoneId: "Asia/Jakarta",
    updatedAtMillis: nowMs,
    defaultRules: {
      mondayToThursday: {startMin: 8 * 60, endMin: 17 * 60, breakMin: 60},
      friday: {startMin: 8 * 60, endMin: 17 * 60, breakMin: 90},
      weekendDefault: "OFF",
    },
    overrides: holidays.map((h) => ({
      dateEpochDay: epochDayFromIso(h.iso), kind: "HOLIDAY", note: h.note, updatedAtMillis: nowMs,
    })),
  });
  return new Set(holidays.map((h) => epochDayFromIso(h.iso)));
}

// ── 3. Three months of attendance + workday history ─────────────────────────────────────
async function seedHistory(holidayDays) {
  const writer = db.bulkWriter();
  const WINDOW_DAYS = 100; // a little more than the rolling 3-month window the app reads
  const workingDays = [];
  for (let d = today - WINDOW_DAYS; d < today; d++) {
    if (isWeekday(d) && !holidayDays.has(d)) workingDays.push(d);
  }
  const lastThree = new Set(workingDays.slice(-3));
  const lastTwo = new Set(workingDays.slice(-2));

  for (const [idx, drv] of DRIVERS.entries()) {
    const rand = rng(1000 + idx);
    for (const d of workingDays) {
      if (rand() < 0.04) continue; // an occasional absence keeps the history honest
      const inMin = 7 * 60 + 48 + Math.floor(rand() * 18); // 07:48 – 08:05
      const overtime = rand() < 0.22 ? 15 * (1 + Math.floor(rand() * 6)) : 0; // 15 – 90 min
      const outMin = 17 * 60 + Math.floor(rand() * 8) + overtime;
      const clockIn = localMs(d, Math.floor(inMin / 60), inMin % 60);
      const clockOut = localMs(d, Math.floor(outMin / 60), outMin % 60);
      const breakMin = dayOfWeek(d) === 5 ? 90 : 60;
      const netMinutes = Math.round((clockOut - clockIn) / 60_000) - breakMin;
      const overtimeMinutes = Math.max(0, netMinutes - 480);
      const place = PLACES[Math.floor(rand() * PLACES.length)];
      const id = `${drv.uid}_${d}`;

      writer.set(db.doc(`attendance_daily/${id}`), {
        driverUid: drv.uid, epochDay: d, clockInMillis: clockIn, clockOutMillis: clockOut,
        updatedAtMillis: clockOut, locationName: place.name, latitude: place.lat, longitude: place.lng,
      });

      const rejected = drv.uid === DRIVERS[2].uid && lastThree.has(d) && d === workingDays.at(-3);
      const pendingWindow = drv.uid === MAIN.uid ? lastTwo : lastThree;
      const pending = !rejected && pendingWindow.has(d);
      const status = rejected ? "rejected" : pending ? "pending" : "approved";
      const reviewedAt = clockOut + 3_600_000;

      writer.set(db.doc(`workdays/${id}`), {
        driverUid: drv.uid, driverName: drv.name, dateEpochDay: d,
        createdAt: clockOut, updatedAt: status === "pending" ? clockOut : reviewedAt,
        clockInMillis: clockIn, clockOutMillis: clockOut,
        totalMinutes: 480 + overtimeMinutes, overtimeMinutes,
        hasChecklist: true, checklistSubmittedAt: clockIn + 600_000,
        hasSafetyDeclaration: true, safetySubmittedAt: clockIn + 540_000,
        plannedStartMillis: localMs(d, 8, 0), plannedEndMillis: localMs(d, 17, 0),
        status,
        ...(status === "approved" ? {
          approvedByExpat: true, approverUid: EXPAT.uid, approverName: EXPAT.name,
          approvedByUid: EXPAT.uid, approvedAtMillis: reviewedAt,
        } : {}),
        ...(status === "rejected" ? {
          approvedByExpat: false, rejectedByUid: EXPAT.uid, rejectedAtMillis: reviewedAt,
          rejectReason: "Clock-out time needs confirmation (demo data).",
          rejectionNote: "Clock-out time needs confirmation (demo data).",
        } : {}),
      });
    }
  }
  await writer.close();
}

// ── 4. Chat (both sides of each message are seeded) ────────────────────────────────────
const threadIdOf = (a, b) => [a, b].sort().join("__"); // mirrors buildOneToOneChatThreadId()

async function seedChat() {
  const specs = [
    {
      driver: DRIVERS[0], unreadFor: null,
      lines: [
        ["d", 26 * 60, "Selamat pagi, Pak Tanaka. Besok saya mulai dari kantor cabang, jam 7.30.",
          "おはようございます、田中さん。明日は支店から出発し、7時30分に開始します。", "polite"],
        ["e", 25 * 60 + 40, "おはようございます。了解しました。到着したら出勤打刻をお願いします。",
          "Selamat pagi. Dimengerti. Mohon lakukan presensi masuk setelah tiba.", "polite"],
        ["d", 25 * 60 + 10, "Baik, Pak. Checklist kendaraan sudah saya isi tadi pagi, semuanya normal.",
          "承知しました。今朝、車両点検チェックリストを記入しました。すべて正常です。", "polite"],
        ["e", 24 * 60 + 30, "ありがとうございます。午後は渋滞が予想されるので、余裕を持って出発してください。",
          "Terima kasih. Sore nanti diperkirakan macet, jadi mohon berangkat lebih awal.", "polite"],
        ["d", 24 * 60 + 5, "Siap, Pak. Saya akan berangkat 30 menit lebih awal.",
          "承知しました。30分早く出発します。", "polite"],
        ["e", 23 * 60 + 50, "お願いします。安全運転でお願いしますね。",
          "Baik, mohon berkendara dengan aman ya.", "polite"],
        ["d", 23 * 60 + 40, "Baik, terima kasih atas perhatiannya, Pak.",
          "はい、お気遣いありがとうございます。", "polite"],
      ],
    },
    {
      driver: DRIVERS[1], unreadFor: null,
      lines: [
        ["d", 5 * 60 + 20, "Pak, ban belakang kiri terlihat agak aus. Mohon arahan.",
          "後輪の左タイヤが少し摩耗しているようです。ご指示をお願いします。", "polite"],
        ["e", 5 * 60, "確認します。今日の午後、整備担当に点検を依頼します。",
          "Saya cek. Sore ini saya minta petugas bengkel memeriksanya.", "polite"],
      ],
    },
    {
      driver: DRIVERS[2], unreadFor: EXPAT.uid,
      lines: [
        ["d", 2 * 60, "Pak Tanaka, hari ini saya izin pulang lebih awal karena ada urusan keluarga.",
          "田中さん、今日は家庭の用事があるため、早退させてください。", "polite"],
      ],
    },
  ];

  for (const spec of specs) {
    const threadId = threadIdOf(spec.driver.uid, EXPAT.uid);
    const participantUids = [spec.driver.uid, EXPAT.uid];
    let last = null;
    for (const [who, minutesAgo, body, translated, register] of spec.lines) {
      const sender = who === "d" ? spec.driver : EXPAT;
      const target = who === "d" ? "ja" : "id";
      const createdAtMillis = nowMs - minutesAgo * 60_000;
      const messageId = `m${String(createdAtMillis)}`;
      await db.doc(`chat_threads/${threadId}/messages/${messageId}`).set({
        threadId, participantUids, senderUid: sender.uid, senderName: sender.name, body,
        createdAtMillis, deleted: false,
        srcLang: who === "d" ? "id" : "ja", register,
        translations: {[target]: {text: translated, uncertain: false}},
        translationStatus: "done", translatedAtMillis: createdAtMillis + 1800, translationModel: "seeded-demo",
      });
      last = {senderUid: sender.uid, preview: body.slice(0, 60), createdAtMillis, deleted: false, messageId};
    }
    await db.doc(`chat_threads/${threadId}`).set({
      participantUids, lastMessage: last, lastMessageAtMillis: last.createdAtMillis,
      unread: Object.fromEntries(participantUids.map((u) => [u, u === spec.unreadFor ? 1 : 0])),
    });
  }
}

// ── 5. Announcements (bilingual on purpose) ─────────────────────────────────────────────
async function seedAnnouncements() {
  const items = [
    [3, "Pengingat keselamatan / 安全のお知らせ",
      "Selalu kenakan sabuk pengaman dan patuhi batas kecepatan.\nシートベルトの着用と制限速度の遵守をお願いします。"],
    [9, "Jadwal libur nasional / 祝日のお知らせ",
      "Tanggal 17 Agustus adalah hari libur nasional (data demo).\n8月17日は祝日です（デモデータ）。"],
    [20, "Pembaruan aplikasi / アプリ更新",
      "Fitur terjemahan otomatis pada chat kini tersedia.\nチャットの自動翻訳機能をご利用いただけます。"],
  ];
  for (const [daysAgo, title, body] of items) {
    const createdAtMillis = nowMs - daysAgo * DAY_MS;
    await db.doc(`announcements/a${createdAtMillis}`).set({
      title, body, createdAtMillis,
      createdByUid: EXPAT.uid, createdByName: "Operations Office", createdByEmail: "operations@example.com",
    });
  }
}

// ── run ─────────────────────────────────────────────────────────────────────────────────
await wipe();
await seedUsers();
const holidayDays = await seedCalendar();
await seedHistory(holidayDays);
await seedChat();
await seedAnnouncements();

console.log(
  `Done. today(epochDay, WIB)=${today}. Logins (password ${PASSWORD}): ` +
  `${[...DRIVERS, NEWBIE].map((d) => d.email).join(", ")}, ${EXPAT.email}` +
  (SOP_PENDING ? "  [driver@example.com has NOT accepted the SOP yet]" : "")
);
