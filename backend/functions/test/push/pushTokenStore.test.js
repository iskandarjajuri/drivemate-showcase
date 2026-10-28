/**
 * Binding Firestore untuk token FCM privat (hanya `fcm_tokens`; `users.fcmToken` diabaikan), diuji di emulator.
 */
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST is not set. Run this test through `npm run test:rules`, " +
    "which starts its own emulator; it must never fall back to someone else's one.");
}
const {initializeApp, getApps} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {createPushTokenStore, tokenDocId} = require("../../lib/push/pushTokenStore");

const app = getApps().length ? getApps()[0] : initializeApp({projectId: "demo-drivemate"});
const db = getFirestore(app, "drivemate-database");
jest.setTimeout(30000);
const store = createPushTokenStore(db);
const DAY = 86400000;
const tok = (c) => `${c}`.repeat(60);

async function seed(uid, token, ageMs = 0, extra = {}) {
  await db.collection("fcm_tokens").doc(tokenDocId(token)).set({
    uid, token, platform: "android", source: "client", updatedAt: Timestamp.fromMillis(Date.now() - ageMs), ...extra,
  });
}

beforeEach(async () => {
  const snap = await db.collection("fcm_tokens").where("uid", "in", ["ps-u1", "ps-u2"]).get();
  await Promise.all(snap.docs.map((d) => d.ref.delete()));
  for (const u of ["ps-u1", "ps-u2"]) await db.collection("users").doc(u).delete().catch(() => undefined);
});

describe("tokenDocId", () => {
  test("sha256 hex huruf kecil, 64 karakter, vektor tetap", () => {
    expect(tokenDocId("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });
});

describe("createPushTokenStore.listTokens", () => {
  test("hanya milik uid, terbaru dulu; semua bersumber dokumen fcm_tokens", async () => {
    await seed("ps-u1", tok("a"), 3000);
    await seed("ps-u1", tok("b"), 1000);
    await seed("ps-u2", tok("c"), 500);
    const list = await store.listTokens("ps-u1");
    expect(list.map((r) => r.token)).toEqual([tok("b"), tok("a")]);
    expect(list.map((r) => r.source)).toEqual(["doc", "doc"]);
    expect(list.map((r) => r.id)).toEqual([tokenDocId(tok("b")), tokenDocId(tok("a"))]);
  });

  test("users.fcmToken yang disemai TIDAK ikut daftar token", async () => {
    await seed("ps-u1", tok("a"), 1000);
    await db.collection("users").doc("ps-u1").set({type: "driver", fcmToken: tok("l")});
    const list = await store.listTokens("ps-u1");
    expect(list.map((r) => r.token)).toEqual([tok("a")]);

    await db.collection("users").doc("ps-u2").set({type: "driver", fcmToken: tok("m")});
    expect(await store.listTokens("ps-u2")).toEqual([]); // hanya field users, tanpa dokumen -> tidak ada token
  });

  test("dokumen lebih dari 60 hari dibersihkan dan tidak dikembalikan", async () => {
    await seed("ps-u1", tok("a"), 61 * DAY);
    await seed("ps-u1", tok("b"), 1 * DAY);
    const list = await store.listTokens("ps-u1");
    expect(list.map((r) => r.token)).toEqual([tok("b")]);
    expect((await db.collection("fcm_tokens").doc(tokenDocId(tok("a"))).get()).exists).toBe(false);
  });
});

describe("createPushTokenStore.removeToken", () => {
  test("hanya dokumen fcm_tokens yang dihapus; dokumen users tidak disentuh", async () => {
    await seed("ps-u1", tok("a"));
    await seed("ps-u1", tok("b"), 1000);
    await db.collection("users").doc("ps-u1").set({type: "driver", name: "N", fcmToken: tok("l")});
    const list = await store.listTokens("ps-u1");
    expect(list).toHaveLength(2);
    await store.removeToken(list.find((r) => r.token === tok("a")));
    expect((await db.collection("fcm_tokens").doc(tokenDocId(tok("a"))).get()).exists).toBe(false);
    expect((await db.collection("fcm_tokens").doc(tokenDocId(tok("b"))).get()).exists).toBe(true);
    const u = (await db.collection("users").doc("ps-u1").get()).data();
    expect(u.fcmToken).toBe(tok("l"));
    expect(u.name).toBe("N");
  });
});
