const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_alice_123";
const CHILD_UID = "child_sam_456";
const STRANGER_UID = "stranger_eve_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildDevicePayload(parentUid, childUid) {
  return {
    parentUid,
    childUid,
    childName: "Omar",
    deviceName: "Pixel 8",
    androidVersion: "15",
    isOnline: true,
    monitoringActive: true,
    usageAccessGranted: true,
    screenTimeMs: 3600000,
    appCount: 5,
    lastSeenMs: 1700000000000,
    syncStatus: "synced",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

test("Unauthenticated caller is denied all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(PARENT_UID).get());
  await assertFails(unauthDb.collection("devices").get());
  await assertFails(unauthDb.collection("pairing_codes").doc("hash123").get());
});

test("User profile: owner can create/update, stranger is denied, ghost field rejected", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();

  await assertSucceeds(
    parentDb.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Parent Alice",
      email: "alice@example.com",
      privacyMode: false,
      role: "parent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(strangerDb.collection("users").doc(PARENT_UID).get());
  await assertFails(
    parentDb.collection("users").doc(PARENT_UID).update({
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Pairing codes: list is blocked, single-use redemption works once only", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const codeHash = "a1b2c3d4e5f60708";

  await assertSucceeds(
    parentDb.collection("pairing_codes").doc(codeHash).set({
      parentUid: PARENT_UID,
      childLabel: "Omar",
      used: false,
      expiresAtMs: Date.now() + 600000,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(childDb.collection("pairing_codes").get());
  await assertSucceeds(childDb.collection("pairing_codes").doc(codeHash).get());

  await assertSucceeds(
    childDb.collection("pairing_codes").doc(codeHash).update({
      used: true,
      usedByDeviceId: "dev_001",
      updatedAt: new Date(),
    })
  );

  // Second redemption attempt must fail
  await assertFails(
    childDb.collection("pairing_codes").doc(codeHash).update({
      used: true,
      usedByDeviceId: "dev_002",
      updatedAt: new Date(),
    })
  );
});

test("Devices & subcollections: parent/child isolation and query alignment", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const deviceId = "dev_omar_01";

  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).set(buildDevicePayload(PARENT_UID, CHILD_UID))
  );

  // Unfiltered query fails; scoped queries succeed
  await assertFails(parentDb.collection("devices").get());
  await assertSucceeds(
    parentDb.collection("devices").where("parentUid", "==", PARENT_UID).get()
  );
  await assertSucceeds(
    childDb.collection("devices").where("childUid", "==", CHILD_UID).get()
  );

  // Stranger cannot read or delete
  await assertFails(strangerDb.collection("devices").doc(deviceId).get());
  await assertFails(strangerDb.collection("devices").doc(deviceId).delete());

  // Child cannot delete device or modify parent-only limits
  await assertFails(childDb.collection("devices").doc(deviceId).delete());
  await assertFails(
    childDb.collection("devices").doc(deviceId).collection("limits").doc("com.youtube").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      packageName: "com.youtube",
      appName: "YouTube",
      dailyLimitMinutes: 30,
      isEnabled: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Parent can create/update limit; child can read it
  await assertSucceeds(
    parentDb.collection("devices").doc(deviceId).collection("limits").doc("com.youtube").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      packageName: "com.youtube",
      appName: "YouTube",
      dailyLimitMinutes: 30,
      isEnabled: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("limits").where("childUid", "==", CHILD_UID).get()
  );

  // Child can check non-existent usage, apps, alerts, limits before writing
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("usage").doc("2026-11-14").get()
  );
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("apps").doc("com.google.android.youtube").get()
  );
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("alerts").doc("alert_1").get()
  );
  await assertSucceeds(
    parentDb.collection("devices").doc(deviceId).collection("limits").doc("com.youtube").get()
  );
  await assertFails(
    strangerDb.collection("devices").doc(deviceId).collection("usage").doc("2026-11-14").get()
  );

  // Child can write usage & apps & alerts
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("usage").doc("2026-11-14").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      date: "2026-11-14",
      totalScreenTimeMs: 3600000,
      appCount: 3,
      topAppName: "YouTube",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("apps").doc("com.google.android.youtube").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      packageName: "com.google.android.youtube",
      appName: "YouTube",
      usageTimeMs: 1800000,
      launchCount: 4,
      lastUsedMs: 1700000000000,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    childDb.collection("devices").doc(deviceId).collection("alerts").doc("alert_1").set({
      parentUid: PARENT_UID,
      childUid: CHILD_UID,
      deviceId,
      type: "limit_exceeded",
      title: "YouTube limit reached",
      message: "Daily 30 min limit exceeded.",
      isRead: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
