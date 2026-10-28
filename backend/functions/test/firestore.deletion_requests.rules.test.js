const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {deleteField} = require("@firebase/firestore");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

/**
 * Blok 5 — rules untuk `deletion_requests/{uid}` (gerbang toko aplikasi, PRD §20).
 *
 * Koleksi ini MENCATAT permintaan penghapusan akun; ia tidak menghapus apa pun.
 * Invariant yang diuji paling keras di sini:
 *
 * 1. Seseorang tidak boleh mengarang permintaan atas nama orang lain — doc-id WAJIB
 *    sama dengan uid pemohon, dan `uid` di dalam dokumen harus cocok juga.
 * 2. Klien TIDAK boleh menulis status akhir. Kalau bisa, "sudah dihapus" menjadi klaim
 *    yang dibuat sendiri oleh yang meminta.
 * 3. Admin yang menangani TIDAK boleh menyunting alasan atau stempel waktu permintaan —
 *    itu bukti apa yang diminta dan kapan.
 * 4. Pemohon tidak boleh menghapus jejak permintaannya sendiri, supaya "saya sudah minta
 *    dan tidak ditindaklanjuti" bisa diperiksa dari dua arah.
 */
describe("Firestore rules: deletion_requests (Blok 5)", () => {
  let testEnv;

  const adminUid = "admin-uid-del";
  const driverUid = "driver-uid-del";
  const otherDriverUid = "driver-uid-del-other";
  const handledUid = "driver-uid-del-handled";
  const expatUid = "expat-uid-del";

  const requestedAtMillis = 1700000000000;

  const freshRequest = (uid, overrides = {}) => ({
    uid,
    status: "pending",
    requestedAtMillis,
    ...overrides,
  });

  const seedDoc = async (docId, data) => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("deletion_requests").doc(docId).set(data);
    });
  };

  const col = (uid) =>
    testEnv.authenticatedContext(uid).firestore().collection("deletion_requests");

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-deletion-requests",
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(handledUid).set({type: "driver"});
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(handledUid).set({type: "driver"});
    });
  });

  // ---------- create ----------

  test("driver dapat membuat permintaan untuk DIRINYA SENDIRI", async () => {
    await assertSucceeds(col(driverUid).doc(driverUid).set(freshRequest(driverUid)));
  });

  test("permintaan boleh menyertakan alasan", async () => {
    await assertSucceeds(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {reason: "pindah kerja"}))
    );
  });

  test("driver TIDAK dapat membuat permintaan atas nama driver lain (doc-id)", async () => {
    // Doc-id milik orang lain — inilah jalur pemalsuan yang paling langsung.
    await assertFails(col(driverUid).doc(otherDriverUid).set(freshRequest(otherDriverUid)));
  });

  test("doc-id sendiri tapi field uid milik orang lain juga DITOLAK", async () => {
    // Kalau hanya doc-id yang dijaga, dokumen bisa menunjuk korban yang berbeda dan
    // admin yang membaca field `uid` akan menghapus akun yang salah.
    await assertFails(col(driverUid).doc(driverUid).set(freshRequest(otherDriverUid)));
  });

  test("pengguna anonim TIDAK dapat membuat permintaan", async () => {
    const ref = testEnv.unauthenticatedContext()
      .firestore().collection("deletion_requests").doc(driverUid);
    await assertFails(ref.set(freshRequest(driverUid)));
  });

  test("klien TIDAK dapat membuat permintaan yang langsung berstatus completed", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {status: "completed"}))
    );
  });

  test("klien TIDAK dapat menyertakan field milik admin saat membuat", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set(
        freshRequest(driverUid, {handledByUid: driverUid, handledAtMillis: requestedAtMillis})
      )
    );
  });

  test("field tak dikenal membuat seluruh tulisan ditolak (hasOnly)", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {priority: "urgent"}))
    );
  });

  test("requestedAtMillis harus int, bukan string", async () => {
    await assertFails(
      col(driverUid).doc(driverUid).set(
        freshRequest(driverUid, {requestedAtMillis: String(requestedAtMillis)})
      )
    );
  });

  test("alasan 500 karakter diterima, 501 ditolak", async () => {
    await assertSucceeds(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {reason: "a".repeat(500)}))
    );
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("users").doc(driverUid).set({type: "driver"});
    });
    await assertFails(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {reason: "a".repeat(501)}))
    );
  });

  // ---------- read ----------

  test("pemohon dapat membaca permintaannya sendiri", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertSucceeds(col(driverUid).doc(driverUid).get());
  });

  test("driver lain TIDAK dapat membaca permintaan orang", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(col(otherDriverUid).doc(driverUid).get());
  });

  test("expat TIDAK dapat membaca permintaan driver yang dia kelola", async () => {
    // Ini keputusan sadar: keluar dari perusahaan bukan urusan penyelia langsung.
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(col(expatUid).doc(driverUid).get());
  });

  test("admin dapat membaca dan meng-query semua permintaan", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertSucceeds(col(adminUid).doc(driverUid).get());
    await assertSucceeds(col(adminUid).get());
  });

  test("driver TIDAK dapat meng-query seluruh koleksi", async () => {
    // `get()` yang lolos tidak membuktikan apa pun soal `list()` — dijaga terpisah.
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(col(driverUid).get());
  });

  // ---------- update: pemohon mengirim ulang ----------

  test("pemohon TIDAK dapat mengirim ulang selagi permintaannya masih pending", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(
      col(driverUid).doc(driverUid).set(freshRequest(driverUid, {requestedAtMillis: requestedAtMillis + 1}))
    );
  });

  test("pemohon dapat mengirim ulang setelah permintaannya DITOLAK", async () => {
    await seedDoc(handledUid, {
      ...freshRequest(handledUid),
      status: "rejected",
      handledByUid: adminUid,
      handledAtMillis: requestedAtMillis + 10,
    });
    await assertSucceeds(
      col(handledUid).doc(handledUid).set(
        freshRequest(handledUid, {requestedAtMillis: requestedAtMillis + 100})
      )
    );
  });

  test("mengirim ulang WAJIB menghapus catatan penanganan yang lama", async () => {
    // Permintaan baru yang lahir membawa handledByUid lama akan terbaca seolah sudah
    // ditangani padahal belum.
    await seedDoc(handledUid, {
      ...freshRequest(handledUid),
      status: "rejected",
      handledByUid: adminUid,
      handledAtMillis: requestedAtMillis + 10,
    });
    await assertFails(
      col(handledUid).doc(handledUid).set({
        ...freshRequest(handledUid, {requestedAtMillis: requestedAtMillis + 100}),
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 10,
      })
    );
  });

  test("pemohon TIDAK dapat menutup permintaannya sendiri sebagai completed", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(
      col(driverUid).doc(driverUid).update({
        status: "completed",
        handledByUid: driverUid,
        handledAtMillis: requestedAtMillis + 5,
      })
    );
  });

  // ---------- update: admin menangani ----------

  test("admin dapat menandai permintaan selesai", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertSucceeds(
      col(adminUid).doc(driverUid).update({
        status: "completed",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 5,
        handledNote: "akun dinonaktifkan, catatan upah dipertahankan",
      })
    );
  });

  test("admin dapat menolak permintaan", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertSucceeds(
      col(adminUid).doc(driverUid).update({
        status: "rejected",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 5,
      })
    );
  });

  test("admin TIDAK dapat mengaku sebagai admin lain di handledByUid", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(
      col(adminUid).doc(driverUid).update({
        status: "completed",
        handledByUid: "admin-uid-lain",
        handledAtMillis: requestedAtMillis + 5,
      })
    );
  });

  test("admin TIDAK dapat menyunting alasan yang ditulis pemohon", async () => {
    await seedDoc(driverUid, freshRequest(driverUid, {reason: "pindah kerja"}));
    await assertFails(
      col(adminUid).doc(driverUid).update({
        status: "completed",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 5,
        reason: "mengundurkan diri",
      })
    );
  });

  test("admin TIDAK dapat menghapus alasan yang ditulis pemohon", async () => {
    await seedDoc(driverUid, freshRequest(driverUid, {reason: "pindah kerja"}));
    await assertFails(
      col(adminUid).doc(driverUid).update({
        status: "completed",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 5,
        reason: deleteField(),
      })
    );
  });

  test("admin TIDAK dapat menggeser stempel waktu permintaan", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(
      col(adminUid).doc(driverUid).update({
        status: "completed",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 5,
        requestedAtMillis: requestedAtMillis + 999,
      })
    );
  });

  test("admin TIDAK dapat mengembalikan status ke pending", async () => {
    await seedDoc(driverUid, {
      ...freshRequest(driverUid),
      status: "completed",
      handledByUid: adminUid,
      handledAtMillis: requestedAtMillis + 5,
    });
    await assertFails(
      col(adminUid).doc(driverUid).update({
        status: "pending",
        handledByUid: adminUid,
        handledAtMillis: requestedAtMillis + 6,
      })
    );
  });

  test("expat TIDAK dapat menangani permintaan", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(
      col(expatUid).doc(driverUid).update({
        status: "completed",
        handledByUid: expatUid,
        handledAtMillis: requestedAtMillis + 5,
      })
    );
  });

  // ---------- delete ----------

  test("pemohon TIDAK dapat menghapus jejak permintaannya sendiri", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertFails(col(driverUid).doc(driverUid).delete());
  });

  test("admin dapat menghapus permintaan", async () => {
    await seedDoc(driverUid, freshRequest(driverUid));
    await assertSucceeds(col(adminUid).doc(driverUid).delete());
  });
});
