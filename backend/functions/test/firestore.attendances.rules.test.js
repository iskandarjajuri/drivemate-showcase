const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-attendances";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: attendances updates", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const managedDriverUid = "driver-uid-1";
  const otherDriverUid = "driver-uid-2";

  const managedAttendanceId = "attendance-managed";
  const otherAttendanceId = "attendance-other";

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

      const baseAttendance = (driverUid) => ({
        driverUid,
        timestampMillis: 1700000000000,
        type: "IN",
        note: "IN",
      });

      await db.collection("attendances").doc(managedAttendanceId).set(
        baseAttendance(managedDriverUid)
      );
      await db.collection("attendances").doc(otherAttendanceId).set(
        baseAttendance(otherDriverUid)
      );
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("expat can update managed attendance", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("attendances").doc(managedAttendanceId).update({
        note: "edited",
      })
    );
  });

  test("driver can create own attendance with current app payload", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("attendances").doc("attendance-driver-create").set({
        driverUid: managedDriverUid,
        timestampMillis: 1700000003000,
        type: "OUT",
        note: "OUT",
        latitude: -6.2,
        longitude: 106.8,
        locationName: "Plant A",
      })
    );
  });

  test("driver cannot create attendance with extra fields", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc("attendance-extra-field").set({
        driverUid: managedDriverUid,
        timestampMillis: 1700000004000,
        type: "IN",
        note: "IN",
        unexpected: true,
      })
    );
  });

  test("driver cannot create attendance for another driver", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc("attendance-wrong-driver").set({
        driverUid: otherDriverUid,
        timestampMillis: 1700000005000,
        type: "IN",
        note: "IN",
      })
    );
  });

  test("expat cannot mutate immutable attendance timestamp", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc(managedAttendanceId).update({
        timestampMillis: 1700000009999,
      })
    );
  });

  test("admin cannot mutate immutable attendance type", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc(managedAttendanceId).update({
        type: "OUT",
      })
    );
  });

  test("expat cannot update other attendance", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc(otherAttendanceId).update({
        note: "edited",
      })
    );
  });

  test("expat cannot delete other attendance", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("attendances").doc(otherAttendanceId).delete()
    );
  });

  test("admin can update and delete any attendance", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("attendances").doc(managedAttendanceId).update({
        note: "admin-edit",
      })
    );
    await assertSucceeds(
      db.collection("attendances").doc(otherAttendanceId).update({
        note: "admin-edit",
      })
    );
    await assertSucceeds(
      db.collection("attendances").doc(otherAttendanceId).delete()
    );
  });

  test("driver can read own attendance, cannot read others", async () => {
    const driverCtx = testEnv.authenticatedContext(managedDriverUid);
    const driverDb = driverCtx.firestore();
    await assertSucceeds(
      driverDb.collection("attendances").doc(managedAttendanceId).get()
    );
    await assertFails(
      driverDb.collection("attendances").doc(otherAttendanceId).get()
    );
  });
});
