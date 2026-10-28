const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

jest.setTimeout(60000);

describe("Firestore rules: announcements", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const driverUid = "driver-uid-1";
  const expatUid = "expat-uid-1";

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
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("announcements").doc("seed").set(
        validAnnouncementPayload(adminUid)
      );
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  test("admin can create a valid announcement", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertSucceeds(
      db.collection("announcements").doc("admin-post").set(
        validAnnouncementPayload(adminUid, {
          title: "Jadwal inspeksi",
        })
      )
    );
  });

  test("driver cannot create an announcement", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();

    await assertFails(
      db.collection("announcements").doc("driver-post").set(
        validAnnouncementPayload(driverUid)
      )
    );
  });

  test("admin cannot create announcement for another author uid", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertFails(
      db.collection("announcements").doc("spoofed-post").set(
        validAnnouncementPayload(driverUid)
      )
    );
  });

  test("driver and expat can read announcements", async () => {
    const driverDb = testEnv.authenticatedContext(driverUid).firestore();
    const expatDb = testEnv.authenticatedContext(expatUid).firestore();

    await assertSucceeds(driverDb.collection("announcements").doc("seed").get());
    await assertSucceeds(driverDb.collection("announcements").limit(5).get());
    await assertSucceeds(expatDb.collection("announcements").doc("seed").get());
    await assertSucceeds(expatDb.collection("announcements").limit(5).get());
  });

  test("unauthenticated user cannot read announcements", async () => {
    const db = testEnv.unauthenticatedContext().firestore();

    await assertFails(db.collection("announcements").doc("seed").get());
    await assertFails(db.collection("announcements").limit(5).get());
  });

  test("admin cannot create announcement with extra fields", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertFails(
      db.collection("announcements").doc("extra-post").set({
        ...validAnnouncementPayload(adminUid),
        audience: "all",
      })
    );
  });

  test("admin can update title and body of an existing announcement", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertSucceeds(
      db.collection("announcements").doc("seed").update({
        title: "Judul diperbarui",
        body: "Isi pesan yang sudah diperbarui oleh admin.",
        createdByUid: adminUid,
        createdAtMillis: 1_762_000_000_000,
      })
    );
  });

  test("admin cannot update announcement to change author uid", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertFails(
      db.collection("announcements").doc("seed").update({
        title: "Judul diperbarui",
        body: "Isi pesan.",
        createdByUid: driverUid,
        createdAtMillis: 1_762_000_000_000,
      })
    );
  });

  test("driver cannot update an announcement", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();

    await assertFails(
      db.collection("announcements").doc("seed").update({
        title: "Diperbarui oleh driver",
        body: "Isi.",
        createdByUid: adminUid,
        createdAtMillis: 1_762_000_000_000,
      })
    );
  });

  test("admin can delete an announcement", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection("announcements").doc("to-delete").set(
        validAnnouncementPayload(adminUid)
      );
    });

    await assertSucceeds(
      db.collection("announcements").doc("to-delete").delete()
    );
  });

  test("driver cannot delete an announcement", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();

    await assertFails(
      db.collection("announcements").doc("seed").delete()
    );
  });
});
