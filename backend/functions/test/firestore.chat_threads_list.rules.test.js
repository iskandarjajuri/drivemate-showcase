const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-chat-threads-list";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

jest.setTimeout(60000);

/**
 * Query yang PERSIS dikirim klien (`FirebaseChatThreadRepository.observeThreads`):
 * `chat_threads where participantUids array-contains <uid>`. Tes ini ada karena
 * `firestore.chat.rules.test.js` tidak pernah menguji `list` koleksi `chat_threads`; akibatnya
 * rules yang menolak query ini lolos CI dan baru ketahuan di perangkat (PERMISSION_DENIED).
 */
describe("Firestore rules: chat_threads list (query inbox)", () => {
  let testEnv;

  const adminUid = "admin-list-uid";
  const driverUid = "driver-list-uid";
  const expatUid = "expat-list-uid";
  const otherDriverUid = "driver-list-other";

  const roles = {
    admin: {uid: adminUid, type: "admin"},
    driver: {uid: driverUid, type: "driver"},
    expat: {uid: expatUid, type: "expat"},
  };

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });
  });

  afterAll(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      for (const {uid, type} of [...Object.values(roles), {uid: otherDriverUid, type: "driver"}]) {
        await db.collection("users").doc(uid).set({type});
      }
      const thread = (a, b) => ({
        participantUids: [a, b],
        lastMessageAtMillis: 1_775_000_000_000,
      });
      await db.collection("chat_threads").doc(`${adminUid}__${driverUid}`).set(thread(adminUid, driverUid));
      await db.collection("chat_threads").doc(`${adminUid}__${expatUid}`).set(thread(adminUid, expatUid));
      await db.collection("chat_threads").doc(`${driverUid}__${expatUid}`).set(thread(driverUid, expatUid));
      await db.collection("chat_threads").doc(`${otherDriverUid}__${adminUid}`).set(thread(otherDriverUid, adminUid));
    });
  });

  for (const [name, {uid}] of Object.entries(roles)) {
    test(`${name} bisa list thread miliknya dengan array-contains uid sendiri`, async () => {
      const db = testEnv.authenticatedContext(uid).firestore();
      await assertSucceeds(
        db.collection("chat_threads").where("participantUids", "array-contains", uid).get()
      );
    });
  }

  test("driver hanya menerima thread yang ia ikuti", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    const snap = await assertSucceeds(
      db.collection("chat_threads").where("participantUids", "array-contains", driverUid).get()
    );
    expect(snap.docs.map((d) => d.id).sort()).toEqual(
      [`${adminUid}__${driverUid}`, `${driverUid}__${expatUid}`].sort()
    );
  });

  test("driver tidak bisa list thread dengan array-contains uid orang lain", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("chat_threads").where("participantUids", "array-contains", adminUid).get()
    );
  });

  test("list tanpa constraint peserta ditolak (tidak boleh membaca semua thread)", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(db.collection("chat_threads").get());
  });

  test("pengguna anonim tidak bisa list thread", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(
      db.collection("chat_threads").where("participantUids", "array-contains", driverUid).get()
    );
  });
});
