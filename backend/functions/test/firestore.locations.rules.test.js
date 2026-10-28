const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-locations";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: locations access", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const managedDriverUid = "driver-uid-1";
  const otherDriverUid = "driver-uid-2";
  const freshDriverUid = "driver-uid-3";
  const invalidCreateDriverUid = "driver-uid-4";

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
      await db.collection("users").doc(freshDriverUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(invalidCreateDriverUid).set({
        type: "driver",
        managerUid: expatUid,
      });

      const baseLocation = (driverUid, updatedAtMillis) => ({
        driverUid,
        latitude: -6.2,
        longitude: 106.8,
        updatedAtMillis,
      });

      await db.collection("locations").doc(managedDriverUid).set(
        baseLocation(managedDriverUid, 1700000001000)
      );
      await db.collection("locations").doc(otherDriverUid).set(
        baseLocation(otherDriverUid, 1700000002000)
      );
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("admin can query locations ordered by updatedAtMillis", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const query = db.collection("locations").orderBy("updatedAtMillis", "desc");
    const snap = await assertSucceeds(query.get());
    const ids = snap.docs.map((doc) => doc.id);
    expect(ids).toEqual([otherDriverUid, managedDriverUid]);
  });

  test("driver can get own location, cannot list all locations", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("locations").doc(managedDriverUid).get()
    );
    await assertFails(
      db.collection("locations").orderBy("updatedAtMillis", "desc").get()
    );
  });

  test("expat can get managed driver location only", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("locations").doc(managedDriverUid).get()
    );
    await assertFails(
      db.collection("locations").doc(otherDriverUid).get()
    );
  });

  test("driver cannot write another driver's location", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(otherDriverUid).set({
        driverUid: otherDriverUid,
        latitude: -6.1,
        longitude: 106.7,
        updatedAtMillis: 1700000003000,
      })
    );
  });

  test("driver can create own location with current app payload", async () => {
    const ctx = testEnv.authenticatedContext(freshDriverUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("locations").doc(freshDriverUid).set({
        driverUid: freshDriverUid,
        latitude: -6.15,
        longitude: 106.82,
        updatedAtMillis: 1700000003000,
        locationName: "Plant A",
      })
    );
  });

  test("driver can update own location mutable fields only", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("locations").doc(managedDriverUid).update({
        latitude: -6.25,
        longitude: 106.75,
        updatedAtMillis: 1700000004000,
        locationName: "Jakarta Yard",
      })
    );
  });

  test("driver cannot add extra fields to location docs", async () => {
    const ctx = testEnv.authenticatedContext(invalidCreateDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(invalidCreateDriverUid).set({
        driverUid: invalidCreateDriverUid,
        latitude: -6.18,
        longitude: 106.83,
        updatedAtMillis: 1700000005000,
        unexpected: true,
      })
    );
  });

  test("driver cannot write invalid location field types", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(managedDriverUid).update({
        locationName: 123,
      })
    );
  });

  test("driver cannot mutate driverUid on own location", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(managedDriverUid).update({
        driverUid: "hijack-uid",
      })
    );
  });

  test("driver can delete own location snapshot", async () => {
    const seedCtx = testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("locations").doc(freshDriverUid).set({
        driverUid: freshDriverUid,
        latitude: -6.17,
        longitude: 106.84,
        updatedAtMillis: 1700000006000,
      });
    });
    await seedCtx;
    const ctx = testEnv.authenticatedContext(freshDriverUid);
    const db = ctx.firestore();
    await assertSucceeds(
      db.collection("locations").doc(freshDriverUid).delete()
    );
  });

  test("driver cannot delete another driver's location snapshot", async () => {
    const ctx = testEnv.authenticatedContext(managedDriverUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(otherDriverUid).delete()
    );
  });

  test("admin cannot write location docs", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    await assertFails(
      db.collection("locations").doc(managedDriverUid).update({
        latitude: -6.25,
      })
    );
  });
});
