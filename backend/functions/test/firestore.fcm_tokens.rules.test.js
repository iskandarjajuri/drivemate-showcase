/**
 * Token FCM privat: koleksi `fcm_tokens/{sha256Hex(token)}`. Tidak ada klien yang boleh MEMBACA/list (tak ada yang bisa
 * mengetahui token orang lain); tulis hanya oleh pemilik (uid == auth.uid) dengan ID terikat ke hash token (tak bisa
 * membajak ID milik orang lain), token tak berubah saat update, bentuk valid, pengguna harus punya dokumen `users`.
 */
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {assertFails, assertSucceeds, initializeTestEnvironment} = require("@firebase/rules-unit-testing");
const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");
jest.setTimeout(60000);

const sha = (t) => crypto.createHash("sha256").update(t).digest("hex");
const TOKEN_A = "fAkEtOkEn_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:APA91bAAAA-_xyz";
const TOKEN_B = "fAkEtOkEn_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB:APA91bBBBB-_xyz";

describe("Firestore rules: fcm_tokens", () => {
  let testEnv;
  const driverUid = "drv-fcm-1";
  const otherDriverUid = "drv-fcm-2";
  const expatUid = "exp-fcm-1";
  const adminUid = "adm-fcm-1";
  const noProfileUid = "ghost-fcm-1"; // terautentikasi tetapi TANPA dokumen users
  const ts = () => firebase.firestore.FieldValue.serverTimestamp();
  const doc = (uid, token, over = {}) => ({uid, token, platform: "android", appVersion: "1.0", source: "client", updatedAt: ts(), ...over});
  const col = (u) => testEnv.authenticatedContext(u).firestore().collection("fcm_tokens");

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-fcm-tokens",
      firestore: {rules: fs.readFileSync(RULES_PATH, "utf8")},
    });
  });
  afterAll(async () => testEnv.cleanup());
  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.collection("users").doc(driverUid).set({type: "driver", managerUid: expatUid});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("fcm_tokens").doc(sha(TOKEN_A)).set({uid: driverUid, token: TOKEN_A, platform: "android", source: "client", updatedAt: new Date()});
    });
  });

  describe("baca/list: tertutup untuk SEMUA klien", () => {
    test.each([["pemilik", driverUid], ["driver lain", otherDriverUid], ["expat pengelola", expatUid], ["admin", adminUid]])(
      "%s tidak bisa get maupun list", async (_n, u) => {
        await assertFails(col(u).doc(sha(TOKEN_A)).get());
        await assertFails(col(u).get());
        await assertFails(col(u).where("uid", "==", driverUid).get());
      });
    test("tidak terautentikasi pun tidak", async () => {
      await assertFails(testEnv.unauthenticatedContext().firestore().collection("fcm_tokens").doc(sha(TOKEN_A)).get());
    });
  });

  describe("create/update oleh pemilik", () => {
    test("pemilik membuat dokumen valid (ID = sha256(token))", async () => {
      await assertSucceeds(col(otherDriverUid).doc(sha(TOKEN_B)).set(doc(otherDriverUid, TOKEN_B)));
    });
    test("ID tak cocok hash token -> ditolak (pembajakan ID milik orang lain)", async () => {
      // penyerang memakai ID hash milik korban dengan token miliknya sendiri
      await assertFails(col(otherDriverUid).doc(sha(TOKEN_A)).set(doc(otherDriverUid, TOKEN_B)));
      await assertFails(col(otherDriverUid).doc("a".repeat(64)).set(doc(otherDriverUid, TOKEN_B)));
    });
    test("uid orang lain -> ditolak", async () => {
      await assertFails(col(otherDriverUid).doc(sha(TOKEN_B)).set(doc(driverUid, TOKEN_B)));
    });
    test("pengguna tanpa dokumen users -> ditolak (batas spam akun kosong)", async () => {
      await assertFails(col(noProfileUid).doc(sha(TOKEN_B)).set(doc(noProfileUid, TOKEN_B)));
    });
    test("field ekstra, platform tak sah, token terlalu pendek / karakter tak sah, updatedAt bukan request.time -> ditolak", async () => {
      const id = sha(TOKEN_B);
      await assertFails(col(otherDriverUid).doc(id).set(doc(otherDriverUid, TOKEN_B, {extra: 1})));
      await assertFails(col(otherDriverUid).doc(id).set(doc(otherDriverUid, TOKEN_B, {platform: "toaster"})));
      const short = "x".repeat(20);
      await assertFails(col(otherDriverUid).doc(sha(short)).set(doc(otherDriverUid, short)));
      const bad = "bad token with spaces " + "y".repeat(60);
      await assertFails(col(otherDriverUid).doc(sha(bad)).set(doc(otherDriverUid, bad)));
      await assertFails(col(otherDriverUid).doc(id).set(doc(otherDriverUid, TOKEN_B, {updatedAt: new Date("2020-01-01")})));
      await assertFails(col(otherDriverUid).doc(id).set(doc(otherDriverUid, TOKEN_B, {appVersion: "v".repeat(64)})));
    });
    test("update oleh pemilik yang sama dengan token sama -> diterima; token diubah -> ditolak", async () => {
      await assertSucceeds(col(driverUid).doc(sha(TOKEN_A)).set(doc(driverUid, TOKEN_A, {appVersion: "1.1"})));
      await assertFails(col(driverUid).doc(sha(TOKEN_A)).set(doc(driverUid, TOKEN_B)));
    });
    test("pengguna lain yang MENGETAHUI token tidak bisa mengambil alih dokumen yang sudah ada (temuan #1)", async () => {
      // token legacy bisa terbaca lewat `users.fcmToken` sesaat; mengetahui token BUKAN bukti memegang perangkat
      await assertFails(col(otherDriverUid).doc(sha(TOKEN_A)).set(doc(otherDriverUid, TOKEN_A)));
      await assertFails(col(expatUid).doc(sha(TOKEN_A)).set(doc(expatUid, TOKEN_A)));
      await assertFails(col(adminUid).doc(sha(TOKEN_A)).set(doc(adminUid, TOKEN_A)));
    });
    test("update tidak boleh memindahkan kepemilikan walau uid diganti ke pemanggil", async () => {
      await assertFails(col(otherDriverUid).doc(sha(TOKEN_A)).update({uid: otherDriverUid, updatedAt: ts()}));
    });
  });

  describe("delete", () => {
    test("pemilik menghapus dokumennya; orang lain tidak bisa", async () => {
      await assertFails(col(otherDriverUid).doc(sha(TOKEN_A)).delete());
      await assertFails(col(adminUid).doc(sha(TOKEN_A)).delete());
      await assertSucceeds(col(driverUid).doc(sha(TOKEN_A)).delete());
    });
  });

  // Jalur legacy tulis-sendiri `users.fcmToken` sudah DICABUT; cakupan lengkapnya di
  // firestore.users_fcm_token.rules.test.js. Di sini cukup memastikan tak ada sisa jalur.
  describe("users.fcmToken (dicabut)", () => {
    const REAL_SHAPE = "e".repeat(40) + ":APA91b" + "Z".repeat(100) + "-_";
    test("bentuk apa pun ditolak, termasuk token sah dan string kosong", async () => {
      const ref = testEnv.authenticatedContext(driverUid).firestore().collection("users").doc(driverUid);
      await assertFails(ref.update({fcmToken: "x".repeat(5000)}));
      await assertFails(ref.update({fcmToken: "<script>alert(1)</script>" + "y".repeat(60)}));
      await assertFails(ref.update({fcmToken: "pendek"}));
      await assertFails(ref.update({fcmToken: REAL_SHAPE}));
      await assertFails(ref.update({fcmToken: ""}));
    });
    test("admin TIDAK bisa menulis fcmToken pada pengguna lain", async () => {
      const ref = testEnv.authenticatedContext(adminUid).firestore().collection("users").doc(driverUid);
      await assertFails(ref.update({fcmToken: "titipan-admin-tidak-sah"}));
    });
  });
});
