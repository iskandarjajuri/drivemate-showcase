const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-workday-plans";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: workday_plans provenance immutability", () => {
  let testEnv;

  const adminUid = "admin-uid-plan-1";
  const expatUid = "expat-uid-plan-1";
  const driverManagedUid = "driver-uid-plan-1";
  const driverOtherUid = "driver-uid-plan-2";

  const planEpochDay = 20020;
  const planId = `${driverManagedUid}_${planEpochDay}`;
  const baseStart = 1700010000000;
  const baseEnd = 1700013600000;
  const createdAt = 1700000000000;

  const basePlan = (overrides = {}) => ({
    driverUid: driverManagedUid,
    dateEpochDay: planEpochDay,
    plannedStartMillis: baseStart,
    plannedEndMillis: baseEnd,
    source: "expat",
    createdAt: createdAt,
    createdByUid: expatUid,
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
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverManagedUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(driverOtherUid).set({
        type: "driver",
        managerUid: "someoneElse",
      });

      await db.collection("workday_plans").doc(planId).set(basePlan());
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("expat can update managed workday plan without provenance changes", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workday_plans").doc(planId);

    await assertSucceeds(
      ref.update({
        plannedStartMillis: baseStart + 600000,
        plannedEndMillis: baseEnd + 600000,
        source: "expat",
      })
    );
  });

  test("admin can update managed workday plan without provenance changes", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workday_plans").doc(planId);

    await assertSucceeds(
      ref.update({
        plannedStartMillis: baseStart + 900000,
        plannedEndMillis: baseEnd + 900000,
        source: "expat",
      })
    );
  });

  test("updating createdAt is denied for expat and admin", async () => {
    const expatRef = testEnv
      .authenticatedContext(expatUid)
      .firestore()
      .collection("workday_plans")
      .doc(planId);
    const adminRef = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("workday_plans")
      .doc(planId);

    await assertFails(expatRef.update({createdAt: createdAt + 1}));
    await assertFails(adminRef.update({createdAt: createdAt + 2}));
  });

  test("updating createdByUid is denied for expat and admin", async () => {
    const expatRef = testEnv
      .authenticatedContext(expatUid)
      .firestore()
      .collection("workday_plans")
      .doc(planId);
    const adminRef = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("workday_plans")
      .doc(planId);

    await assertFails(expatRef.update({createdByUid: adminUid}));
    await assertFails(adminRef.update({createdByUid: adminUid}));
  });

  // Regression: the daily "plan for today" lookup reads a canonically-named
  // plan doc that usually does not exist. Before the fix the driver/expat read
  // branches were guarded by `resource != null`, so a get on a non-existent doc
  // evaluated with `resource == null` and only admin passed — drivers hit
  // PERMISSION_DENIED on the normal no-override path every ordinary day.
  describe("read of a non-existent plan doc (daily lookup)", () => {
    const missingEpochDay = 20021;
    const ownMissingId = `${driverManagedUid}_${missingEpochDay}`;
    const otherMissingId = `${driverOtherUid}_${missingEpochDay}`;

    test("driver can get their own non-existent canonical plan doc", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertSucceeds(
        db.collection("workday_plans").doc(ownMissingId).get()
      );
    });

    test("driver cannot get another driver's non-existent plan doc", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(otherMissingId).get()
      );
    });

    test("driver cannot get a non-existent doc with a non-canonical id", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc("not-a-canonical-id").get()
      );
    });

    test("driver can still get their own existing plan doc", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertSucceeds(
        db.collection("workday_plans").doc(planId).get()
      );
    });

    test("driver cannot get another driver's existing plan doc", async () => {
      const db = testEnv.authenticatedContext(driverOtherUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(planId).get()
      );
    });

    // Edge cases confirming isUidEpochDocId binds the doc id strictly to the
    // caller's uid (exactly two `_`-parts, part[0] == uid, part[1] all digits).
    // These prove the resource == null branch cannot be abused to read
    // arbitrary non-existent docs.
    test("driver cannot get a non-existent doc with extra underscores", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(`${driverManagedUid}_${missingEpochDay}_extra`).get()
      );
    });

    test("driver cannot get a non-existent doc embedding own uid in the epoch segment", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(`${driverOtherUid}_${driverManagedUid}`).get()
      );
    });

    test("driver cannot get a non-existent doc with a non-numeric epoch segment", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(`${driverManagedUid}_notanumber`).get()
      );
    });

    test("driver cannot get a non-existent doc that only prefixes own uid", async () => {
      const db = testEnv.authenticatedContext(driverManagedUid).firestore();
      await assertFails(
        db.collection("workday_plans").doc(`${driverManagedUid}x_${missingEpochDay}`).get()
      );
    });
  });
});
