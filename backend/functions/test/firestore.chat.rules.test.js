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

describe("Firestore rules: chat", () => {
  let testEnv;

  const adminUid = "admin-chat-uid";
  const driverUid = "driver-chat-uid";
  const expatUid = "expat-chat-uid";
  const otherDriverUid = "driver-chat-other";
  const otherExpatUid = "expat-chat-other";
  const adminDriverThreadId = "admin-chat-uid__driver-chat-uid";
  const adminExpatThreadId = "admin-chat-uid__expat-chat-uid";
  const driverDriverThreadId = "driver-chat-other__driver-chat-uid";
  const expatExpatThreadId = "expat-chat-other__expat-chat-uid";

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

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(driverUid).set({type: "driver"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(otherExpatUid).set({type: "expat"});
      await db
        .collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("seed-admin-driver")
        .set(
          validMessagePayload({
            threadId: adminDriverThreadId,
            participants: [adminUid, driverUid],
            senderUid: adminUid,
            body: "Briefing pukul 07.00",
          })
        );
      await db
        .collection("chat_threads")
        .doc(adminExpatThreadId)
        .collection("messages")
        .doc("seed-admin-expat")
        .set(
          validMessagePayload({
            threadId: adminExpatThreadId,
            participants: [adminUid, expatUid],
            senderUid: expatUid,
            body: "Siap untuk review hari ini.",
          })
        );
      await db
        .collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("msg-delete-me")
        .set(
          validMessagePayload({
            threadId: adminDriverThreadId,
            participants: [adminUid, driverUid],
            senderUid: adminUid,
            body: "Pesan untuk dihapus",
          })
        );
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  test("driver can list admin users for chat contacts", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("users").where("type", "==", "admin").get()
    );
  });

  test("driver cannot list driver users for chat contacts", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("users").where("type", "==", "driver").get()
    );
  });

  test("admin can send message to driver", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("admin-send")
        .set(
          validMessagePayload({
            threadId: adminDriverThreadId,
            participants: [adminUid, driverUid],
            senderUid: adminUid,
          })
        )
    );
  });

  test("driver can reply to admin", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("driver-reply")
        .set(
          validMessagePayload({
            threadId: adminDriverThreadId,
            participants: [adminUid, driverUid],
            senderUid: driverUid,
          })
        )
    );
  });

  test("expat can read admin conversation", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(adminExpatThreadId)
        .collection("messages")
        .orderBy("createdAtMillis")
        .get()
    );
  });

  test("unrelated driver cannot read another thread", async () => {
    const db = testEnv.authenticatedContext(otherDriverUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .orderBy("createdAtMillis")
        .get()
    );
  });

  test("sender cannot spoof another uid", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("spoofed")
        .set(
          validMessagePayload({
            threadId: adminDriverThreadId,
            participants: [adminUid, driverUid],
            senderUid: adminUid,
          })
        )
    );
  });

  test("driver can create driver to expat chat", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc("driver-chat-uid__expat-chat-uid")
        .collection("messages")
        .doc("driver-expat-pair")
        .set(
          validMessagePayload({
            threadId: "driver-chat-uid__expat-chat-uid",
            participants: [driverUid, expatUid],
            senderUid: driverUid,
          })
        )
    );
  });

  // ===== Kebijakan baru: chat bebas antar peran, hapus pesan, status terbaca, direktori =====

  test("driver can message another driver", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(driverDriverThreadId)
        .collection("messages")
        .doc("driver-driver-msg")
        .set(
          validMessagePayload({
            threadId: driverDriverThreadId,
            participants: [driverUid, otherDriverUid],
            senderUid: driverUid,
          })
        )
    );
  });

  test("expat can message another expat", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(expatExpatThreadId)
        .collection("messages")
        .doc("expat-expat-msg")
        .set(
          validMessagePayload({
            threadId: expatExpatThreadId,
            participants: [expatUid, otherExpatUid],
            senderUid: expatUid,
          })
        )
    );
  });

  test("pair with unknown role is rejected", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc("driver-chat-uid__ghost-uid")
        .collection("messages")
        .doc("unknown-pair")
        .set(
          validMessagePayload({
            threadId: "driver-chat-uid__ghost-uid",
            participants: [driverUid, "ghost-uid"],
            senderUid: driverUid,
          })
        )
    );
  });

  test("non-sender cannot delete-for-everyone a message", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("seed-admin-driver")
        .update({deleted: true, deletedAtMillis: 1_775_000_000_900})
    );
  });

  test("sender cannot edit body via update", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("seed-admin-driver")
        .update({body: "diedit"})
    );
  });

  test("sender can mark own message as deleted", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("msg-delete-me")
        .update({deleted: true, deletedAtMillis: 1_775_000_000_900})
    );
  });

  test("sender cannot un-delete a message", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("msg-delete-me")
        .update({deleted: false})
    );
  });

  test("client cannot delete a message document", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("seed-admin-driver")
        .delete()
    );
  });

  test("client cannot write the thread document", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .set({
          participantUids: [adminUid, driverUid],
          lastMessageAtMillis: 1,
          unread: {[driverUid]: 1},
        })
    );
  });

  test("participant can read the thread document", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .collection("chat_threads").doc(adminDriverThreadId)
        .set({
          participantUids: [adminUid, driverUid],
          lastMessageAtMillis: 1_775_000_000_000,
          unread: {[driverUid]: 1},
        });
    });
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads").doc(adminDriverThreadId).get()
    );
  });

  test("client cannot write another participant's read marker", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("reads")
        .doc(driverUid)
        .set({lastReadAtMillis: 1_775_000_000_000})
    );
  });

  test("participant can write own read marker", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("reads")
        .doc(driverUid)
        .set({lastReadAtMillis: 1_775_000_000_000})
    );
  });

  test("participant can read the partner's read marker", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .collection("chat_threads").doc(adminDriverThreadId)
        .collection("reads").doc(adminUid)
        .set({lastReadAtMillis: 1_775_000_000_000, hiddenMessageIds: []});
    });
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads").doc(adminDriverThreadId)
        .collection("reads").doc(adminUid).get()
    );
  });

  test("read marker with >500 hidden messages is rejected", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    const ids = Array.from({length: 501}, (_, i) => `msg-${i}`);
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("reads")
        .doc(driverUid)
        .set({lastReadAtMillis: 1, hiddenMessageIds: ids})
    );
  });

  test("read marker with extra field is rejected", async () => {
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("reads")
        .doc(driverUid)
        .set({lastReadAtMillis: 1, hiddenMessageIds: [], injected: true})
    );
  });

  test("client cannot write the user directory", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("user_directory").doc("some-uid").set({displayName: "X", role: "admin"})
    );
  });

  test("signed-in user can read and list the user directory", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("user_directory").doc(driverUid).set({
        uid: driverUid,
        displayName: "Driver QA",
        role: "driver",
        active: true,
      });
    });
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(db.collection("user_directory").doc(driverUid).get());
    await assertSucceeds(db.collection("user_directory").get());
  });

  // ===== Terjemahan otomatis: field hanya boleh ditulis Cloud Function (Admin SDK) =====

  const translationFields = {
    srcLang: "ja",
    register: "polite",
    translations: {id: {text: "Halo", uncertain: false}},
    translationStatus: "done",
    translatedAtMillis: 1_775_000_000_500,
    translationModel: "deepseek-flash",
  };

  test("klien tidak bisa membuat pesan dengan field terjemahan", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    for (const [key, value] of Object.entries(translationFields)) {
      await assertFails(
        db.collection("chat_threads")
          .doc(adminDriverThreadId)
          .collection("messages")
          .doc(`inject-${key}`)
          .set({
            ...validMessagePayload({
              threadId: adminDriverThreadId,
              participants: [adminUid, driverUid],
              senderUid: adminUid,
            }),
            [key]: value,
          })
      );
    }
  });

  test("klien tidak bisa mengubah pesan (termasuk menempel terjemahan)", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("chat_threads")
        .doc(adminDriverThreadId)
        .collection("messages")
        .doc("seed-admin-driver")
        .update(translationFields)
    );
  });

  test("pesan yang sudah diterjemahkan Admin SDK tetap terbaca peserta", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore()
        .collection("chat_threads").doc(adminDriverThreadId)
        .collection("messages").doc("seed-admin-driver")
        .update(translationFields);
    });
    const db = testEnv.authenticatedContext(driverUid).firestore();
    await assertSucceeds(
      db.collection("chat_threads").doc(adminDriverThreadId)
        .collection("messages").doc("seed-admin-driver").get()
    );
  });
});
