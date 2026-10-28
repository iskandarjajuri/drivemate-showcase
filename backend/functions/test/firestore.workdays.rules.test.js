const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {deleteField} = require("@firebase/firestore");

const PROJECT_ID = "drivemate-rules-test-workdays";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");

describe("Firestore rules: workdays updates", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const driverManagedUid = "driver-uid-1";
  const driverOtherUid = "driver-uid-2";

  const managedWorkdayId = "workday-managed-expat";
  const managedWorkdayAdminId = "workday-managed-admin";
  const managedWorkdayImmutId = "workday-managed-immut";
  const managedWorkdaySchemaId = "workday-managed-schema";
  const managedWorkdayPlanSourceInvalidId = "workday-managed-invalid-plan-source";
  const managedWorkdayFlagsInvalidId = "workday-managed-invalid-flags";
  const adminCreateSchemaId = `${driverManagedUid}_20001`;
  const adminCreateFinalizedId = `${driverManagedUid}_20002`;
  const adminCreateDecisionAuditOverrideId = `${driverManagedUid}_20003`;
  const regenWorkdayId = "workday-managed-regen";
  const preserveWorkdayId = "workday-managed-preserve";
  const rejectedWorkdayId = "workday-managed-rejected";
  const adminOverrideWorkdayId = "workday-managed-admin-override";
  const adminOverrideMissingAuditId = "workday-managed-admin-override-missing";
  const legacyMissingFieldsId = "workday-legacy-missing";
  // Blok 4.2 — jejak keputusan dua langkah.
  const forgeApprovalPendingId = "workday-trail-forge-pending";
  const forgeApprovalAddId = "workday-trail-forge-add";
  const forgeOverrideAddId = "workday-trail-forge-override-add";
  const actorFieldDeleteId = "workday-trail-actor-delete";
  const supervisorStepFreshId = "workday-trail-step1-fresh";
  const supervisorStepMismatchId = "workday-trail-step1-mismatch";
  const supervisorStepIncompleteId = "workday-trail-step1-incomplete";
  const supervisorStepFrozenId = "workday-trail-step1-frozen";
  const supervisorStepAdminId = "workday-trail-step1-admin";
  const adminStepConcurId = "workday-trail-step2-concur";
  const adminStepPendingId = "workday-trail-step2-pending";
  const adminStepForgedActorId = "workday-trail-step2-forged-actor";
  const adminStepMismatchId = "workday-trail-step2-mismatch";
  const adminStepExpatId = "workday-trail-step2-expat";
  const adminStepRestampId = "workday-trail-step2-restamp";
  const adminStepOverrideId = "workday-trail-step2-override";
  const adminStepLongNoteId = "workday-trail-step2-long-note";
  const adminStepRemoveId = "workday-trail-step2-remove";
  const adminStepMergeId = "workday-trail-step2-merge";
  const adminStepStaleId = "workday-trail-step2-stale";
  const trailCreateStep1Id = `${driverManagedUid}_20004`;
  const trailCreateStep2Id = `${driverManagedUid}_20005`;
  const otherWorkdayId = "workday-other";

  const baseClockIn = 1700000000000;
  const baseClockOut = 1700002880000;
  const baseWorkday = (driverUid, overrides = {}) => ({
    driverUid,
    driverName: `Driver ${driverUid}`,
    dateEpochDay: 20000,
    clockInMillis: baseClockIn,
    clockOutMillis: baseClockOut,
    totalMinutes: 480,
    overtimeMinutes: 0,
    status: "pending",
    updatedAt: baseClockOut,
    ...overrides,
  });
  const withCalendarFields = (driverUid, overrides = {}) => ({
    ...baseWorkday(driverUid),
    plannedStartMillis: baseClockIn,
    plannedEndMillis: baseClockOut,
    planSource: "default",
    deviationFlags: ["late_unplanned"],
    reviewHold: true,
    ...overrides,
  });
  const approvePayload = (approverUid, updatedAt) => ({
    approvedByExpat: true,
    status: "approved",
    rejectionNote: null,
    approverUid: approverUid,
    approverName: null,
    approvedByUid: approverUid,
    approvedAtMillis: updatedAt,
    approvalNote: null,
    rejectedByUid: null,
    rejectedAtMillis: null,
    rejectReason: null,
    adjustedBy: approverUid,
    updatedAt: updatedAt,
  });
  const rejectPayload = (approverUid, updatedAt, reason) => ({
    approvedByExpat: false,
    status: "rejected",
    rejectionNote: reason,
    approverUid: approverUid,
    approverName: null,
    rejectedByUid: approverUid,
    rejectedAtMillis: updatedAt,
    rejectReason: reason,
    approvedByUid: null,
    approvedAtMillis: null,
    approvalNote: null,
    adjustedBy: approverUid,
    updatedAt: updatedAt,
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
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverManagedUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(driverOtherUid).set({
        type: "driver",
        managerUid: "someoneElse",
      });

      await db.collection("workdays").doc(managedWorkdayId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(managedWorkdayAdminId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(managedWorkdayImmutId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(managedWorkdaySchemaId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(managedWorkdayPlanSourceInvalidId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(managedWorkdayFlagsInvalidId).set(baseWorkday(driverManagedUid));
      await db
        .collection("workdays")
        .doc(legacyMissingFieldsId)
        .set({
          driverUid: driverManagedUid,
          clockInMillis: baseClockIn,
          clockOutMillis: baseClockOut,
          updatedAt: baseClockOut,
        });
      await db
        .collection("workdays")
        .doc(regenWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "approved",
            approvedByExpat: true,
            approverUid: expatUid,
            approverName: "Expat",
            approvedAtMillis: 1700002880000,
            approvalNote: "OK",
          })
        );
      await db
        .collection("workdays")
        .doc(preserveWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "approved",
            approvedByExpat: true,
            approverUid: expatUid,
            approverName: "Expat",
            approvedAtMillis: 1700002880000,
            approvalNote: "OK",
          })
        );
      await db
        .collection("workdays")
        .doc(rejectedWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "rejected",
            approvedByExpat: false,
            approverUid: expatUid,
            approverName: "Expat",
            rejectedByUid: expatUid,
            rejectedAtMillis: 1700002880000,
            rejectReason: "Initial rejection",
            rejectionNote: "Initial rejection",
          })
        );
      await db
        .collection("workdays")
        .doc(adminOverrideWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "approved",
            approvedByExpat: true,
            approverUid: expatUid,
            approverName: "Expat",
            approvedByUid: expatUid,
            approvedAtMillis: 1700002880000,
            approvalNote: "OK",
          })
        );
      await db
        .collection("workdays")
        .doc(adminOverrideMissingAuditId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "approved",
            approvedByExpat: true,
            approverUid: expatUid,
            approverName: "Expat",
            approvedByUid: expatUid,
            approvedAtMillis: 1700002880000,
            approvalNote: "OK",
          })
        );
      await db.collection("workdays").doc(otherWorkdayId).set(baseWorkday(driverOtherUid));

      // ── Blok 4.2 seeds ────────────────────────────────────────────────────
      const approvedByExpatDoc = (extra = {}) =>
        baseWorkday(driverManagedUid, {
          status: "approved",
          approvedByExpat: true,
          approverUid: expatUid,
          approvedByUid: expatUid,
          approvedAtMillis: baseClockOut,
          ...extra,
        });
      const recordedAdminStep = {
        decisionTrail: {
          admin: {status: "approved", byUid: adminUid, atMillis: baseClockOut},
        },
      };

      await db.collection("workdays").doc(forgeApprovalPendingId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(forgeApprovalAddId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(forgeOverrideAddId).set(approvedByExpatDoc());
      await db
        .collection("workdays")
        .doc(actorFieldDeleteId)
        .set(baseWorkday(driverManagedUid, {adjustedBy: expatUid}));
      await db.collection("workdays").doc(supervisorStepFreshId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(supervisorStepMismatchId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(supervisorStepIncompleteId).set(baseWorkday(driverManagedUid));
      await db
        .collection("workdays")
        .doc(supervisorStepFrozenId)
        .set(
          approvedByExpatDoc({
            decisionTrail: {
              supervisor: {status: "approved", byUid: expatUid, atMillis: baseClockOut},
            },
          })
        );
      await db.collection("workdays").doc(supervisorStepAdminId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(adminStepConcurId).set(approvedByExpatDoc());
      await db.collection("workdays").doc(adminStepPendingId).set(baseWorkday(driverManagedUid));
      await db.collection("workdays").doc(adminStepForgedActorId).set(approvedByExpatDoc());
      await db.collection("workdays").doc(adminStepMismatchId).set(approvedByExpatDoc());
      await db.collection("workdays").doc(adminStepExpatId).set(approvedByExpatDoc());
      await db.collection("workdays").doc(adminStepRestampId).set(approvedByExpatDoc(recordedAdminStep));
      await db.collection("workdays").doc(adminStepOverrideId).set(approvedByExpatDoc(recordedAdminStep));
      await db.collection("workdays").doc(adminStepLongNoteId).set(approvedByExpatDoc());
      await db.collection("workdays").doc(adminStepRemoveId).set(approvedByExpatDoc(recordedAdminStep));
      // Sudah di-override menjadi `rejected`, tapi langkah 2 masih mencatat `approved` —
      // keadaan yang persis muncul setelah `adminOverrideDecision` (yang tidak menulis
      // jejak sama sekali). Langkah 2 di sini KEDALUWARSA dan harus bisa disegarkan.
      await db
        .collection("workdays")
        .doc(adminStepStaleId)
        .set(
          baseWorkday(driverManagedUid, {
            status: "rejected",
            approvedByExpat: false,
            approverUid: adminUid,
            rejectedByUid: adminUid,
            rejectedAtMillis: baseClockOut,
            rejectReason: "Jam pulang tidak sesuai bukti",
            overrideByUid: adminUid,
            overrideAtMillis: baseClockOut,
            overrideReason: "Koreksi setelah audit",
            overrideFromStatus: "approved",
            decisionTrail: {
              supervisor: {status: "approved", byUid: expatUid, atMillis: baseClockOut},
              admin: {status: "approved", byUid: adminUid, atMillis: baseClockOut},
            },
          })
        );
      await db
        .collection("workdays")
        .doc(adminStepMergeId)
        .set(
          approvedByExpatDoc({
            decisionTrail: {
              supervisor: {status: "approved", byUid: expatUid, atMillis: baseClockOut},
            },
          })
        );
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("expat can bulk-approve managed driver workday with exact payload", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayId);
    const now = baseClockOut + 5000;

    await assertSucceeds(ref.update(approvePayload(expatUid, now)));
  });

  test("expat cannot approve managed workday when approverUid mismatches auth uid", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayId);
    const now = baseClockOut + 5100;

    await assertFails(
      ref.update({
        ...approvePayload(expatUid, now),
        approverUid: "spoofed-uid",
      })
    );
  });

  test("expat cannot approve managed workday when adjustedBy mismatches auth uid", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayId);
    const now = baseClockOut + 5200;

    await assertFails(
      ref.update({
        ...approvePayload(expatUid, now),
        adjustedBy: "spoofed-uid",
      })
    );
  });

  test("expat cannot approve non-managed driver workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(otherWorkdayId);
    const now = baseClockOut + 6000;

    await assertFails(ref.update(approvePayload(expatUid, now)));
  });

  test("admin can approve any workday with exact payload", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayAdminId);
    const now = baseClockOut + 7000;

    await assertSucceeds(ref.update(approvePayload(adminUid, now)));
  });

  test("admin can create workday with canonical doc ID and calendar/deviation fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(adminCreateSchemaId);
    const now = baseClockOut + 7100;

    await assertSucceeds(
      ref.set(
        withCalendarFields(driverManagedUid, {
          dateEpochDay: 20001,
          updatedAt: now,
        })
      )
    );
  });

  test("admin cannot create workday with finalized status", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(adminCreateFinalizedId);
    const now = baseClockOut + 7110;

    await assertFails(
      ref.set(
        withCalendarFields(driverManagedUid, {
          dateEpochDay: 20002,
          status: "approved",
          updatedAt: now,
        })
      )
    );
  });

  test("admin cannot create workday with decision/audit/override fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(adminCreateDecisionAuditOverrideId);
    const now = baseClockOut + 7120;

    await assertFails(
      ref.set(
        withCalendarFields(driverManagedUid, {
          dateEpochDay: 20003,
          status: "pending",
          approvedByUid: adminUid,
          approvedAtMillis: now,
          approverUid: adminUid,
          adjustedBy: adminUid,
          overrideByUid: adminUid,
          overrideAtMillis: now,
          overrideReason: "not allowed on create",
          overrideFromStatus: "approved",
          updatedAt: now,
        })
      )
    );
  });

  test("admin cannot create workday with non-canonical doc ID", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc("not-a-canonical-id");
    const now = baseClockOut + 7200;

    await assertFails(
      ref.set(
        withCalendarFields(driverManagedUid, {
          dateEpochDay: 20004,
          updatedAt: now,
        })
      )
    );
  });

  test("admin cannot create workday when doc ID uid does not match driverUid field", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc("wrong-uid-99_20005");
    const now = baseClockOut + 7300;

    await assertFails(
      ref.set(
        withCalendarFields(driverManagedUid, {
          dateEpochDay: 20005,
          updatedAt: now,
        })
      )
    );
  });

  test("expat can update managed workday with calendar/deviation fields", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdaySchemaId);
    const now = baseClockOut + 7200;

    await assertSucceeds(
      ref.update({
        plannedStartMillis: baseClockIn,
        plannedEndMillis: baseClockOut,
        planSource: "explicit",
        deviationFlags: ["early_unplanned", "late_unplanned"],
        reviewHold: true,
        updatedAt: now,
      })
    );
  });

  test("workday update fails when planSource is invalid", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayPlanSourceInvalidId);
    const now = baseClockOut + 7300;

    await assertFails(
      ref.update({
        plannedStartMillis: baseClockIn,
        plannedEndMillis: baseClockOut,
        planSource: "invalid_plan_source",
        updatedAt: now,
      })
    );
  });

  test("workday update fails when deviationFlags contains unknown value", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayFlagsInvalidId);
    const now = baseClockOut + 7400;

    await assertFails(
      ref.update({
        deviationFlags: ["late_unplanned", "unknown_flag"],
        updatedAt: now,
      })
    );
  });

  test("expat can approve legacy workday after backfill fields supplied", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(legacyMissingFieldsId);
    const now = baseClockOut + 7500;

    await assertSucceeds(
      ref.update({
        ...approvePayload(expatUid, now),
        dateEpochDay: 20000,
        totalMinutes: 0,
      })
    );
  });

  test("expat can reject managed driver workday with exact payload", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayFlagsInvalidId);
    const now = baseClockOut + 8000;

    await assertSucceeds(ref.update(rejectPayload(expatUid, now, "Tidak sesuai")));
  });

  test("expat cannot reject managed workday when rejectedByUid mismatches auth uid", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(managedWorkdayFlagsInvalidId);
    const now = baseClockOut + 8100;

    await assertFails(
      ref.update({
        ...rejectPayload(expatUid, now, "Tidak sesuai"),
        rejectedByUid: "spoofed-uid",
      })
    );
  });

  test("expat cannot flip approved workday to rejected", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(regenWorkdayId);
    const now = baseClockOut + 8150;

    await assertFails(ref.update(rejectPayload(expatUid, now, "Flip not allowed")));
  });

  test("expat cannot flip rejected workday to approved", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(rejectedWorkdayId);
    const now = baseClockOut + 8175;

    await assertFails(ref.update(approvePayload(expatUid, now)));
  });

  test("expat cannot re-approve approved workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(preserveWorkdayId);
    const now = baseClockOut + 8185;

    await assertFails(ref.update(approvePayload(expatUid, now)));
  });

  test("expat cannot update finalized workday when computed fields change", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(regenWorkdayId);

    const newClockIn = 1700000000000;
    const newClockOut = 1700003240000;
    await assertFails(
      ref.update({
        clockInMillis: newClockIn,
        clockOutMillis: newClockOut,
        totalMinutes: 540,
        overtimeMinutes: 60,
        updatedAt: newClockOut,
        status: "pending",
        approvedByExpat: deleteField(),
        approverUid: deleteField(),
        approverName: deleteField(),
        approvedByUid: deleteField(),
        approvedAtMillis: deleteField(),
        approvalNote: deleteField(),
        rejectedByUid: deleteField(),
        rejectedAtMillis: deleteField(),
        rejectReason: deleteField(),
        rejectionNote: deleteField(),
      })
    );
  });

  test("expat cannot update finalized workday even when computed fields are unchanged", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(preserveWorkdayId);

    await assertFails(
      ref.update({
        clockInMillis: baseClockIn,
        clockOutMillis: baseClockOut,
        totalMinutes: 480,
        overtimeMinutes: 0,
        updatedAt: baseClockOut + 1000,
      })
    );
  });

  test("admin can override approved workday to rejected with required audit fields", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(adminOverrideWorkdayId);
    const now = baseClockOut + 9000;

    await assertSucceeds(
      ref.update({
        ...rejectPayload(adminUid, now, "Corrected after manager review"),
        overrideByUid: adminUid,
        overrideAtMillis: now,
        overrideReason: "Correction after audit",
        overrideFromStatus: "approved",
      })
    );
  });

  test("admin override status change without required override audit fields is denied", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(adminOverrideMissingAuditId);
    const now = baseClockOut + 9100;

    await assertFails(
      ref.update({
        ...rejectPayload(adminUid, now, "Corrected after manager review"),
        overrideByUid: adminUid,
        overrideAtMillis: now,
      })
    );
  });

  test("changing driverUid on update must fail for admin and expat", async () => {
    const expatCtx = testEnv.authenticatedContext(expatUid);
    const adminCtx = testEnv.authenticatedContext(adminUid);
    const expatDb = expatCtx.firestore();
    const adminDb = adminCtx.firestore();

    await assertFails(
      expatDb
        .collection("workdays")
        .doc(managedWorkdayImmutId)
        .update({driverUid: driverOtherUid})
    );

    await assertFails(
      adminDb
        .collection("workdays")
        .doc(managedWorkdayImmutId)
        .update({driverUid: driverOtherUid})
    );
  });

  test("changing createdAt on update must fail for admin and expat", async () => {
    const expatCtx = testEnv.authenticatedContext(expatUid);
    const adminCtx = testEnv.authenticatedContext(adminUid);
    const expatDb = expatCtx.firestore();
    const adminDb = adminCtx.firestore();
    const mutatedCreatedAt = baseClockOut + 9200;

    await assertFails(
      expatDb
        .collection("workdays")
        .doc(managedWorkdayImmutId)
        .update({createdAt: mutatedCreatedAt})
    );

    await assertFails(
      adminDb
        .collection("workdays")
        .doc(managedWorkdayImmutId)
        .update({createdAt: mutatedCreatedAt})
    );
  });
  // ═══════════════════════════════════════════════════════════════════════════
  // Blok 4.2 — jejak keputusan dua langkah
  //
  // Kelompok pertama adalah REGRESI, bukan fitur: ketiganya LULUS (yakni write-nya
  // diizinkan) sebelum `actorAuditFieldsMatchAuthUid` beralih dari `changedKeys()` ke
  // `affectedKeys()`. `changedKeys()` hanya melaporkan key yang ada di kedua map dengan
  // nilai berbeda, jadi penambahan PERTAMA sebuah field aktor lewat tanpa diperiksa — dan
  // penambahan pertama adalah keadaan normal setiap field audit.
  // ═══════════════════════════════════════════════════════════════════════════

  test("admin tidak bisa mengarang persetujuan supervisor atas nama expat (changedKeys)", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 30000;

    await assertFails(
      db.collection("workdays").doc(forgeApprovalPendingId).update({
        status: "approved",
        approvedByExpat: true,
        approverUid: expatUid,
        approverName: "Expat",
        approvedByUid: expatUid,
        approvedAtMillis: now,
        updatedAt: now,
      })
    );
  });

  test("admin tidak bisa MENAMBAH approvedByUid milik expat tanpa menulis status (changedKeys)", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 30100;

    await assertFails(
      db.collection("workdays").doc(forgeApprovalAddId).update({
        approvedByUid: expatUid,
        approvedAtMillis: now,
        updatedAt: now,
      })
    );
  });

  test("admin tidak bisa MENAMBAH overrideByUid milik expat (changedKeys)", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 30200;

    await assertFails(
      db.collection("workdays").doc(forgeOverrideAddId).update({
        overrideByUid: expatUid,
        updatedAt: now,
      })
    );
  });

  test("admin tetap bisa menghapus field aktor saat reset approval", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 30300;

    // Cabang penghapusan pada actorFieldMatchesAuthUid: `affectedKeys()` memuat key yang
    // dihapus — dan mengakses key yang tidak ada pada map akan error, bukan `null`. Tanpa
    // cabang itu, reset approval oleh `WorkdayGenerationRepository` ikut tertolak.
    await assertSucceeds(
      db.collection("workdays").doc(actorFieldDeleteId).update({
        adjustedBy: deleteField(),
        updatedAt: now,
      })
    );
  });

  // ── Langkah 1: peninjauan supervisor ──────────────────────────────────────

  test("expat mencatat langkah 1 saat menyetujui", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 31000;

    await assertSucceeds(
      db.collection("workdays").doc(supervisorStepFreshId).update({
        ...approvePayload(expatUid, now),
        "decisionTrail.supervisor": {status: "approved", byUid: expatUid, atMillis: now},
      })
    );
  });

  test("langkah 1 dengan pelaku orang lain ditolak", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 31100;

    await assertFails(
      db.collection("workdays").doc(supervisorStepMismatchId).update({
        ...approvePayload(expatUid, now),
        "decisionTrail.supervisor": {status: "approved", byUid: adminUid, atMillis: now},
      })
    );
  });

  test("langkah 1 yang tidak sepakat dengan keputusan write-nya ditolak", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 31200;

    await assertFails(
      db.collection("workdays").doc(supervisorStepMismatchId).update({
        ...approvePayload(expatUid, now),
        "decisionTrail.supervisor": {status: "rejected", byUid: expatUid, atMillis: now},
      })
    );
  });

  test("langkah 1 tanpa waktu ditolak", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 31300;

    await assertFails(
      db.collection("workdays").doc(supervisorStepIncompleteId).update({
        ...approvePayload(expatUid, now),
        "decisionTrail.supervisor": {status: "approved", byUid: expatUid},
      })
    );
  });

  test("langkah 1 beku setelah tercatat — expat sendiri pun tidak bisa menimpanya", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 31400;

    await assertFails(
      db.collection("workdays").doc(supervisorStepFrozenId).update({
        "decisionTrail.supervisor": {status: "approved", byUid: expatUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("admin tidak boleh menulis langkah 1", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 31500;

    await assertFails(
      db.collection("workdays").doc(supervisorStepAdminId).update({
        ...approvePayload(adminUid, now),
        "decisionTrail.supervisor": {status: "approved", byUid: adminUid, atMillis: now},
      })
    );
  });

  // ── Langkah 2: keputusan admin ────────────────────────────────────────────

  test("admin mencatat langkah 2 di atas keputusan supervisor", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32000;

    await assertSucceeds(
      db.collection("workdays").doc(adminStepConcurId).update({
        "decisionTrail.admin": {
          status: "approved",
          byUid: adminUid,
          atMillis: now,
          note: "Angka lembur sudah dicek ulang",
        },
        "updatedAt": now,
      })
    );
  });

  test("langkah 2 pada workday yang belum ditinjau ditolak", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32100;

    await assertFails(
      db.collection("workdays").doc(adminStepPendingId).update({
        "decisionTrail.admin": {status: "approved", byUid: adminUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("langkah 2 dengan pelaku orang lain ditolak", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32200;

    await assertFails(
      db.collection("workdays").doc(adminStepForgedActorId).update({
        "decisionTrail.admin": {status: "approved", byUid: expatUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("langkah 2 yang tidak sepakat dengan keputusan dokumen ditolak", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32300;

    await assertFails(
      db.collection("workdays").doc(adminStepMismatchId).update({
        "decisionTrail.admin": {status: "rejected", byUid: adminUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("expat tidak boleh menulis langkah 2", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();
    const now = baseClockOut + 32400;

    await assertFails(
      db.collection("workdays").doc(adminStepExpatId).update({
        "decisionTrail.admin": {status: "approved", byUid: expatUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("admin kedua tidak bisa menukar nama pada langkah 2 tanpa override", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32500;

    // Pelakunya sendiri (adminUid) pun ditolak: yang dilarang adalah menimpa langkah 2
    // tanpa perubahan keputusan, bukan sekadar menimpa milik orang lain.
    await assertFails(
      db.collection("workdays").doc(adminStepRestampId).update({
        "decisionTrail.admin": {status: "approved", byUid: adminUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("langkah 2 boleh diganti sebagai bagian dari override yang sah", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32600;

    await assertSucceeds(
      db.collection("workdays").doc(adminStepOverrideId).update({
        ...rejectPayload(adminUid, now, "Jam pulang tidak sesuai bukti"),
        "overrideByUid": adminUid,
        "overrideAtMillis": now,
        "overrideReason": "Koreksi setelah audit",
        "overrideFromStatus": "approved",
        "decisionTrail.admin": {status: "rejected", byUid: adminUid, atMillis: now},
      })
    );
  });

  test("catatan langkah 2 lebih dari 500 karakter ditolak", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32700;

    await assertFails(
      db.collection("workdays").doc(adminStepLongNoteId).update({
        "decisionTrail.admin": {
          status: "approved",
          byUid: adminUid,
          atMillis: now,
          note: "x".repeat(501),
        },
        "updatedAt": now,
      })
    );
  });

  test("langkah 2 tidak bisa dihapus setelah tercatat", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32800;

    await assertFails(
      db.collection("workdays").doc(adminStepRemoveId).update({
        "decisionTrail.admin": deleteField(),
        "updatedAt": now,
      })
    );
  });


  test("langkah 2 yang kedaluwarsa boleh disegarkan di write terpisah", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 33000;

    // Write ini BUKAN override — statusnya tidak berubah, jadi tidak ada audit override.
    // Yang membuatnya sah adalah langkah 2 tercatat `approved` sementara dokumennya
    // `rejected`. Syarat sebelumnya ("hanya boleh dalam write yang memang override")
    // menolak write ini, sehingga jejaknya membeku membantah statusnya sendiri.
    await assertSucceeds(
      db.collection("workdays").doc(adminStepStaleId).update({
        "decisionTrail.admin": {status: "rejected", byUid: adminUid, atMillis: now},
        "updatedAt": now,
      })
    );
  });

  test("merge bersarang mempertahankan langkah 1 saat admin mencatat langkah 2", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const now = baseClockOut + 32900;

    // Bentuk yang BENAR-BENAR dipakai klien: `FirebaseAdminDataRepository` menulis
    // `set({decisionTrail: {admin: {...}}}, {merge: true})`, bukan path bertitik. Test ini
    // ada karena klaim "SetOptions.merge() menggabungkan map bersarang alih-alih
    // menggantinya" adalah satu-satunya alasan langkah 1 selamat — kalau merge ternyata
    // MENGGANTI, write-nya justru ditolak rules (langkah 1 wajib lestari), dan tanpa test
    // ini kegagalannya baru muncul di perangkat.
    await assertSucceeds(
      db.collection("workdays").doc(adminStepMergeId).set(
        {
          decisionTrail: {admin: {status: "approved", byUid: adminUid, atMillis: now}},
          updatedAt: now,
        },
        {merge: true}
      )
    );

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const snap = await context
        .firestore()
        .collection("workdays")
        .doc(adminStepMergeId)
        .get();
      const trail = snap.data().decisionTrail;
      expect(trail.supervisor).toEqual({
        status: "approved",
        byUid: expatUid,
        atMillis: baseClockOut,
      });
      expect(trail.admin).toEqual({status: "approved", byUid: adminUid, atMillis: now});
    });
  });

  // ── Create: jejak tidak boleh lahir bersama dokumennya ────────────────────

  test("create dengan field langkah 1 ditolak", async () => {
    const db = testEnv.authenticatedContext(expatUid).firestore();

    await assertFails(
      db.collection("workdays").doc(trailCreateStep1Id).set(
        baseWorkday(driverManagedUid, {
          dateEpochDay: 20004,
          createdAt: baseClockOut,
          decisionTrail: {
            supervisor: {status: "approved", byUid: expatUid, atMillis: baseClockOut},
          },
        })
      )
    );
  });

  test("create dengan field langkah 2 ditolak", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();

    await assertFails(
      db.collection("workdays").doc(trailCreateStep2Id).set(
        baseWorkday(driverManagedUid, {
          dateEpochDay: 20005,
          createdAt: baseClockOut,
          decisionTrail: {
            admin: {status: "approved", byUid: adminUid, atMillis: baseClockOut},
          },
        })
      )
    );
  });
});

describe("Firestore rules: workdays driverName backfill", () => {
  let testEnv;

  const adminUid = "admin_1";
  const expatUid = "expat_1";
  const driverManagedUid = "driver_managed";
  const driverOtherUid = "driver_other";

  const legacyManagedWorkdayId = "workday-legacy-managed";
  const nullNameManagedWorkdayId = "workday-nullname-managed";
  const emptyNameManagedWorkdayId = "workday-emptyname-managed";
  const whitespaceNameManagedWorkdayId = "workday-whitespacename-managed";
  const namedManagedWorkdayId = "workday-named-managed";
  const legacyOtherWorkdayId = "workday-legacy-other";

  const baseClockIn = 1700005000000;
  const baseClockOut = 1700008600000;
  const baseWorkday = (driverUid, overrides = {}) => ({
    driverUid,
    dateEpochDay: 20010,
    clockInMillis: baseClockIn,
    clockOutMillis: baseClockOut,
    totalMinutes: 600,
    overtimeMinutes: 120,
    status: "pending",
    updatedAt: baseClockOut,
    ...overrides,
  });

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "drivemate-rules-test-workdays-drivername",
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });

    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await db.collection("users").doc(adminUid).set({type: "admin"});
      await db.collection("users").doc(expatUid).set({type: "expat"});
      await db.collection("users").doc(driverManagedUid).set({
        type: "driver",
        managerUid: expatUid,
      });
      await db.collection("users").doc(driverOtherUid).set({
        type: "driver",
        managerUid: "someone_else",
      });

      await db
        .collection("workdays")
        .doc(legacyManagedWorkdayId)
        .set(baseWorkday(driverManagedUid));
      await db
        .collection("workdays")
        .doc(nullNameManagedWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            driverName: null,
          })
        );
      await db
        .collection("workdays")
        .doc(emptyNameManagedWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            driverName: "",
          })
        );
      await db
        .collection("workdays")
        .doc(whitespaceNameManagedWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            driverName: "   ",
          })
        );
      await db
        .collection("workdays")
        .doc(namedManagedWorkdayId)
        .set(
          baseWorkday(driverManagedUid, {
            driverName: "Existing Name",
          })
        );
      await db
        .collection("workdays")
        .doc(legacyOtherWorkdayId)
        .set(baseWorkday(driverOtherUid));
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("expat can backfill driverName when missing on legacy managed workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(legacyManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "Iskandar Jajuri",
        updatedAt: baseClockOut + 1000,
      })
    );

    let data;
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const snap = await context.firestore().collection("workdays").doc(legacyManagedWorkdayId).get();
      data = snap.data();
    });
    expect(data.driverName).toBe("Iskandar Jajuri");
  });

  test("expat can backfill driverName when null on managed workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(nullNameManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "John",
        updatedAt: baseClockOut + 1500,
      })
    );
  });

  test("expat can backfill driverName when empty string on managed workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(emptyNameManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "Budi",
        updatedAt: baseClockOut + 1600,
      })
    );
  });

  test("expat can backfill driverName when whitespace-only on managed workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(whitespaceNameManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "Sari",
        updatedAt: baseClockOut + 1700,
      })
    );
  });

  test("expat cannot overwrite driverName after backfill from blank", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(emptyNameManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "Budi",
        updatedAt: baseClockOut + 1800,
      })
    );
    await assertFails(
      ref.update({
        driverName: "Andi",
        updatedAt: baseClockOut + 1900,
      })
    );
  });

  test("expat cannot overwrite driverName when already present", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(namedManagedWorkdayId);

    await assertFails(
      ref.update({
        driverName: "HACK",
        updatedAt: baseClockOut + 2000,
      })
    );
  });

  test("expat cannot clear driverName when already present", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(namedManagedWorkdayId);

    await assertFails(
      ref.update({
        driverName: null,
        updatedAt: baseClockOut + 2500,
      })
    );
  });

  test("expat cannot backfill driverName for non-managed driver workday", async () => {
    const ctx = testEnv.authenticatedContext(expatUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(legacyOtherWorkdayId);

    await assertFails(
      ref.update({
        driverName: "Nope",
        updatedAt: baseClockOut + 3000,
      })
    );
  });

  test("admin can overwrite driverName anytime", async () => {
    const ctx = testEnv.authenticatedContext(adminUid);
    const db = ctx.firestore();
    const ref = db.collection("workdays").doc(namedManagedWorkdayId);

    await assertSucceeds(
      ref.update({
        driverName: "Admin Override",
        updatedAt: baseClockOut + 4000,
      })
    );
  });
});
