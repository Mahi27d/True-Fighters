const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const DATABASE_ID = "ai-studio-android-truefigh-0d444016-a20d-4c16-a6bc-19d38e7bb9a0";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";
const ADMIN_UID = "admin_owner_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read branches or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("branches").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated student: can read branches and create admission request", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("branches").get());
  await assertSucceeds(aliceDb.collection("admissionRequests").add({
    fullName: "Alice Karate",
    email: "alice@test.com",
    phoneNumber: "9876543210",
    programId: "prog_karate",
    preferredBranchId: "branch_main",
    status: "pending",
    createdAt: new Date().toISOString()
  }));
});

test("Authenticated student: can read own student record and invoices but cannot read others", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("students").doc("student_alice").set({
      userId: ALICE_UID,
      fullName: "Alice Student",
      branchId: "branch_main",
      status: "active"
    });
    await db.collection("students").doc("student_bob").set({
      userId: BOB_UID,
      fullName: "Bob Student",
      branchId: "branch_main",
      status: "active"
    });
    await db.collection("feeInvoices").doc("inv_alice").set({
      studentUserId: ALICE_UID,
      amountPaise: 250000,
      status: "unpaid"
    });
    await db.collection("feeInvoices").doc("inv_bob").set({
      studentUserId: BOB_UID,
      amountPaise: 300000,
      status: "unpaid"
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("students").doc("student_alice").get());
  await assertFails(aliceDb.collection("students").doc("student_bob").get());

  await assertSucceeds(aliceDb.collection("feeInvoices").doc("inv_alice").get());
  await assertFails(aliceDb.collection("feeInvoices").doc("inv_bob").get());
});

test("Security check: Student cannot self-grant admin privileges", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Attempt to write into admins collection
  await assertFails(aliceDb.collection("admins").doc(ALICE_UID).set({ role: "admin" }));
  // Attempt to update own user profile role to admin
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      role: "student",
      displayName: "Alice",
      email: "alice@test.com"
    });
  });
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ role: "admin" }));
});

test("Security check: Student cannot forge attendance records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("attendance").doc("session_1_alice").set({
    branchId: "branch_main",
    sessionId: "session_1",
    studentId: "student_alice",
    studentUserId: ALICE_UID,
    status: "present"
  }));
});

test("Security check: Student cannot tamper with invoice status directly", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("feeInvoices").doc("inv_alice_tamper").set({
      studentUserId: ALICE_UID,
      amountPaise: 250000,
      paidPaise: 0,
      status: "unpaid"
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Attempt to directly set status to 'paid' in feeInvoices
  await assertFails(aliceDb.collection("feeInvoices").doc("inv_alice_tamper").update({
    status: "paid",
    paidPaise: 250000
  }));
});

test("Security check: Student cannot read another student's certificates", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("certificates").doc("cert_bob").set({
      studentUserId: BOB_UID,
      title: "Black Belt 1st Dan Certificate",
      grade: "1st Dan",
      issuedAt: "2026-10-01"
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("certificates").doc("cert_bob").get());
});

