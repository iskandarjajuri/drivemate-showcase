const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: daily checklist/safety schema hardening", () => {
  let testEnv;

  const driverUid = "driver-uid-1";
  const otherDriverUid = "driver-uid-2";
  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";

  const validChecklistPayload = (uid, dateEpochDay, overrides = {}) => ({
    driverUid: uid,
    driverEmail: "budi@example.com",
    dateEpochDay,
    submittedAt: 123,
    createdAt: 123,
    updatedAt: 123,
    hasIssue: false,
    ...overrides,
  });

  const validSafetyPayload = (uid, dateEpochDay, overrides = {}) => ({
    driverUid: uid,
    driverEmail: "budi@example.com",
    dateEpochDay,
    submittedAt: 123,
    fitToWork: true,
    noAlcoholOrDrugs: true,
    noSevereStress: true,
    willFollowSpeedLimit: true,
    willFollowRestBreaks: true,
    willNotUsePhoneWhileDriving: true,
    willWearSeatbelt: true,
    confirmed: true,
    createdAt: 123,
    updatedAt: 123,
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
      await db.collection("users").doc(driverUid).set({type: "driver"});
      await db.collection("users").doc(otherDriverUid).set({type: "driver"});
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
    });
  });

  afterAll(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  test("driver can CREATE vehicle_checklists with canonical docId", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260115`;

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260115, {
          driverName: "Budi",
          notes: "Semua aman",
        })
      )
    );
  });

  test("driver cannot CREATE vehicle_checklists with non-canonical docId", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();

    await assertFails(
      db.collection("vehicle_checklists").doc("randomDocId").set(
        validChecklistPayload(driverUid, 20260116)
      )
    );
  });

  test("driver cannot overwrite existing vehicle_checklists doc", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260117`;

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260117)
      )
    );

    await assertFails(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260117, {
          updatedAt: 999,
          notes: "ubah kedua",
        })
      )
    );
  });

  test("driver cannot CREATE vehicle_checklists with extra fields", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260118`;

    await assertFails(
      db.collection("vehicle_checklists").doc(docId).set({
        ...validChecklistPayload(driverUid, 20260118),
        unexpected: true,
      })
    );
  });

  test("driver can CREATE vehicle_checklists with the new turnSignalBrakeLightsOk/washerFluidOk fields", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260130`;

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260130, {
          turnSignalBrakeLightsOk: true,
          washerFluidOk: false,
        })
      )
    );
  });

  test("driver cannot CREATE vehicle_checklists with wrong field types", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260119`;

    await assertFails(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260119, {
          odometerKm: "1000",
        })
      )
    );
  });

  test("driver cannot CREATE vehicle_checklists for another driver", async () => {
    const ctx = testEnv.authenticatedContext(otherDriverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260120`;

    await assertFails(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260120)
      )
    );
  });

  test("admin can UPDATE checklist mutable fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260121`;

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260121)
      )
    );

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).update({
        notes: "admin review",
        hasIssue: true,
        updatedAt: 999,
      })
    );
  });

  test("admin cannot UPDATE immutable checklist fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260122`;

    await assertSucceeds(
      db.collection("vehicle_checklists").doc(docId).set(
        validChecklistPayload(driverUid, 20260122)
      )
    );

    await assertFails(
      db.collection("vehicle_checklists").doc(docId).update({
        createdAt: 999,
      })
    );
  });

  // ---------- Blok 4.1: tindak lanjut temuan (C3) ----------
  // Mesin statusnya ada di DUA tempat dengan sengaja: `evaluateChecklistFollowUp` di
  // commonMain (bisa diuji di mesin dev) dan `validFollowUpTransition` di rules (berlaku
  // juga untuk tulisan dari Console). Test di bawah menguji yang KEDUA.

  const seedChecklist = async (dateEpochDay, followUp = null) => {
    const docId = `${driverUid}_${dateEpochDay}`;
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const payload = validChecklistPayload(driverUid, dateEpochDay, {hasIssue: true});
      await context.firestore()
        .collection("vehicle_checklists").doc(docId)
        .set(followUp ? {...payload, ...followUp} : payload);
    });
    return docId;
  };

  const adminUpdate = (docId, patch) =>
    testEnv.authenticatedContext(adminUid).firestore()
      .collection("vehicle_checklists").doc(docId).update(patch);

  test("driver TIDAK dapat menyelundupkan followUpStatus saat CREATE", async () => {
    // Tanpa penjaga ini seorang driver bisa menutup temuannya sendiri sebelum ada yang
    // melihatnya — lubang paling serius pada perubahan rules ini.
    const ctx = testEnv.authenticatedContext(driverUid);
    await assertFails(
      ctx.firestore().collection("vehicle_checklists")
        .doc(`${driverUid}_20260401`)
        .set(validChecklistPayload(driverUid, 20260401, {
          hasIssue: true,
          followUpStatus: "closed",
          followUpNote: "sudah beres kok",
        })),
    );
  });

  test("admin pun TIDAK dapat mengisi followUp saat CREATE", async () => {
    // Penjaganya di `validChecklistCreate`, bukan di cabang peran — jadi ia berlaku
    // untuk keduanya, dan itu memang yang diinginkan: tindak lanjut lahir dari update.
    const ctx = testEnv.authenticatedContext(adminUid);
    await assertFails(
      ctx.firestore().collection("vehicle_checklists")
        .doc(`${driverUid}_20260402`)
        .set(validChecklistPayload(driverUid, 20260402, {
          followUpStatus: "reviewed",
          followUpOwnerUid: adminUid,
        })),
    );
  });

  test("expat TIDAK dapat mengubah followUp", async () => {
    // Batas SEKARANG, dipatok sebelum Blok 4.2 memindahkannya.
    const docId = await seedChecklist(20260403);
    await assertFails(
      testEnv.authenticatedContext(expatUid).firestore()
        .collection("vehicle_checklists").doc(docId)
        .update({followUpStatus: "reviewed", followUpOwnerUid: expatUid}),
    );
  });

  test("admin: new -> reviewed dengan penanggung jawab DITERIMA", async () => {
    const docId = await seedChecklist(20260404);
    await assertSucceeds(adminUpdate(docId, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
      followUpUpdatedAtMillis: 1757000000000,
      followUpUpdatedByUid: adminUid,
      updatedAt: 999,
    }));
  });

  test("admin: new -> reviewed TANPA penanggung jawab DITOLAK", async () => {
    const docId = await seedChecklist(20260405);
    await assertFails(adminUpdate(docId, {followUpStatus: "reviewed"}));
  });

  test("admin: new -> closed DITOLAK (belum pernah ditinjau)", async () => {
    const docId = await seedChecklist(20260406);
    await assertFails(adminUpdate(docId, {
      followUpStatus: "closed",
      followUpNote: "sudah beres",
    }));
  });

  test("admin: reviewed -> closed dengan catatan DITERIMA", async () => {
    const docId = await seedChecklist(20260407, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertSucceeds(adminUpdate(docId, {
      followUpStatus: "closed",
      followUpNote: "rem diganti",
      followUpUpdatedAtMillis: 1757000000001,
      followUpUpdatedByUid: adminUid,
    }));
  });

  test("admin: reviewed -> closed TANPA catatan DITOLAK", async () => {
    const docId = await seedChecklist(20260408, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertFails(adminUpdate(docId, {followUpStatus: "closed"}));
  });

  test("admin: closed -> reviewed dengan catatan DITERIMA (koreksi berjejak)", async () => {
    const docId = await seedChecklist(20260409, {
      followUpStatus: "closed",
      followUpOwnerUid: "mekanik-1",
      followUpNote: "rem diganti",
    });
    await assertSucceeds(adminUpdate(docId, {
      followUpStatus: "reviewed",
      followUpNote: "salah tutup, rem masih bunyi",
    }));
  });

  test("admin: closed -> reviewed TANPA catatan BARU DITOLAK", async () => {
    // Update Firestore bersifat merge, jadi `request.resource.data` masih membawa
    // catatan penutup yang lama. Tanpa pembandingan terhadap `resource.data`, membuka
    // ulang temuan akan lolos dengan memakai catatan penutupnya sendiri sebagai alasan
    // pembukaan — koreksi tanpa jejak. Test ini GAGAL sebelum pembandingan itu ada.
    const docId = await seedChecklist(20260410, {
      followUpStatus: "closed",
      followUpOwnerUid: "mekanik-1",
      followUpNote: "rem diganti",
    });
    await assertFails(adminUpdate(docId, {followUpStatus: "reviewed"}));
    // Menyalin catatan lama juga tidak cukup.
    await assertFails(adminUpdate(docId, {
      followUpStatus: "reviewed",
      followUpNote: "rem diganti",
    }));
  });

  test("admin: kembali ke 'new' DITOLAK dari status apa pun", async () => {
    const a = await seedChecklist(20260411, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertFails(adminUpdate(a, {followUpStatus: "new"}));

    const b = await seedChecklist(20260412, {
      followUpStatus: "closed",
      followUpOwnerUid: "mekanik-1",
      followUpNote: "rem diganti",
    });
    await assertFails(adminUpdate(b, {followUpStatus: "new", followUpNote: "reset"}));
  });

  test("admin: kode status di luar enum DITOLAK", async () => {
    // Kode disimpan, label diterjemahkan. "Selesai" adalah label.
    const docId = await seedChecklist(20260413, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertFails(adminUpdate(docId, {followUpStatus: "Selesai"}));
  });

  test("admin: field liar TETAP ditolak setelah followUp ditambahkan", async () => {
    // Yang dijaga di sini bukan followUp-nya, tapi bahwa `hasOnlyAllowed` masih menutup
    // himpunan kuncinya setelah lima kunci baru masuk daftar.
    const docId = await seedChecklist(20260414, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertFails(adminUpdate(docId, {followUpSecretBackdoor: "x"}));
  });

  test("driver TIDAK dapat mengubah followUp lewat update", async () => {
    // `validChecklistUpdate` isAdmin()-gated; ini memastikan tidak ada jalur kedua.
    const docId = await seedChecklist(20260415, {
      followUpStatus: "reviewed",
      followUpOwnerUid: "mekanik-1",
    });
    await assertFails(
      testEnv.authenticatedContext(driverUid).firestore()
        .collection("vehicle_checklists").doc(docId)
        .update({followUpStatus: "closed", followUpNote: "beres"}),
    );
  });

  test("driver can CREATE safety_declarations with canonical docId", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260123`;

    await assertSucceeds(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260123, {
          driverName: "Budi",
          hazardAwareness: "Hujan ringan",
        })
      )
    );
  });

  test("driver cannot CREATE safety_declarations with non-canonical docId", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();

    await assertFails(
      db.collection("safety_declarations").doc("anotherDocId").set(
        validSafetyPayload(driverUid, 20260124)
      )
    );
  });

  test("driver cannot overwrite existing safety_declarations doc", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260125`;

    await assertSucceeds(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260125)
      )
    );

    await assertFails(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260125, {
          updatedAt: 999,
          confirmed: false,
        })
      )
    );
  });

  test("driver cannot CREATE safety_declarations with extra fields", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260126`;

    await assertFails(
      db.collection("safety_declarations").doc(docId).set({
        ...validSafetyPayload(driverUid, 20260126),
        unexpected: true,
      })
    );
  });

  test("driver cannot CREATE safety_declarations with wrong field types", async () => {
    const ctx = testEnv.authenticatedContext(driverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260127`;

    await assertFails(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260127, {
          sleepHours: "8",
        })
      )
    );
  });

  test("driver cannot CREATE safety_declarations for another driver", async () => {
    const ctx = testEnv.authenticatedContext(otherDriverUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260128`;

    await assertFails(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260128)
      )
    );
  });

  test("admin can UPDATE safety mutable fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260129`;

    await assertSucceeds(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260129)
      )
    );

    await assertSucceeds(
      db.collection("safety_declarations").doc(docId).update({
        hazardAwareness: "admin review",
        updatedAt: 999,
      })
    );
  });

  test("admin cannot UPDATE immutable safety fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const docId = `${driverUid}_20260130`;

    await assertSucceeds(
      db.collection("safety_declarations").doc(docId).set(
        validSafetyPayload(driverUid, 20260130)
      )
    );

    await assertFails(
      db.collection("safety_declarations").doc(docId).update({
        submittedAt: 999,
      })
    );
  });
});
