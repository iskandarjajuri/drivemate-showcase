const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {Bytes} = require("@firebase/firestore");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

/**
 * PR-E — rules untuk `user_photos/{uid}` (foto profil driver).
 *
 * Invariant yang diuji paling keras:
 *
 * 1. Foto adalah milik drivernya: hanya pemilik (berperan driver) yang boleh menulis atau
 *    menghapus; doc-id WAJIB sama dengan uid penulis. Admin tidak punya jalur tulis.
 * 2. Pembaca selain pemilik terbatas pada admin dan expat yang MENAUNGI driver itu.
 * 3. Tidak ada `list` — tidak ada layar yang butuh semua foto, dan menolaknya mencegah
 *    penarikan massal blob.
 * 4. Batas 150000 byte ditegakkan di rules (bukan hanya di klien), dan hanya field
 *    `jpeg` + `updatedAtMillis` yang boleh ada.
 */
describe("Firestore rules: user_photos (PR-E)", () => {
  let testEnv;

  jest.setTimeout(60000);

  const adminUid = "admin-uid-photo";
  const expatUid = "expat-uid-photo";
  const otherExpatUid = "expat-uid-photo-other";
  const driverUid = "driver-uid-photo";
  const otherDriverUid = "driver-uid-photo-other";

  const MAX_BYTES = 150000;
  const jpegOf = (size) => Bytes.fromUint8Array(new Uint8Array(size).fill(7));

  const photo = (overrides = {}) => ({
    jpeg: jpegOf(30000),
    updatedAtMillis: 1700000000000,
    ...overrides,
  });

  const seedPhoto = async (uid, data = photo()) => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("user_photos").doc(uid).set(data);
    });
  };

  const col = (uid) =>
    testEnv.authenticatedContext(uid).firestore().collection("user_photos");

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-user-photos",
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
    });
  }, 60000);

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
    });
  });

  // ===== TULIS =====

  test("pemilik driver dapat membuat fotonya sendiri", async () => {
    await assertSucceeds(col(driverUid).doc(driverUid).set(photo()));
  });

  test("pemilik driver dapat mengganti fotonya (update)", async () => {
    await seedPhoto(driverUid);
    await assertSucceeds(
      col(driverUid).doc(driverUid).set(photo({updatedAtMillis: 1700000999000})));
  });

  test("driver lain tidak boleh menulis foto orang lain", async () => {
    await assertFails(col(otherDriverUid).doc(driverUid).set(photo()));
  });

  test("admin tidak punya jalur tulis", async () => {
    await assertFails(col(adminUid).doc(driverUid).set(photo()));
  });

  test("expat tidak punya jalur tulis", async () => {
    await assertFails(col(expatUid).doc(driverUid).set(photo()));
  });

  test("tanpa autentikasi ditolak", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(db.collection("user_photos").doc(driverUid).set(photo()));
    await assertFails(db.collection("user_photos").doc(driverUid).get());
  });

  test("pemilik non-driver (admin menulis dokumennya sendiri) ditolak", async () => {
    await assertFails(col(adminUid).doc(adminUid).set(photo()));
  });

  // ===== BATAS UKURAN & SKEMA =====

  test("tepat 150000 byte diterima", async () => {
    await assertSucceeds(
      col(driverUid).doc(driverUid).set(photo({jpeg: jpegOf(MAX_BYTES)})));
  });

  test("150001 byte ditolak", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set(photo({jpeg: jpegOf(MAX_BYTES + 1)})));
  });

  test("jpeg kosong (0 byte) ditolak", async () => {
    await assertFails(col(driverUid).doc(driverUid).set(photo({jpeg: jpegOf(0)})));
  });

  test("jpeg bertipe string ditolak", async () => {
    await assertFails(col(driverUid).doc(driverUid).set(photo({jpeg: "bukan-bytes"})));
  });

  test("field tambahan ditolak", async () => {
    await assertFails(col(driverUid).doc(driverUid).set(photo({extra: "x"})));
  });

  test("field jpeg hilang ditolak", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set({updatedAtMillis: 1700000000000}));
  });

  test("updatedAtMillis hilang atau bukan int ditolak", async () => {
    await assertFails(col(driverUid).doc(driverUid).set({jpeg: jpegOf(1000)}));
    await assertFails(
      col(driverUid).doc(driverUid).set(photo({updatedAtMillis: "kemarin"})));
  });

  // ===== BACA =====

  test("pemilik dapat membaca fotonya", async () => {
    await seedPhoto(driverUid);
    await assertSucceeds(col(driverUid).doc(driverUid).get());
  });

  test("admin dapat membaca foto driver mana pun", async () => {
    await seedPhoto(driverUid);
    await assertSucceeds(col(adminUid).doc(driverUid).get());
  });

  test("expat penaung dapat membaca foto driver-nya", async () => {
    await seedPhoto(driverUid);
    await assertSucceeds(col(expatUid).doc(driverUid).get());
  });

  test("expat lain (bukan penaung) tidak dapat membaca", async () => {
    await seedPhoto(driverUid);
    await assertFails(col(otherExpatUid).doc(driverUid).get());
  });

  test("driver lain tidak dapat membaca foto driver", async () => {
    await seedPhoto(driverUid);
    await assertFails(col(otherDriverUid).doc(driverUid).get());
  });

  test("list/query seluruh koleksi ditolak, bahkan untuk admin", async () => {
    await seedPhoto(driverUid);
    await assertFails(col(adminUid).get());
    await assertFails(col(driverUid).get());
  });

  // ===== HAPUS =====

  test("pemilik driver dapat menghapus fotonya", async () => {
    await seedPhoto(driverUid);
    await assertSucceeds(col(driverUid).doc(driverUid).delete());
  });

  test("orang lain (driver lain, admin, expat) tidak dapat menghapus", async () => {
    await seedPhoto(driverUid);
    await assertFails(col(otherDriverUid).doc(driverUid).delete());
    await assertFails(col(adminUid).doc(driverUid).delete());
    await assertFails(col(expatUid).doc(driverUid).delete());
  });
});
