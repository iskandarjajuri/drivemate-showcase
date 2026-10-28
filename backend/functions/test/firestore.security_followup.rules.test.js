/**
 * Tindak lanjut tinjauan keamanan 2026-10-12 (AGENTS/SECURITY_REVIEW_20261012.md):
 * - koleksi khusus-server (`push_markers`, `translation_usage`, `_test_fcm_send_log`, `fcm_tokens`) tertutup untuk SEMUA klien
 *   (tidak ada `match` -> ditolak default; tes ini mengunci agar tak ada yang menambahkan wildcard/izin tanpa sengaja);
 * - pembuatan `workday_plans` hanya oleh admin atau expat PENGELOLA driver itu, dengan `createdByUid` = pemanggil.
 */
const fs = require("fs");
const path = require("path");
const {assertFails, assertSucceeds, initializeTestEnvironment} = require("@firebase/rules-unit-testing");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");
jest.setTimeout(60000);

describe("Firestore rules: tindak lanjut keamanan", () => {
  let testEnv;
  const adminUid = "sf-admin";
  const expatUid = "sf-expat";
  const otherExpatUid = "sf-expat-2";
  const driverUid = "sf-driver";
  let epochDay = 20500;
  let planId = `${driverUid}_${epochDay}`;
  const plan = (over = {}) => ({
    driverUid, dateEpochDay: epochDay, plannedStartMillis: 1700010000000, plannedEndMillis: 1700013600000,
    source: "expat", createdAt: 1700000000000, createdByUid: expatUid, ...over,
  });
  const dbOf = (uid) => testEnv.authenticatedContext(uid).firestore();

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-security-followup",
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      for (const c of ["push_markers", "translation_usage", "_test_fcm_send_log"]) {
        await db.collection(c).doc("seed").set({createdAt: 1});
      }
    });
  });
  afterAll(async () => testEnv.cleanup());

  describe("koleksi khusus-server tertutup untuk semua peran (get/list/create/update/delete)", () => {
    const roles = [["admin", adminUid], ["expat", expatUid], ["driver", driverUid]];
    for (const coll of ["push_markers", "translation_usage", "_test_fcm_send_log"]) {
      test.each(roles)(`${coll}: %s ditolak`, async (_n, uid) => {
        const c = dbOf(uid).collection(coll);
        await assertFails(c.doc("seed").get());
        await assertFails(c.get());
        await assertFails(c.doc("baru").set({createdAt: 2}));
        await assertFails(c.doc("seed").update({createdAt: 3}));
        await assertFails(c.doc("seed").delete());
      });
      test(`${coll}: tanpa autentikasi ditolak`, async () => {
        const c = testEnv.unauthenticatedContext().firestore().collection(coll);
        await assertFails(c.doc("seed").get());
        await assertFails(c.doc("baru").set({createdAt: 2}));
      });
    }
  });

  describe("workday_plans: siapa yang boleh membuat", () => {
    // Satu dokumen baru per tes (id kanonik {uid}_{epochDay}) supaya create tak berubah jadi update.
    beforeEach(() => {
      epochDay += 1;
      planId = `${driverUid}_${epochDay}`;
    });
    test("expat pengelola membuat jadwal untuk driver binaannya", async () => {
      await assertSucceeds(dbOf(expatUid).collection("workday_plans").doc(planId).set(plan()));
    });
    test("admin boleh membuat (source admin)", async () => {
      await assertSucceeds(dbOf(adminUid).collection("workday_plans").doc(planId)
        .set(plan({source: "admin", createdByUid: adminUid})));
    });
    test("expat yang BUKAN pengelola driver itu ditolak", async () => {
      await assertFails(dbOf(otherExpatUid).collection("workday_plans").doc(planId).set(plan({createdByUid: otherExpatUid})));
    });
    test("driver tidak bisa membuat jadwal, termasuk untuk dirinya sendiri", async () => {
      await assertFails(dbOf(driverUid).collection("workday_plans").doc(planId).set(plan({createdByUid: driverUid})));
    });
    test("createdByUid palsu (atas nama admin) ditolak", async () => {
      await assertFails(dbOf(expatUid).collection("workday_plans").doc(planId).set(plan({createdByUid: adminUid})));
    });
    test("tanpa autentikasi ditolak", async () => {
      await assertFails(testEnv.unauthenticatedContext().firestore().collection("workday_plans").doc(planId).set(plan()));
    });
  });
});
