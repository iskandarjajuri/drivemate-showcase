const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-affectedkeys";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

jest.setTimeout(60000);

// Mengunci migrasi changedKeys() -> affectedKeys() di empat jalur UPDATE:
// locations, attendances, safety_declarations, vehicle_checklists.
//
// Analisis per-situs (set difference) membuktikan: untuk keempat situs,
//   daftar-update  union  field-dipin  ==  daftar-dokumen-penuh (hasOnlyAllowed),
// jadi TIDAK ADA celah add-field hari ini. Untuk setiap key yang masih di dalam
// skema, affectedKeys().hasOnly(L) berperilaku identik dengan changedKeys().hasOnly(L).
//
// Nilai perubahan ini = pertahanan berlapis terhadap "allowlist drift": bila kelak
// sebuah field ditambahkan ke daftar dokumen-penuh TANPA ikut di daftar-update
// (persis pola kegagalan managerUid, firestore.rules L200-205), changedKeys()
// akan melewatkannya sebagai add-field diam-diam, sedang affectedKeys() menutupnya.
//
// SATU-SATUNYA selisih perilaku yang bisa diuji hari ini: penghapusan key
// LUAR-SKEMA (legacy) yang sudah ada di dokumen. changedKeys() tak melihat
// penghapusan -> update sah yang membuangnya LOLOS sebelum fix; affectedKeys()
// melihatnya sebagai affected -> DITOLAK sesudah fix. Itulah test merah->hijau.
// Tiap koleksi diberi: (a) pin regresi yang lolos sebelum & sesudah fix, dan
// (b) satu test legacy-key sebagai bukti perubahan perilaku yang jujur.
describe("Firestore rules: changedKeys() -> affectedKeys() migration (4 update paths)", () => {
  let testEnv;

  const driverUid = "driver-uid-ak-1";
  const otherDriverUid = "driver-uid-ak-2";
  const adminUid = "admin-uid-ak-1";
  const expatUid = "expat-uid-ak-1";

  // ---- Payloads sah minimal (cermin test checklist_safety yang sudah ada) ----
  const validLocationPayload = (uid, overrides = {}) => ({
    driverUid: uid,
    latitude: -6.2,
    longitude: 106.8,
    updatedAtMillis: 1_700_000_000_000,
    locationName: "Depot A",
    ...overrides,
  });

  const validAttendancePayload = (uid, overrides = {}) => ({
    driverUid: uid,
    timestampMillis: 1_700_000_000_000,
    type: "IN",
    note: "tap in pagi",
    locationName: "Gerbang 1",
    ...overrides,
  });

  const validSafetyPayload = (uid, dateEpochDay, overrides = {}) => ({
    driverUid: uid,
    driverEmail: "budi@example.com",
    dateEpochDay,
    submittedAt: 123,
    fitToWork: true,
    noAlcoholOrDrugs: true,
    noSevereStress: true,
    willFollowSpeedLimit: true,
    willFollowRestBreaks: true,
    willNotUsePhoneWhileDriving: true,
    willWearSeatbelt: true,
    confirmed: true,
    createdAt: 123,
    updatedAt: 123,
    sleepHours: 7,
    ...overrides,
  });

  const validChecklistPayload = (uid, dateEpochDay, overrides = {}) => ({
    driverUid: uid,
    driverEmail: "budi@example.com",
    dateEpochDay,
    submittedAt: 123,
    createdAt: 123,
    updatedAt: 123,
    hasIssue: false,
    notes: "catatan",
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
      await db.collection("users").doc(driverUid).set({type: "driver"});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({
        type: "expat",
        managerUid: adminUid,
      });
      // managedDrivers: expat mengelola driverUid lewat users.managerUid
      await db.collection("users").doc(driverUid).set({
        type: "driver",
        managerUid: expatUid,
      });
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  // =====================================================================
  // locations/{uid} — driver self-update (merge-set per menit di produksi)
  // =====================================================================
  describe("locations update (driver self)", () => {
    const seed = async (overrides = {}) => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context
          .firestore()
          .collection("locations")
          .doc(driverUid)
          .set(validLocationPayload(driverUid, overrides));
      });
    };

    test("[regresi] driver dapat update field posisi yang terdaftar", async () => {
      await seed();
      const db = testEnv.authenticatedContext(driverUid).firestore();
      await assertSucceeds(
        db.collection("locations").doc(driverUid).update({
          latitude: -6.3,
          longitude: 106.9,
          updatedAtMillis: 1_700_000_100_000,
        })
      );
    });

    test("[regresi] driver tetap tidak boleh mengubah driverUid (pinned)", async () => {
      await seed();
      const db = testEnv.authenticatedContext(driverUid).firestore();
      await assertFails(
        db.collection("locations").doc(driverUid).update({
          driverUid: otherDriverUid,
          updatedAtMillis: 1_700_000_200_000,
        })
      );
    });

    // Merah sebelum fix (changedKeys melewatkan penghapusan legacyFlag),
    // hijau sesudah fix (affectedKeys melihatnya -> di luar allowlist -> tolak).
    test("[perilaku] menghapus key legacy luar-skema via full-set DITOLAK", async () => {
      await seed({legacyFlag: "x"});
      const db = testEnv.authenticatedContext(driverUid).firestore();
      // set non-merge yang menjatuhkan legacyFlag tapi tetap dokumen valid:
      await assertFails(
        db
          .collection("locations")
          .doc(driverUid)
          .set(validLocationPayload(driverUid, {updatedAtMillis: 1_700_000_300_000}))
      );
    });
  });

  // =====================================================================
  // attendances/{autoId} — admin/expat koreksi
  // =====================================================================
  describe("attendances update (admin/expat correction)", () => {
    const seed = async (docId, overrides = {}) => {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context
          .firestore()
          .collection("attendances")
          .doc(docId)
          .set(validAttendancePayload(driverUid, overrides));
      });
    };

    test("[regresi] admin dapat mengoreksi note (field terdaftar)", async () => {
      await seed("att-ak-1");
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertSucceeds(
        db.collection("attendances").doc("att-ak-1").update({note: "koreksi"})
      );
    });

    test("[regresi] menghapus locationName opsional (dalam allowlist) tetap LOLOS", async () => {
      await seed("att-ak-2");
      const db = testEnv.authenticatedContext(adminUid).firestore();
      // locationName ada di daftar-update, jadi penghapusannya sah di kedua implementasi.
      const {locationName, ...withoutLocName} = validAttendancePayload(driverUid);
      void locationName;
      await assertSucceeds(
        db.collection("attendances").doc("att-ak-2").set(withoutLocName)
      );
    });

    test("[regresi] admin tetap tidak boleh mengubah type (pinned)", async () => {
      await seed("att-ak-3");
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db.collection("attendances").doc("att-ak-3").update({type: "OUT"})
      );
    });

    test("[perilaku] menghapus key legacy luar-skema via full-set DITOLAK", async () => {
      await seed("att-ak-4", {legacyFlag: "x"});
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db.collection("attendances").doc("att-ak-4").set(validAttendancePayload(driverUid))
      );
    });
  });

  // =====================================================================
  // safety_declarations/{uid_epoch} — admin update
  // =====================================================================
  describe("safety_declarations update (admin)", () => {
    const seed = async (dateEpochDay, overrides = {}) => {
      const docId = `${driverUid}_${dateEpochDay}`;
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context
          .firestore()
          .collection("safety_declarations")
          .doc(docId)
          .set(validSafetyPayload(driverUid, dateEpochDay, overrides));
      });
      return docId;
    };

    test("[regresi] admin dapat update field terdaftar (hazardAwareness)", async () => {
      const docId = await seed(20260201);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertSucceeds(
        db.collection("safety_declarations").doc(docId).update({
          hazardAwareness: "hujan deras",
          updatedAt: 456,
        })
      );
    });

    test("[regresi] menghapus sleepHours opsional (dalam allowlist) tetap LOLOS", async () => {
      const docId = await seed(20260202);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      const base = validSafetyPayload(driverUid, 20260202);
      const {sleepHours, ...withoutSleep} = base;
      void sleepHours;
      await assertSucceeds(
        db.collection("safety_declarations").doc(docId).set(withoutSleep)
      );
    });

    test("[regresi] admin tetap tidak boleh mengubah submittedAt (pinned)", async () => {
      const docId = await seed(20260203);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db.collection("safety_declarations").doc(docId).update({submittedAt: 999})
      );
    });

    test("[perilaku] menghapus key legacy luar-skema via full-set DITOLAK", async () => {
      const docId = await seed(20260204, {legacyFlag: "x"});
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db
          .collection("safety_declarations")
          .doc(docId)
          .set(validSafetyPayload(driverUid, 20260204))
      );
    });
  });

  // =====================================================================
  // vehicle_checklists/{uid_epoch} — admin update
  // =====================================================================
  describe("vehicle_checklists update (admin)", () => {
    const seed = async (dateEpochDay, overrides = {}) => {
      const docId = `${driverUid}_${dateEpochDay}`;
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await context
          .firestore()
          .collection("vehicle_checklists")
          .doc(docId)
          .set(validChecklistPayload(driverUid, dateEpochDay, overrides));
      });
      return docId;
    };

    test("[regresi] admin dapat update field terdaftar (hasIssue/notes)", async () => {
      const docId = await seed(20260301);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertSucceeds(
        db.collection("vehicle_checklists").doc(docId).update({
          hasIssue: true,
          notes: "rem perlu dicek",
          updatedAt: 456,
        })
      );
    });

    test("[regresi] menghapus notes opsional (dalam allowlist) tetap LOLOS", async () => {
      const docId = await seed(20260302);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      const base = validChecklistPayload(driverUid, 20260302);
      const {notes, ...withoutNotes} = base;
      void notes;
      await assertSucceeds(
        db.collection("vehicle_checklists").doc(docId).set(withoutNotes)
      );
    });

    test("[regresi] admin tetap tidak boleh mengubah dateEpochDay (pinned)", async () => {
      const docId = await seed(20260303);
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db.collection("vehicle_checklists").doc(docId).update({dateEpochDay: 20260304})
      );
    });

    test("[perilaku] menghapus key legacy luar-skema via full-set DITOLAK", async () => {
      const docId = await seed(20260305, {legacyFlag: "x"});
      const db = testEnv.authenticatedContext(adminUid).firestore();
      await assertFails(
        db
          .collection("vehicle_checklists")
          .doc(docId)
          .set(validChecklistPayload(driverUid, 20260305))
      );
    });
  });
});
