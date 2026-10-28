const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

// projectId UNIK per suite. Sebelumnya suite ini memakai "drivemate-rules-test"
// yang dipakai bersama 10 suite lain, sementara test "admin can LIST users"
// mengasumsikan JUMLAH user persis 6 — begitu suite lain menyeed user ke project
// yang sama, asersi itu gagal. Kegagalannya nyata tapi penyebabnya polusi antar-suite,
// bukan rules. Pola projectId unik ini sudah dipakai 5 suite lain
// (mis. drivemate-rules-test-fcm-token, -speed-safety, -driver-assignments).
const PROJECT_ID = "drivemate-rules-test-users-managed-drivers";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: users managed drivers", () => {
  let testEnv;

  const expatUid = "expat-uid-1";
  const otherExpatUid = "expat-uid-2";
  const managedDriver1Uid = "driver-uid-1";
  const managedDriver2Uid = "driver-uid-2";
  const unmanagedDriverUid = "driver-uid-3";
  const adminUid = "admin-uid-1";

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(managedDriver1Uid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(managedDriver2Uid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(unmanagedDriverUid).set({
        type: "driver",
        managerUid: otherExpatUid,
      });
      await db.collection("users").doc(adminUid).set({type: "admin"});
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("expat can LIST managed drivers only", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const query = db
      .collection("users")
      .where("type", "==", "driver")
      .where("managerUid", "==", expatUid);

    const snap = await assertSucceeds(query.get());
    const ids = snap.docs.map((doc) => doc.id).sort();
    expect(ids).toEqual([managedDriver1Uid, managedDriver2Uid].sort());
  });

  test("admin can LIST users without constraints", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const query = db.collection("users");

    const snap = await assertSucceeds(query.get());
    const ids = snap.docs.map((doc) => doc.id).sort();
    expect(ids).toEqual(
      [adminUid, expatUid, otherExpatUid, managedDriver1Uid, managedDriver2Uid, unmanagedDriverUid].sort()
    );
    expect(snap.size).toBeGreaterThanOrEqual(6);
  });

  test("expat cannot LIST users without required filters", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    // Firestore list is per-document authorized: if query may return any unauthorized
    // doc (non-driver or unmanaged driver), the entire list request is denied.
    const query = db.collection("users");
    await assertFails(query.get());
  });

  test("expat cannot LIST all drivers without managerUid filter", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    // Partial filter still matches unmanaged drivers, so the list must fail.
    const query = db.collection("users").where("type", "==", "driver");
    await assertFails(query.get());
  });

  test("expat cannot LIST drivers for a different managerUid", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    // This query targets drivers managed by another expat; those docs are unauthorized
    // for the caller, so the query must be denied rather than returning empty by policy.
    const query = db
      .collection("users")
      .where("type", "==", "driver")
      .where("managerUid", "==", otherExpatUid);
    await assertFails(query.get());
  });

  test("driver cannot LIST users", async () => {
    const ctx = testEnv.authenticatedContext(managedDriver1Uid);
    const db = ctx.firestore();
    await assertFails(db.collection("users").get());
  });

  test("self GET is allowed for expat", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertSucceeds(db.collection("users").doc(expatUid).get());
  });

  test("self GET is allowed for driver", async () => {
    const ctx = testEnv.authenticatedContext(managedDriver1Uid);
    const db = ctx.firestore();
    await assertSucceeds(db.collection("users").doc(managedDriver1Uid).get());
  });

  test("expat cannot GET unmanaged driver doc", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertFails(db.collection("users").doc(unmanagedDriverUid).get());
  });
});
