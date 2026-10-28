const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-attendance-daily";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: attendance_daily", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const managedDriverUid = "driver-uid-1";
  const otherDriverUid = "driver-uid-2";
  const managedEpochDay = 20011;
  const otherEpochDay = 20012;

  const managedDocId = `${managedDriverUid}_${managedEpochDay}`;
  const otherDocId = `${otherDriverUid}_${otherEpochDay}`;

  const clockInPayload = (driverUid, epochDay) => ({
    driverUid,
    epochDay,
    clockInMillis: 1700000000000,
    updatedAtMillis: 1700000000000,
  });

  const clockOutPayload = (driverUid, epochDay) => ({
    driverUid,
    epochDay,
    clockOutMillis: 1700000600000,
    updatedAtMillis: 1700000600000,
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
      await db.collection("users").doc(managedDriverUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(otherDriverUid).set({
        type: "driver",
        managerUid: "someoneElse",
      });

      await db.collection("attendance_daily").doc(managedDocId).set(
        {
          ...clockInPayload(managedDriverUid, managedEpochDay),
          clockOutMillis: 1700000300000,
        }
      );
      await db.collection("attendance_daily").doc(otherDocId).set(
        {
          ...clockInPayload(otherDriverUid, otherEpochDay),
          clockOutMillis: 1700000300000,
        }
      );
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  test("unauthenticated cannot read attendance_daily", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(db.collection("attendance_daily").doc(managedDocId).get());
  });

  test("driver can read own attendance_daily and cannot read others", async () => {
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();
    await assertSucceeds(driverDb.collection("attendance_daily").doc(managedDocId).get());
    await assertFails(driverDb.collection("attendance_daily").doc(otherDocId).get());
  });

  test("driver can read own missing canonical doc", async () => {
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();
    const missingDocId = `${managedDriverUid}_${managedEpochDay + 50}`;
    await assertSucceeds(driverDb.collection("attendance_daily").doc(missingDocId).get());
  });

  test("admin can read all and expat can read managed driver only", async () => {
    const adminDb = testEnv.authenticatedContext(adminUid).firestore();
    const expatDb = testEnv.authenticatedContext(expatUid).firestore();

    await assertSucceeds(adminDb.collection("attendance_daily").doc(managedDocId).get());
    await assertSucceeds(adminDb.collection("attendance_daily").doc(otherDocId).get());
    await assertSucceeds(expatDb.collection("attendance_daily").doc(managedDocId).get());
    await assertFails(expatDb.collection("attendance_daily").doc(otherDocId).get());
  });

  test("driver can write own canonical clock-in payload and merge clock-out payload", async () => {
    const day = 20020;
    const docId = `${managedDriverUid}_${day}`;
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();

    await assertSucceeds(
      driverDb.collection("attendance_daily").doc(docId).set(
        clockInPayload(managedDriverUid, day)
      )
    );
    await assertSucceeds(
      driverDb.collection("attendance_daily").doc(docId).set(
        clockOutPayload(managedDriverUid, day),
        {merge: true}
      )
    );
  });

  test("driver cannot write another driver's attendance_daily", async () => {
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();
    await assertFails(
      driverDb.collection("attendance_daily").doc(otherDocId).set(
        clockInPayload(otherDriverUid, otherEpochDay)
      )
    );
  });

  test("driver cannot write non-canonical doc id", async () => {
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();
    await assertFails(
      driverDb.collection("attendance_daily").doc("random_doc").set(
        clockInPayload(managedDriverUid, managedEpochDay)
      )
    );
  });

  test("driver cannot write malformed attendance_daily schema", async () => {
    const driverDb = testEnv.authenticatedContext(managedDriverUid).firestore();
    const day = 20021;
    const docId = `${managedDriverUid}_${day}`;

    await assertFails(
      driverDb.collection("attendance_daily").doc(docId).set({
        driverUid: managedDriverUid,
        epochDay: day,
        clockInMillis: 1700000000000,
      })
    );

    await assertFails(
      driverDb.collection("attendance_daily").doc(docId).set({
        ...clockInPayload(managedDriverUid, day),
        updatedAtMillis: "1700000000000",
      })
    );

    await assertFails(
      driverDb.collection("attendance_daily").doc(docId).set({
        ...clockInPayload(managedDriverUid, day),
        extraField: true,
      })
    );
  });

  test("admin and expat cannot write attendance_daily", async () => {
    const adminDb = testEnv.authenticatedContext(adminUid).firestore();
    const expatDb = testEnv.authenticatedContext(expatUid).firestore();
    const day = 20030;
    const adminDocId = `${adminUid}_${day}`;
    const managedDoc = `${managedDriverUid}_${day}`;

    await assertFails(
      adminDb.collection("attendance_daily").doc(adminDocId).set(
        clockInPayload(adminUid, day)
      )
    );
    await assertFails(
      expatDb.collection("attendance_daily").doc(managedDoc).set(
        clockInPayload(managedDriverUid, day)
      )
    );
  });
});
