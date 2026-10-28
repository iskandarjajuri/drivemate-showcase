const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-addfield-guards";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

jest.setTimeout(60000);

// Mengunci tiga jalur tulis yang PERNAH menerima field arbitrer (add-field liar):
// `announcements` update, `users` create, dan cabang admin `users` update.
// Ketiganya ditemukan lewat sweep empiris di emulator (Gerbang 18, 17 Sep 2026) —
// saat itu operasi di bawah yang ber-assertFails justru BERHASIL.
//
// Cakupannya ADMIN, bukan driver/expat: jalur tulis mereka sudah dijaga sejak awal.
// Jadi ini defense-in-depth (klien admin yang bug/dikompromikan), bukan tambalan
// privilege-escalation oleh pengguna tak-berhak.
//
// Tiap penolakan dipasangkan dengan operasi SAH yang harus tetap lolos. Itu
// disengaja: allow-list yang kelewat ketat akan merusak aplikasi admin di produksi,
// dan kerusakan itu lebih buruk daripada celah yang sedang ditutup. Pasangan
// positif inilah yang menjaga keseimbangan itu tetap terukur.
describe("Firestore rules: guard add-field (announcements update, users create/update)", () => {
  let testEnv;

  const adminUid = "admin-uid-af-1";
  const driverUid = "driver-uid-af-1";
  const sopDriverUid = "driver-uid-af-sop";
  const targetUserUid = "user-uid-af-target";

  const validAnnouncementPayload = (uid, overrides = {}) => ({
    title: "Pemberitahuan rute",
    body: "Besok masuk lebih awal untuk briefing pagi.",
    createdAtMillis: 1_762_000_000_000,
    createdByUid: uid,
    createdByName: "Admin Operasional",
    createdByEmail: "admin@example.com",
    ...overrides,
  });

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(driverUid).set({type: "driver"});
      await db.collection("users").doc(sopDriverUid).set({type: "driver"});
      await db.collection("users").doc(targetUserUid).set({
        type: "driver",
        managerUid: "expat-lama",
      });
      await db.collection("announcements").doc("seed-af").set(
        validAnnouncementPayload(adminUid)
      );
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  describe("announcements update", () => {
    test("admin cannot inject an unlisted field via update", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("announcements").doc("seed-af").update({isPinned: true})
      );
    });

    // Jalur produksi sesungguhnya: FirebaseAnnouncementRepository.updateAnnouncement()
    // mengirim HANYA {title, body} sebagai merge-set. Rules tetap melihat dokumen
    // hasil-tulis yang UTUH — termasuk createdByName/createdByEmail dari create-nya.
    // Karena itu allow-list update harus selebar allow-list create; kalau dipersempit
    // jadi empat field, justru update sah inilah yang pertama patah.
    test("admin can still edit title and body (production path)", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertSucceeds(
        db.collection("announcements").doc("seed-af").update({
          title: "Judul diperbarui",
          body: "Isi pesan yang sudah diperbarui oleh admin.",
        })
      );
    });
  });

  describe("users create", () => {
    test("admin cannot create a user document with an unlisted field", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc("user-uid-af-new-1").set({
          type: "driver",
          managerUid: "expat-uid-af-1",
          internalNote: "x",
        })
      );
    });

    test("admin cannot create a user document without type", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc("user-uid-af-new-2").set({
          name: "Tanpa Peran",
          email: "tanpa-peran@example.com",
        })
      );
    });

    test("admin can still create a well-formed driver document", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertSucceeds(
        db.collection("users").doc("user-uid-af-new-3").set({
          type: "driver",
          managerUid: "expat-uid-af-1",
          name: "Driver Baru",
          email: "driver-baru@example.com",
        })
      );
    });

    // `managerUid` khusus driver, jadi mewajibkannya akan memblokir pembuatan
    // admin dan expat. Hanya `type` yang wajib.
    test("admin can still create an admin document with type only", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertSucceeds(
        db.collection("users").doc("user-uid-af-new-4").set({type: "admin"})
      );
    });
  });

  describe("users update (cabang admin)", () => {
    test("admin cannot inject an unlisted field into a user document", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc(targetUserUid).update({internalNote: "x"})
      );
    });

    test("admin cannot smuggle an unlisted field alongside a legitimate change", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc(targetUserUid).update({
          managerUid: "expat-baru",
          internalNote: "x",
        })
      );
    });

    // Manajemen peran dan penugasan: ini memang hak admin dan WAJIB tetap jalan.
    // Yang ditutup cuma field di luar daftar, bukan kemampuan admin mengubah peran.
    test("admin can still change type and managerUid (role management)", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertSucceeds(
        db.collection("users").doc(targetUserUid).update({
          type: "expat",
          managerUid: "expat-baru",
        })
      );
    });

    test("admin can still edit profile fields name and email", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertSucceeds(
        db.collection("users").doc(targetUserUid).update({
          name: "Nama Diperbarui",
          email: "nama-diperbarui@example.com",
        })
      );
    });
  });

  // Jalur self-write SOP tidak disentuh perbaikan ini dan harus tetap lolos. Jalur
  // legacy `fcmToken` sudah DICABUT: field itu kini termasuk "tambah field baru" yang
  // ditolak untuk siapa pun, termasuk admin. Diuji ulang di sini supaya patahnya
  // langsung terlihat kalau cabang `users` dirapikan lagi suatu hari nanti.
  describe("jalur self-write dan field fcmToken yang dicabut", () => {
    test("driver cannot add new field fcmToken to their own document", async () => {
      const db = testEnv.authenticatedContext(driverUid).firestore();

      await assertFails(
        db.collection("users").doc(driverUid).update({fcmToken: "af".repeat(30) + ":APA91b" + "Z".repeat(60)})
      );
      await assertFails(
        db.collection("users").doc(driverUid).update({fcmToken: ""})
      );
    });

    test("admin cannot add new field fcmToken to a user document", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc(targetUserUid).update({fcmToken: "af".repeat(30) + ":APA91b" + "Z".repeat(60)})
      );
      await assertFails(
        db.collection("users").doc(adminUid).update({fcmToken: "af".repeat(30) + ":APA91b" + "Z".repeat(60)})
      );
    });

    test("admin cannot create a user document with field fcmToken", async () => {
      const db = testEnv.authenticatedContext(adminUid).firestore();

      await assertFails(
        db.collection("users").doc("user-uid-af-new-fcm").set({
          type: "driver",
          fcmToken: "af".repeat(30) + ":APA91b" + "Z".repeat(60),
        })
      );
    });

    test("driver can still acknowledge their own SOP", async () => {
      const db = testEnv.authenticatedContext(sopDriverUid).firestore();

      await assertSucceeds(
        db.collection("users").doc(sopDriverUid).update({
          sopAccepted: true,
          sopAcceptedAtMillis: 1_700_000_000_000,
        })
      );
    });

    test("driver still cannot add an unlisted field to their own document", async () => {
      const db = testEnv.authenticatedContext(driverUid).firestore();

      await assertFails(
        db.collection("users").doc(driverUid).update({internalNote: "x"})
      );
    });
  });
});
