const fs = require("fs");
const path = require("path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "drivemate-rules-test-work-calendars";
const RULES_PATH = path.resolve(__dirname, "..", "..", "firestore.rules");
jest.setTimeout(30000);

describe("Firestore rules: work_calendars", () => {
  let testEnv;

  const adminUid = "admin-uid-1";
  const expatUid = "expat-uid-1";
  const driverUid = "driver-uid-1";

  const validWorkSpec = {
    startMin: 450,
    endMin: 990,
    breakMin: 60,
  };

  const validFridaySpec = {
    startMin: 450,
    endMin: 1010,
    breakMin: 60,
  };

  const buildCalendarPayload = (overrides = [], extra = {}) => ({
    year: 2026,
    updatedAtMillis: 1700000000000,
    timeZoneId: "Asia/Jakarta",
    defaultRules: {
      mondayToThursday: {...validWorkSpec},
      friday: {...validFridaySpec},
      weekendDefault: "OFF",
    },
    overrides,
    ...extra,
  });

  const holidayOverride = () => ({
    dateEpochDay: 20001,
    kind: "HOLIDAY",
    workSpec: null,
    note: "National holiday",
    updatedAtMillis: 1700000000100,
  });

  const swapOverride = () => ({
    dateEpochDay: 20002,
    kind: "WORKDAY_SWAP",
    workSpec: null,
    note: null,
    updatedAtMillis: 1700000000200,
  });

  const customOverride = () => ({
    dateEpochDay: 20003,
    kind: "CUSTOM",
    workSpec: {
      startMin: 480,
      endMin: 1020,
      breakMin: 60,
    },
    note: "Standard schedule",
    updatedAtMillis: 1700000000300,
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
      await db.collection("users").doc(driverUid).set({
        type: "driver",
        managerUid: expatUid,
      });

      await db.collection("work_calendars").doc("2026").set(
        buildCalendarPayload([holidayOverride(), swapOverride()])
      );
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  test("unauthenticated cannot read calendar", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(db.collection("work_calendars").doc("2026").get());
  });

  test("driver and expat can read calendar", async () => {
    const driverDb = testEnv.authenticatedContext(driverUid).firestore();
    const expatDb = testEnv.authenticatedContext(expatUid).firestore();

    await assertSucceeds(driverDb.collection("work_calendars").doc("2026").get());
    await assertSucceeds(expatDb.collection("work_calendars").doc("2026").get());
  });

  test("admin can read and write calendar", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(db.collection("work_calendars").doc("2026").get());
    await assertSucceeds(
      db.collection("work_calendars").doc("2026").set(
        buildCalendarPayload([holidayOverride(), swapOverride(), customOverride()])
      )
    );
  });

  test("admin can read own users doc used by role helper", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(db.collection("users").doc(adminUid).get());
  });

  test("admin can read missing calendar doc without permission denied", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertSucceeds(db.collection("work_calendars").doc("2031").get());
  });

  test("authenticated user without users doc cannot read calendar", async () => {
    const orphanUid = "orphan-uid-1";
    const db = testEnv.authenticatedContext(orphanUid).firestore();
    await assertFails(db.collection("work_calendars").doc("2026").get());
  });

  test("driver and expat cannot write calendar", async () => {
    const driverDb = testEnv.authenticatedContext(driverUid).firestore();
    const expatDb = testEnv.authenticatedContext(expatUid).firestore();
    const payload = buildCalendarPayload([holidayOverride()]);

    await assertFails(driverDb.collection("work_calendars").doc("2026").set(payload));
    await assertFails(expatDb.collection("work_calendars").doc("2026").set(payload));
  });

  test("reject non-4-digit year document id", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("work_calendars").doc("20261").set(buildCalendarPayload())
    );
  });

  test("reject payload when year field does not match doc id", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload([], {year: 2025}))
    );
  });

  test("reject payload with unexpected top-level fields", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("work_calendars").doc("2026").set(
        buildCalendarPayload([], {unexpectedField: true})
      )
    );
  });

  test("reject invalid default rules shape", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const payload = buildCalendarPayload();
    delete payload.defaultRules.friday;

    await assertFails(db.collection("work_calendars").doc("2026").set(payload));
  });

  test("reject invalid work spec ranges", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const payload = buildCalendarPayload();
    payload.defaultRules.mondayToThursday = {
      startMin: 500,
      endMin: 500,
      breakMin: 0,
    };

    await assertFails(db.collection("work_calendars").doc("2026").set(payload));
  });

  test("reject holiday override with non-null workSpec", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const invalidHoliday = holidayOverride();
    invalidHoliday.workSpec = {...validWorkSpec};

    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload([invalidHoliday]))
    );
  });

  test("reject custom override with missing workSpec", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const invalidCustom = customOverride();
    invalidCustom.workSpec = null;

    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload([invalidCustom]))
    );
  });

  test("reject custom override with invalid workSpec", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const invalidCustom = customOverride();
    invalidCustom.workSpec = {
      startMin: 1000,
      endMin: 900,
      breakMin: 30,
    };

    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload([invalidCustom]))
    );
  });

  test("reject override note longer than 300 chars", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const invalidCustom = customOverride();
    invalidCustom.note = "x".repeat(301);

    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload([invalidCustom]))
    );
  });

  test("reject non-Asia/Jakarta timezone", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    await assertFails(
      db.collection("work_calendars").doc("2026").set(
        buildCalendarPayload([], {timeZoneId: "UTC"})
      )
    );
  });

  test("reject payload when overrides exceed hard validation cap", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const tooManyOverrides = Array.from({length: 31}, (_, idx) => ({
      dateEpochDay: 21000 + idx,
      kind: "HOLIDAY",
      workSpec: null,
      note: "cap check",
      updatedAtMillis: 1700001000000 + idx,
    }));

    await assertFails(
      db.collection("work_calendars").doc("2026").set(buildCalendarPayload(tooManyOverrides))
    );
  });

  test("allow Android repository-compatible happy path payload", async () => {
    const db = testEnv.authenticatedContext(adminUid).firestore();
    const payload = buildCalendarPayload([
      {
        dateEpochDay: 20010,
        kind: "HOLIDAY",
        workSpec: null,
        note: null,
        updatedAtMillis: 1700000001000,
      },
      {
        dateEpochDay: 20011,
        kind: "WORKDAY_SWAP",
        workSpec: null,
        note: "Swap for weekend replacement",
        updatedAtMillis: 1700000001100,
      },
      {
        dateEpochDay: 20012,
        kind: "CUSTOM",
        workSpec: {
          startMin: 480,
          endMin: 1020,
          breakMin: 60,
        },
        note: "Special production shift",
        updatedAtMillis: 1700000001200,
      },
    ]);

    await assertSucceeds(db.collection("work_calendars").doc("2026").set(payload));
  });
});
