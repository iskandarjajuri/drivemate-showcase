const fs = require("fs");
const path = require("path");
const {deleteApp, initializeApp} = require("@firebase/app");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  addDoc,
  collection,
  connectFirestoreEmulator,
  doc,
  getFirestore,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  setDoc,
  where,
} = require("@firebase/firestore");

const PROJECT_ID = "drivemate-rules-test-named-db";
const DATABASE_ID = "drivemate-database";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

jest.setTimeout(60000);

let appCounter = 0;

function emulatorHostAndPort() {
  const value = process.env.FIRESTORE_EMULATOR_HOST;
  if (!value) {
    throw new Error("FIRESTORE_EMULATOR_HOST must be set for named Firestore rules tests.");
  }
  const [host, portRaw] = value.split(":");
  const port = Number(portRaw);
  if (!host || !Number.isInteger(port)) {
    throw new Error(`Invalid FIRESTORE_EMULATOR_HOST=${value}`);
  }
  return {host, port};
}

function createNamedDb(mockUserToken) {
  const app = initializeApp(
    {
      apiKey: "demo-api-key",
      appId: `1:demo:web:${appCounter}`,
      projectId: PROJECT_ID,
    },
    `named-db-test-${appCounter++}`
  );
  const db = getFirestore(app, DATABASE_ID);
  const {host, port} = emulatorHostAndPort();
  connectFirestoreEmulator(db, host, port, {mockUserToken});
  return {app, db};
}

