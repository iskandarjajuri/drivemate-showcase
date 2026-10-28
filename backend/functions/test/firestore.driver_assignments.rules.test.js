const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: driver_assignments (admin-only daily substitution)", () => {
  let testEnv;

  const adminUid = "admin-uid-da-1";
  const expatUid = "expat-uid-da-1"; // assigned substitute on the seeded doc
  const otherExpatUid = "expat-uid-da-2"; // not assigned
  const driverUid = "driver-uid-da-1";
  const otherDriverUid = "driver-uid-da-2";

  const epochDay = 20300;
  const docId = `${driverUid}_${epochDay}`;
  const createdAt = 1700000000000;

  const baseAssignment = (overrides = {}) => ({
    driverUid,
    expatUid,
    assignedBy: adminUid,
    reason: "SICK",
    epochDay,
    createdAt,
    ...overrides,
  });

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-driver-assignments",
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(otherDriverUid).set({
        type: "driver",
        managerUid: otherExpatUid,
      });

      await db.collection("driver_assignments").doc(docId).set(baseAssignment());
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  // --- Writes: Admin-only ---

  test("admin can create a daily assignment override", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 1}`);

    await assertSucceeds(ref.set(baseAssignment({epochDay: epochDay + 1})));
  });

  test("expat cannot create a daily assignment override", async () => {
    const ref = testEnv
      .authenticatedContext(expatUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 2}`);

    await assertFails(ref.set(baseAssignment({epochDay: epochDay + 2})));
  });

  test("driver cannot create a daily assignment override", async () => {
    const ref = testEnv
      .authenticatedContext(driverUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 3}`);

    await assertFails(ref.set(baseAssignment({epochDay: epochDay + 3})));
  });

  test("create is rejected when assignedBy does not match the caller", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 4}`);

    await assertFails(
      ref.set(baseAssignment({epochDay: epochDay + 4, assignedBy: "someone-else"}))
    );
  });

  test("create is rejected when reason is not one of the allowed values", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 5}`);

    await assertFails(ref.set(baseAssignment({epochDay: epochDay + 5, reason: "VACATION"})));
  });

  test("create is rejected when doc id does not match driverUid_epochDay", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 6}`);

    await assertFails(ref.set(baseAssignment({epochDay: epochDay + 999})));
  });

  test("create is rejected when driverUid equals expatUid", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(`${driverUid}_${epochDay + 7}`);

    await assertFails(ref.set(baseAssignment({epochDay: epochDay + 7, expatUid: driverUid})));
  });

  test("updating createdAt on an existing override is denied", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertFails(ref.update({createdAt: createdAt + 1}));
  });

  // --- Reads ---

  test("admin can read any assignment override", async () => {
    const ref = testEnv
      .authenticatedContext(adminUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertSucceeds(ref.get());
  });

  test("assigned expat can read the override", async () => {
    const ref = testEnv
      .authenticatedContext(expatUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertSucceeds(ref.get());
  });

  test("expat not assigned to this override cannot read it", async () => {
    const ref = testEnv
      .authenticatedContext(otherExpatUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertFails(ref.get());
  });

  test("the driver on the override can read their own override", async () => {
    const ref = testEnv
      .authenticatedContext(driverUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertSucceeds(ref.get());
  });

  test("a different driver cannot read someone else's override", async () => {
    const ref = testEnv
      .authenticatedContext(otherDriverUid)
      .firestore()
      .collection("driver_assignments")
      .doc(docId);

    await assertFails(ref.get());
  });
});

describe("Firestore rules: users/{uid}.managerUid write is Admin-only", () => {
  let testEnv;

  const adminUid = "admin-uid-mgr-1";
  const expatUid = "expat-uid-mgr-1";
  const driverUid = "driver-uid-mgr-1";

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-manager-uid",
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverUid).set({type: "driver"});
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("admin can set managerUid on a driver profile", async () => {
    const ref = testEnv.authenticatedContext(adminUid).firestore()
      .collection("users").doc(driverUid);

    await assertSucceeds(ref.update({managerUid: expatUid}));
  });

  test("expat cannot set managerUid on a driver profile", async () => {
    const ref = testEnv.authenticatedContext(expatUid).firestore()
      .collection("users").doc(driverUid);

    await assertFails(ref.update({managerUid: expatUid}));
  });

  test("driver cannot set their own managerUid", async () => {
    const ref = testEnv.authenticatedContext(driverUid).firestore()
      .collection("users").doc(driverUid);

    await assertFails(ref.update({managerUid: expatUid}));
  });

  // Regression for a bug found while implementing the fcmToken self-write rule
  // (2026-08-20): the SOP-acknowledgement `allow update` clause used to gate
  // on changedKeys(), which silently omits a field the write newly ADDS (as
  // opposed to changing an existing one) — a driver whose doc had never had
  // managerUid set could bundle an arbitrary managerUid into an otherwise
  // legitimate "I accept the SOP" write and have it slip through undetected.
  // Fixed by switching that clause to affectedKeys(). Uses its own fresh
  // driver doc (not the shared driverUid above, which an earlier test in
  // this describe block already gives a managerUid — reusing it here would
  // make this a same-value no-op write and prove nothing).
  test("driver cannot smuggle managerUid in by bundling it with SOP acceptance", async () => {
    const smuggleDriverUid = "driver-uid-mgr-smuggle-1";
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("users").doc(smuggleDriverUid).set({type: "driver"});
    });

    const ref = testEnv.authenticatedContext(smuggleDriverUid).firestore()
      .collection("users").doc(smuggleDriverUid);

    await assertFails(ref.update({
      sopAccepted: true,
      sopAcceptedAtMillis: Date.now(),
      managerUid: expatUid,
    }));
  });
});
