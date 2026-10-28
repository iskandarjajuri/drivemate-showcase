const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

/**
 * R3 — rules untuk `app_config/{doc}` (config batas kecepatan) dan
 * `speed_violations/{doc}` (event pelanggaran, append-only).
 *
 * Dua invariant keamanan yang diuji paling keras di sini:
 * 1. Driver TIDAK boleh mengarang pelanggaran atas nama driver lain (kalau bisa,
 *    dia bisa memicu notifikasi ke expat+admin orang lain).
 * 2. Event pelanggaran TIDAK boleh di-update/hapus klien — nilainya sebagai
 *    catatan keselamatan hilang kalau subjeknya sendiri bisa menghapusnya.
 */
describe("Firestore rules: app_config + speed_violations (R3)", () => {
  let testEnv;

  const adminUid = "admin-uid-r3";
  const expatUid = "expat-uid-r3";
  const otherExpatUid = "expat-uid-r3-other";
  const driverUid = "driver-uid-r3";
  const otherDriverUid = "driver-uid-r3-other";

  const occurredAtMillis = 1700000000000;

  const baseViolation = (overrides = {}) => ({
    driverUid,
    speedKph: 95.5,
    speedLimitKph: 80,
    occurredAtMillis,
    ...overrides,
  });

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-speed-safety",
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({
        type: "driver",
        managerUid: otherExpatUid,
      });

      await db.collection("app_config").doc("safety").set({
        speedLimitKph: 80,
        enabled: true,
      });
      await db.collection("speed_violations").doc("seed-violation").set(baseViolation());
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  // ---------- app_config ----------

  test("driver dapat MEMBACA config batas kecepatan", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("app_config").doc("safety");
    await assertSucceeds(ref.get());
  });

  test("expat dapat membaca config", async () => {
    const ref = testEnv.authenticatedContext(expatUid)
      .firestore().collection("app_config").doc("safety");
    await assertSucceeds(ref.get());
  });

  test("user belum login TIDAK dapat membaca config", async () => {
    const ref = testEnv.unauthenticatedContext()
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.get());
  });

  test("admin dapat menulis config", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety");
    await assertSucceeds(ref.set({speedLimitKph: 90, enabled: true}));
  });

  test("driver TIDAK dapat menulis config", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.set({speedLimitKph: 200, enabled: true}));
  });

  test("expat TIDAK dapat menulis config", async () => {
    const ref = testEnv.authenticatedContext(expatUid)
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.set({speedLimitKph: 200, enabled: true}));
  });

  test("admin TIDAK dapat menulis batas non-positif", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.set({speedLimitKph: 0, enabled: true}));
  });

  test("admin TIDAK dapat menyelundupkan field tak dikenal", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.set({speedLimitKph: 80, enabled: true, rogueField: "x"}));
  });

  // ---------- app_config: payload PERSIS yang ditulis aplikasi (Blok 3.4) ----------
  // Layar Setelan Keselamatan menggantikan penyuntingan manual di Console, jadi mulai
  // sekarang APLIKASI yang bisa merusak dokumen ini. Tiga test di bawah menjaga sisi
  // rules dari fail-closed: bentuk yang sah diterima, dan dua bentuk yang akan
  // menghasilkan "menyala tapi tanpa batas yang sah" ditolak.

  test("admin dapat menulis payload persis milik aplikasi (3 kunci)", async () => {
    // `buildSafetyConfigPayload` — speedLimitKph + enabled + updatedAtMillis.
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety");
    await assertSucceeds(ref.set({
      speedLimitKph: 80,
      enabled: true,
      updatedAtMillis: 1700000000000,
    }));
  });

  test("merge sebagian TANPA speedLimitKph ditolak", async () => {
    // Inilah sebabnya repository menulis PENUH, bukan merge. Kalau rules menerima ini,
    // satu tulisan bisa meninggalkan `enabled: true` tanpa batas yang sah — deteksi mati
    // sementara layar mengaku hidup.
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety-partial");
    await assertFails(ref.set({enabled: true}, {merge: true}));
  });

  test("enabled bukan boolean ditolak", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("app_config").doc("safety");
    await assertFails(ref.set({speedLimitKph: 80, enabled: "true"}));
  });

  // ---------- speed_violations: create ----------

  test("driver dapat membuat event pelanggaran UNTUK DIRINYA SENDIRI", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("own-violation");
    await assertSucceeds(ref.set(baseViolation()));
  });

  test("driver TIDAK dapat mengarang pelanggaran atas nama driver lain", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("forged-violation");
    await assertFails(ref.set(baseViolation({driverUid: otherDriverUid})));
  });

  test("expat TIDAK dapat membuat event pelanggaran", async () => {
    const ref = testEnv.authenticatedContext(expatUid)
      .firestore().collection("speed_violations").doc("expat-created");
    await assertFails(ref.set(baseViolation()));
  });

  test("admin TIDAK dapat membuat event pelanggaran (ini jalur tulis driver)", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("speed_violations").doc("admin-created");
    await assertFails(ref.set(baseViolation()));
  });

  test("field wajib yang hilang ditolak", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("missing-field");
    await assertFails(ref.set({driverUid, speedKph: 95}));
  });

  test("field tak dikenal ditolak", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("rogue-field");
    await assertFails(ref.set(baseViolation({rogueField: "x"})));
  });

  test("tipe salah ditolak", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("bad-type");
    await assertFails(ref.set(baseViolation({speedKph: "cepat"})));
  });

  test("lat/long opsional diterima kalau bertipe number", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("with-coords");
    await assertSucceeds(ref.set(baseViolation({latitude: -6.2, longitude: 106.8})));
  });

  // ---------- speed_violations: append-only ----------

  test("driver TIDAK dapat meng-update event pelanggarannya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertFails(ref.update({speedKph: 10}));
  });

  test("driver TIDAK dapat menghapus event pelanggarannya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertFails(ref.delete());
  });

  test("bahkan admin TIDAK dapat menghapus event pelanggaran (append-only mutlak)", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertFails(ref.delete());
  });

  // ---------- speed_violations: read scoping ----------

  test("driver dapat membaca pelanggarannya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertSucceeds(ref.get());
  });

  test("driver lain TIDAK dapat membaca pelanggaran milik orang", async () => {
    const ref = testEnv.authenticatedContext(otherDriverUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertFails(ref.get());
  });

  test("expat yang me-manage driver dapat membacanya", async () => {
    const ref = testEnv.authenticatedContext(expatUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertSucceeds(ref.get());
  });

  test("expat yang TIDAK me-manage driver itu tidak dapat membacanya", async () => {
    const ref = testEnv.authenticatedContext(otherExpatUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertFails(ref.get());
  });

  test("admin dapat membaca semua pelanggaran", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("speed_violations").doc("seed-violation");
    await assertSucceeds(ref.get());
  });

  // ---------- speed_violations: QUERY (list), bukan get ----------
  // Ini query yang PERSIS dipakai `FirebaseSpeedViolationRepository.listRecentViolations`
  // (Blok 3.3): satu penyaring rentang pada `occurredAtMillis` + orderBy menurun. Test
  // `get()` di atas tidak membuktikan apa pun soal `list` — untuk `list`, rules dievaluasi
  // per dokumen dan kondisi yang menyebut `resource.data` bisa lolos untuk satu dokumen
  // lalu menjatuhkan seluruh query pada dokumen berikutnya.

  const recentQuery = (db) => db.collection("speed_violations")
    .where("occurredAtMillis", ">=", occurredAtMillis - 1000)
    .orderBy("occurredAtMillis", "desc")
    .limit(200);

  test("admin dapat men-query seluruh pelanggaran (jalur layar riwayat)", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(recentQuery(db).get());
  });

  test("driver TIDAK dapat men-query tanpa penyaring driverUid", async () => {
    // Inilah sebabnya `listRecentViolations` admin-only: `isDriver()` menuntut
    // `resource.data.driverUid == request.auth.uid`, dan query tanpa penyaring itu
    // tidak bisa dibuktikan aman oleh rules sebelum dokumennya dibaca.
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(recentQuery(db).get());
  });

  test("expat TIDAK dapat men-query tanpa penyaring driverUid", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    await assertFails(recentQuery(db).get());
  });

  test("driver dapat men-query pelanggarannya sendiri bila disaring driverUid", async () => {
    // Jalur ini SAH menurut rules, tapi sengaja belum dibuka di repository: ia
    // menuntut indeks komposit (driverUid + occurredAtMillis) yang tidak ada di
    // production. Diuji di sini supaya keputusan itu terlihat sebagai pilihan, bukan
    // sebagai batasan rules yang tidak pernah diperiksa.
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("speed_violations").where("driverUid", "==", driverUid).get(),
    );
  });

  // ---------- speed_violation_state: server-only ----------
  // Cloud Function menulisnya lewat Admin SDK (bypass rules). Klien mana pun
  // harus tertutup: kalau driver bisa me-reset penanda cooldown-nya sendiri,
  // dia bisa membanjiri tiga penerima notifikasi sesuka hati.

  test("driver TIDAK dapat membaca state cooldown-nya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violation_state").doc(driverUid);
    await assertFails(ref.get());
  });

  test("driver TIDAK dapat me-reset state cooldown-nya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violation_state").doc(driverUid);
    await assertFails(ref.set({lastNotifiedAtMillis: 0}));
  });

  test("driver TIDAK dapat menghapus state cooldown-nya sendiri", async () => {
    const ref = testEnv.authenticatedContext(driverUid)
      .firestore().collection("speed_violation_state").doc(driverUid);
    await assertFails(ref.delete());
  });

  test("bahkan admin TIDAK dapat menyentuh state cooldown (server-only mutlak)", async () => {
    const ref = testEnv.authenticatedContext(adminUid)
      .firestore().collection("speed_violation_state").doc(driverUid);
    await assertFails(ref.set({lastNotifiedAtMillis: 0}));
  });
});