describe("Firestore rules: named database active app surfaces", () => {
  let testEnv;
  const apps = [];

  const adminUid = "admin-uid-1";
  const driverUid = "driver-uid-1";
  const expatUid = "expat-uid-1";
  const otherDriverUid = "driver-uid-2";
  const adminDriverThreadId = "admin-uid-1__driver-uid-1";

  const validAnnouncementPayload = (uid, overrides = {}) => ({
    title: "Shift update",
    body: "Please check the revised pickup schedule.",
    createdAtMillis: 1_762_000_000_000,
    createdByUid: uid,
    createdByName: "Admin Operations",
    createdByEmail: "admin@example.com",
    ...overrides,
  });

  const validMessagePayload = ({threadId, participants, senderUid, body = "Halo dari chat"}) => ({
    threadId,
    participantUids: participants,
    senderUid,
    senderName: "Pengguna",
    body,
    createdAtMillis: 1_775_000_000_000,
  });

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    const seed = createNamedDb("owner");
    apps.push(seed.app);
    await testEnv.withSecurityRulesDisabled(async () => {
      const {db} = seed;
      await setDoc(doc(collection(db, "users"), adminUid), {type: "admin"});
      await setDoc(doc(collection(db, "users"), driverUid), {
        type: "driver",
        sopAccepted: false,
      });
      await setDoc(doc(collection(db, "users"), expatUid), {type: "expat"});
      await setDoc(doc(collection(db, "users"), otherDriverUid), {type: "driver"});
      await setDoc(
        doc(collection(db, "announcements"), "seed"),
        validAnnouncementPayload(adminUid)
      );
      await setDoc(doc(collection(db, "workdays"), `${driverUid}_20000`), {
        driverUid,
        driverName: "Driver One",
        dateEpochDay: 20000,
        clockInMillis: 1_700_000_000_000,
        clockOutMillis: 1_700_002_880_000,
        totalMinutes: 480,
        overtimeMinutes: 0,
        status: "pending",
        updatedAt: 1_700_002_880_000,
      });
      await setDoc(
        doc(collection(db, `chat_threads/${adminDriverThreadId}/messages`), "seed"),
        validMessagePayload({
          threadId: adminDriverThreadId,
          participants: [adminUid, driverUid],
          senderUid: adminUid,
          body: "Briefing pukul 07.00",
        })
      );
    });
  });

  afterAll(async () => {
    await Promise.all(apps.map((app) => deleteApp(app)));
    if (testEnv) await testEnv.cleanup();
  });

  test("driver can read own role document from named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertSucceeds(getDoc(doc(collection(client.db, "users"), driverUid)));
  });

  test("driver can acknowledge only their own SOP in the named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    const driverDoc = doc(collection(client.db, "users"), driverUid);
    await assertSucceeds(
      setDoc(
        driverDoc,
        {sopAccepted: true, sopAcceptedAtMillis: 1_780_000_000_000},
        {merge: true}
      )
    );
    const persisted = await getDoc(driverDoc);
    expect(persisted.data()).toMatchObject({
      sopAccepted: true,
      sopAcceptedAtMillis: 1_780_000_000_000,
    });
  });

  test("expat cannot acknowledge SOP through the named database", async () => {
    const client = createNamedDb({sub: expatUid, user_id: expatUid});
    apps.push(client.app);
    await assertFails(
      setDoc(
        doc(collection(client.db, "users"), expatUid),
        {sopAccepted: true, sopAcceptedAtMillis: 1_780_000_000_000},
        {merge: true}
      )
    );
  });

  test("driver cannot modify an already accepted SOP acknowledgement", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertFails(
      setDoc(
        doc(collection(client.db, "users"), driverUid),
        {sopAccepted: true, sopAcceptedAtMillis: 1_780_000_000_001},
        {merge: true}
      )
    );
  });

  test("driver cannot flip an already accepted SOP back to false", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertFails(
      setDoc(
        doc(collection(client.db, "users"), driverUid),
        {sopAccepted: false, sopAcceptedAtMillis: 1_780_000_000_002},
        {merge: true}
      )
    );
  });

  test("driver cannot acknowledge SOP with a non-integer timestamp", async () => {
    const client = createNamedDb({sub: otherDriverUid, user_id: otherDriverUid});
    apps.push(client.app);
    await assertFails(
      setDoc(
        doc(collection(client.db, "users"), otherDriverUid),
        {sopAccepted: true, sopAcceptedAtMillis: "1_780_000_000_000"},
        {merge: true}
      )
    );
  });

  test("driver cannot change profile data while acknowledging SOP", async () => {
    const client = createNamedDb({sub: otherDriverUid, user_id: otherDriverUid});
    apps.push(client.app);
    await assertFails(
      setDoc(
        doc(collection(client.db, "users"), otherDriverUid),
        {
          sopAccepted: true,
          sopAcceptedAtMillis: 1_780_000_000_000,
          type: "admin",
        },
        {merge: true}
      )
    );
  });

  test("driver can list announcements from named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertSucceeds(
      getDocs(query(collection(client.db, "announcements"), limit(5)))
    );
  });

  test("expat can read announcements from named database", async () => {
    const client = createNamedDb({sub: expatUid, user_id: expatUid});
    apps.push(client.app);
    await assertSucceeds(getDoc(doc(collection(client.db, "announcements"), "seed")));
  });

  test("driver can query own workdays from named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertSucceeds(
      getDocs(
        query(
          collection(client.db, "workdays"),
          where("driverUid", "==", driverUid),
          where("dateEpochDay", ">=", 19900),
          where("dateEpochDay", "<=", 20100),
          orderBy("dateEpochDay", "desc"),
          orderBy("updatedAt", "desc"),
          limit(100)
        )
      )
    );
  });

  test("admin can publish announcement to named database using addDoc", async () => {
    const client = createNamedDb({sub: adminUid, user_id: adminUid});
    apps.push(client.app);
    await assertSucceeds(
      addDoc(
        collection(client.db, "announcements"),
        validAnnouncementPayload(adminUid, {title: "Jadwal inspeksi"})
      )
    );
  });

  test("driver cannot publish announcement to named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await expect(
      addDoc(
        collection(client.db, "announcements"),
        validAnnouncementPayload(driverUid)
      )
    ).rejects.toThrow();
  });

  test("admin can send chat message to driver on named database", async () => {
    const client = createNamedDb({sub: adminUid, user_id: adminUid});
    apps.push(client.app);
    await assertSucceeds(
      addDoc(
        collection(client.db, `chat_threads/${adminDriverThreadId}/messages`),
        validMessagePayload({
          threadId: adminDriverThreadId,
          participants: [adminUid, driverUid],
          senderUid: adminUid,
          body: "Briefing tambahan",
        })
      )
    );
  });

  test("driver cannot spoof sender uid on named database chat write", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await expect(
      addDoc(
        collection(client.db, `chat_threads/${adminDriverThreadId}/messages`),
        validMessagePayload({
          threadId: adminDriverThreadId,
          participants: [adminUid, driverUid],
          senderUid: adminUid,
          body: "Spoofed sender",
        })
      )
    ).rejects.toThrow();
  });

  test("driver can observe admin thread messages from named database", async () => {
    const client = createNamedDb({sub: driverUid, user_id: driverUid});
    apps.push(client.app);
    await assertSucceeds(
      getDocs(
        query(
          collection(client.db, `chat_threads/${adminDriverThreadId}/messages`),
          orderBy("createdAtMillis", "asc")
        )
      )
    );
  });

  test("unrelated driver cannot observe another named database thread", async () => {
    const client = createNamedDb({sub: otherDriverUid, user_id: otherDriverUid});
    apps.push(client.app);
    await expect(
      getDocs(
        query(
          collection(client.db, `chat_threads/${adminDriverThreadId}/messages`),
          orderBy("createdAtMillis", "asc")
        )
      )
    ).rejects.toThrow();
  });
});
