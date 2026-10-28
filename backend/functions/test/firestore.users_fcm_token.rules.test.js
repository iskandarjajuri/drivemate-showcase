const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

// Satu-satunya suite rules di repo ini yang TIDAK menyetel timeout, sehingga
// beforeAll-nya kena default Jest 5000ms dan gagal ("Exceeded timeout of 5000 ms
// for a hook") begitu emulator start-nya sedikit lebih lambat. Gejalanya
// menyesatkan: KEDELAPAN test gagal sekaligus, kelihatan seperti rules pecah
// padahal cuma setup yang belum kelar. Suite lain memakai 30000-60000.
jest.setTimeout(60000);

// Bentuk token FCM nyata (id:APA91b…): dulu satu-satunya bentuk yang lolos jalur legacy.
const realToken = (seed) => `${seed.padEnd(22, "e")}:APA91b${"Z".repeat(100)}`;

// Tulis-sendiri legacy `users/{uid}.fcmToken` sudah DICABUT dari rules (temuan keamanan #1).
// Token hanya boleh didaftarkan lewat `fcm_tokens/{sha256Hex(token)}`. Suite ini mengunci bahwa
// TIDAK ADA akun — driver, expat, admin, pemilik dokumen, atau orang lain — yang bisa menulis
// atau membuat field itu, termasuk simulasi build lama yang menulis token sah maupun "".
describe("Firestore rules: users/{uid}.fcmToken DITOLAK untuk semua (jalur legacy dicabut)", () => {
  let testEnv;

  const driverUid = "driver-uid-fcm-1";
  const otherDriverUid = "driver-uid-fcm-2";
  const expatUid = "expat-uid-fcm-1";
  const adminUid = "admin-uid-fcm-1";
  const strangerUid = "stranger-uid-fcm-1"; // terautentikasi, tanpa dokumen users

  const usersAs = (uid) => testEnv.authenticatedContext(uid).firestore().collection("users");

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-fcm-token",
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(driverUid).set({type: "driver"});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(adminUid).set({type: "admin"});
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  describe("pemilik menulis fcmToken pada dokumennya sendiri (simulasi build lama)", () => {
    test.each([
      ["driver", driverUid],
      ["expat", expatUid],
      ["admin", adminUid],
    ])("%s: token sah ditolak", async (_role, uid) => {
      await assertFails(usersAs(uid).doc(uid).update({fcmToken: realToken(uid)}));
    });

    test.each([
      ["driver", driverUid],
      ["expat", expatUid],
      ["admin", adminUid],
    ])("%s: string kosong (logout build lama) ditolak", async (_role, uid) => {
      await assertFails(usersAs(uid).doc(uid).update({fcmToken: ""}));
    });

    test("pemilik: set merge berisi fcmToken ditolak", async () => {
      await assertFails(
        usersAs(driverUid).doc(driverUid).set({fcmToken: realToken("merge")}, {merge: true})
      );
    });

    test("pemilik: fcmToken bukan string tetap ditolak", async () => {
      await assertFails(usersAs(driverUid).doc(driverUid).update({fcmToken: 12345}));
    });

    test("pemilik: fcmToken dibundel dengan perubahan SOP sah tetap ditolak", async () => {
      await assertFails(
        usersAs(otherDriverUid).doc(otherDriverUid).update({
          fcmToken: realToken("bundle"),
          sopAccepted: true,
          sopAcceptedAtMillis: 1700000000000,
        })
      );
    });
  });

  describe("menulis fcmToken pada dokumen orang lain", () => {
    test.each([
      ["driver lain", otherDriverUid],
      ["expat", expatUid],
      ["admin", adminUid],
      ["orang lain tanpa profil", strangerUid],
    ])("%s: token sah maupun string kosong ditolak", async (_who, uid) => {
      const ref = usersAs(uid).doc(driverUid);
      await assertFails(ref.update({fcmToken: realToken("hijack")}));
      await assertFails(ref.update({fcmToken: ""}));
    });

    test("admin: fcmToken dibundel dengan perubahan nama yang sah tetap ditolak", async () => {
      await assertFails(
        usersAs(adminUid).doc(driverUid).update({name: "Nama Baru", fcmToken: realToken("adm")})
      );
    });
  });

  describe("membuat dokumen users berisi fcmToken", () => {
    test("admin: create dokumen user dengan fcmToken (token sah maupun kosong) ditolak", async () => {
      await assertFails(
        usersAs(adminUid).doc("user-uid-fcm-new-1").set({type: "driver", fcmToken: realToken("new")})
      );
      await assertFails(
        usersAs(adminUid).doc("user-uid-fcm-new-2").set({type: "driver", fcmToken: ""})
      );
    });

    test.each([
      ["driver", driverUid],
      ["expat", expatUid],
      ["orang lain tanpa profil", strangerUid],
    ])("%s: create dokumen users berisi fcmToken ditolak", async (_who, uid) => {
      const target = `user-uid-fcm-new-${uid}`;
      await assertFails(usersAs(uid).doc(target).set({type: "driver", fcmToken: realToken("x")}));
      await assertFails(usersAs(uid).doc(target).set({type: "driver", fcmToken: ""}));
    });

    test("orang lain tanpa profil: membuat dokumennya sendiri berisi fcmToken ditolak", async () => {
      await assertFails(
        usersAs(strangerUid).doc(strangerUid).set({type: "driver", fcmToken: realToken("self")})
      );
    });
  });

  describe("update sah lain tidak terdampak", () => {
    test("driver's existing SOP-acknowledgement update path is unaffected", async () => {
      await assertSucceeds(
        usersAs(driverUid).doc(driverUid).update({sopAccepted: true, sopAcceptedAtMillis: 1700000000000})
      );
    });

    test("admin masih bisa mengubah nama pengguna", async () => {
      await assertSucceeds(usersAs(adminUid).doc(expatUid).update({name: "Expat Diperbarui"}));
    });
  });
});
