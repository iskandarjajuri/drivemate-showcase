/**
 * Test KARAKTERISASI — mengunci perilaku yang SEKARANG berlaku, bukan perilaku yang
 * diinginkan. Ia sengaja menegaskan sebuah CACAT yang belum diperbaiki.
 *
 * Fakta yang dikunci: `hasOnlyAllowed(request.resource.data.keys(), ...)` pada UPDATE
 * dinilai terhadap dokumen HASIL MERGE, bukan terhadap field yang sedang ditulis. Maka
 * dokumen yang sudah membawa field di luar allow-list menolak SETIAP update berikutnya,
 * termasuk update yang tidak menyebut field itu sama sekali.
 *
 * Kenapa ini penting dan bukan sekadar teori: audit read-only 2026-09-08 menemukan
 * **3 dokumen `workdays` production** yang membawa `expatDecision` / `expatCorrectionHistory`
 * — ketiganya berstatus `approved`, dan ketiganya tidak bisa dikoreksi admin. Kondisi ini
 * berlaku sejak setidaknya 20 Agustus 2026 (riwayat ruleset), bukan akibat deploy 8 September.
 *
 * Kalau test ini MERAH, kemungkinan besar itu kabar baik: seseorang menerapkan opsi A dari
 * `AGENTS/SESSION_REPORT_20260908_ubuntu_deploy_rules.md` §9.5 dan menambahkan field warisan
 * ke allow-list. Perbarui test ini, jangan kembalikan rules-nya.
 *
 * Suite lain tidak menangkap kelas bug ini karena setiap fixture dibangun dari `baseWorkday()`,
 * yang menurut konstruksinya hanya memakai kunci yang diizinkan — test-nya setuju dengan
 * dirinya sendiri.
 */
const fs = require("fs");
const path = require("path");
const {assertFails, assertSucceeds, initializeTestEnvironment} =
  require("@firebase/rules-unit-testing");
const {deleteField} = require("@firebase/firestore");

const PROJECT_ID = "drivemate-rules-test-workdays-legacy";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: workdays warisan dengan field di luar allow-list", () => {
  let testEnv;
  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const driverUid = "driver-uid-1";
  const clockIn = 1700000000000;
  const clockOut = 1700002880000;

  // Bentuk yang PERSIS ditemukan di production: sudah approved, membawa dua field
  // warisan yang tidak ada di allow-list rules mana pun di blok `workdays`.
  const legacyDoc = {
    driverUid,
    driverName: "Driver X",
    dateEpochDay: 20000,
    clockInMillis: clockIn,
    clockOutMillis: clockOut,
    totalMinutes: 780,
    overtimeMinutes: 0,
    status: "approved",
    approvedByExpat: true,
    approverUid: expatUid,
    approvedByUid: expatUid,
    approvedAtMillis: clockOut,
    updatedAt: clockOut,
    expatDecision: "approved",
    expatCorrectionHistory: [{by: expatUid, at: clockOut}],
  };

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("workdays").doc("legacy-a").set(legacyDoc);
      await db.collection("workdays").doc("legacy-b").set(legacyDoc);
      const kontrol = {...legacyDoc};
      delete kontrol.expatDecision;
      delete kontrol.expatCorrectionHistory;
      await db.collection("workdays").doc("kontrol").set(kontrol);
    });
  });

  afterAll(async () => {
    await testEnv.cleanup();
  });

  const koreksi = (now) => ({
    clockOutMillis: clockOut + 3600000,
    totalMinutes: 840,
    adjustedAt: now,
    adjustedBy: adminUid,
    adjustedReason: "koreksi jam pulang",
    updatedAt: now,
  });

  test("KONTROL: dokumen tanpa field warisan bisa dikoreksi admin", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(db.collection("workdays").doc("kontrol").update(koreksi(clockOut + 9000)));
  });

  test("dokumen warisan DITOLAK saat admin mencoba koreksi biasa", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(db.collection("workdays").doc("legacy-a").update(koreksi(clockOut + 9000)));
  });

  test("dokumen warisan BISA di-update bila field warisan ikut dihapus", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
      db.collection("workdays").doc("legacy-b").update({
        ...koreksi(clockOut + 9000),
        expatDecision: deleteField(),
        expatCorrectionHistory: deleteField(),
      })
    );
  });
});
