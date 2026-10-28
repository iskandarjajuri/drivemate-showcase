/**
 * Binding Firestore untuk push chat, diuji terhadap Firestore emulator (Admin SDK): klaim idempoten
 * `pushSentAt`, pembacaan token/direktori, dan penghapusan token. FCM tidak dipanggil.
 */
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:9098";

const {initializeApp, getApps} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {createFirestorePushStore} = require("../../lib/chat/chatPushFirestore");

const app = getApps().length ? getApps()[0] : initializeApp({projectId: "demo-drivemate"});
const db = getFirestore(app, "drivemate-database");
jest.setTimeout(30000);

const T = "push-test-thread";
const store = createFirestorePushStore(db);
const msgRef = () => db.collection("chat_threads").doc(T).collection("messages").doc("m1");

beforeEach(async () => {
  await msgRef().delete().catch(() => undefined);
  await db.collection("user_directory").doc("push-u1").delete().catch(() => undefined);
});

describe("createFirestorePushStore", () => {
  test("claimPush: pertama true dan menulis pushSentAt; kedua false (idempoten)", async () => {
    await msgRef().set({body: "x", senderUid: "a"});
    expect(await store.claimPush(T, "m1", 12345)).toBe(true);
    expect((await msgRef().get()).data().pushSentAt).toBe(12345);
    expect(await store.claimPush(T, "m1", 99999)).toBe(false);
    expect((await msgRef().get()).data().pushSentAt).toBe(12345);
  });

  test("claimPush: dokumen pesan tidak ada -> false", async () => {
    expect(await store.claimPush(T, "tidak-ada", 1)).toBe(false);
  });

  test("getDirectory membaca role/displayName/lang dari user_directory", async () => {
    await db.collection("user_directory").doc("push-u1").set({role: "expat", displayName: "Expat Satu", lang: "ja", active: true});
    const dir = await store.getDirectory(["push-u1", "push-none"]);
    expect(dir["push-u1"]).toEqual({role: "expat", displayName: "Expat Satu", lang: "ja"});
    expect(dir["push-none"]).toBeUndefined();
  });
});
