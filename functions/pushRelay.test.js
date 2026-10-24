"use strict";

const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { processNotificationCreated } = require("./pushRelay");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ADMIN_UID = "admin_global_uid";
const BRANCH_ADMIN_MAIN_UID = "admin_branch_main_uid";
const COACH_MAIN_UID = "coach_main_uid";
const STUDENT_MAIN_UID = "student_main_uid";
const STUDENT_WEST_UID = "student_west_uid";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function createMockMessaging(customBehavior) {
  const sentMessages = [];
  let callCount = 0;

  return {
    sentMessages,
    getCallCount: () => callCount,
    sendEachForMulticast: async (multicastMessage) => {
      callCount++;
      sentMessages.push(multicastMessage);
      if (customBehavior) {
        return customBehavior(multicastMessage, callCount);
      }
      return {
        successCount: multicastMessage.tokens.length,
        failureCount: 0,
        responses: multicastMessage.tokens.map(() => ({
          success: true,
          messageId: "msg_mock_ok",
        })),
      };
    },
  };
}

before(async () => {
  const rulesPath = path.resolve(__dirname, "../firestore.rules");
  const rules = fs.readFileSync(rulesPath, "utf8");
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

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    // Global Admin
    await db.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "Mahi27d@gmail.com",
      role: "admin",
      scope: "all",
      active: true,
    });
    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      email: "Mahi27d@gmail.com",
      role: "admin",
      status: "active",
      active: true,
    });

    // Branch-scoped Admin (branch_main only)
    await db.collection("admins").doc(BRANCH_ADMIN_MAIN_UID).set({
      userId: BRANCH_ADMIN_MAIN_UID,
      email: "admin_main@truefighters.in",
      role: "admin",
      scope: "branch",
      branchIds: ["branch_main"],
      active: true,
    });

    // Coach assigned to branch_main
    await db.collection("users").doc(COACH_MAIN_UID).set({
      userId: COACH_MAIN_UID,
      displayName: "Sensei Vikram",
      role: "coach",
      status: "active",
      active: true,
      branchIds: ["branch_main"],
    });

    // Active Student in branch_main
    await db.collection("users").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      displayName: "Rohan Sharma",
      role: "student",
      status: "active",
      active: true,
      branchIds: ["branch_main"],
      preferredBranchId: "branch_main",
    });

    // Active Student in branch_west
    await db.collection("users").doc(STUDENT_WEST_UID).set({
      userId: STUDENT_WEST_UID,
      displayName: "Aarav Verma",
      role: "student",
      status: "active",
      active: true,
      branchIds: ["branch_west"],
      preferredBranchId: "branch_west",
    });
  });
});

test("1. Authorized delivery & PII/financial stripping: sends minimal deep-link payload", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    await db.collection("notificationPreferences").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      feeDueRemindersEnabled: true,
      fcmTokens: ["fcm_valid_token_1", "demo_ignored_token"],
    });

    const notifData = {
      notificationId: "notif_auth_1",
      recipientUserId: STUDENT_MAIN_UID,
      recipientRole: "student",
      branchId: "branch_main",
      type: "fee_reminder",
      channelId: "fees",
      title: "October Fee Invoice ₹2,500.00 for +919876543210",
      body: "Please pay ₹2,500.00 (250000 paise) or contact rohan@truefighters.in / +919876543210",
      data: {
        invoiceId: "inv_oct_2026",
        phoneNumber: "+919876543210",
        guardianName: "Rajesh Sharma",
        guardianPhone: "+919876500000",
        amountPaise: "250000",
      },
      createdByUid: ADMIN_UID,
      createdByEmail: "Mahi27d@gmail.com",
      dedupKey: "inv_oct_2026_reminder",
      deliveryStatus: "pending",
    };

    await db.collection("notifications").doc("notif_auth_1").set(notifData);

    const messaging = createMockMessaging();
    const res = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_auth_1",
      notificationData: notifData,
    });

    assert.equal(res.status, "sent");
    assert.equal(res.sentCount, 1);
    assert.equal(messaging.getCallCount(), 1);

    const sent = messaging.sentMessages[0];
    // Demo token must be filtered out
    assert.deepEqual(sent.tokens, ["fcm_valid_token_1"]);

    // Verify PII & currency amounts were stripped from title, body, and data
    assert.equal(sent.notification.title.includes("2,500"), false);
    assert.equal(sent.notification.title.includes("9876543210"), false);
    assert.equal(sent.notification.body.includes("250000"), false);
    assert.equal(sent.notification.body.includes("rohan@truefighters.in"), false);

    // Data payload contains only minimal deep-link routing fields
    assert.deepEqual(sent.data, {
      notificationId: "notif_auth_1",
      type: "fee_reminder",
      channelId: "fees",
      targetScreen: "fees",
      branchId: "branch_main",
      invoiceId: "inv_oct_2026",
    });
    assert.equal("phoneNumber" in sent.data, false);
    assert.equal("guardianPhone" in sent.data, false);
    assert.equal("amountPaise" in sent.data, false);
  });
});

test("2. Unauthorized injection & role/branch enforcement: blocks student, cross-branch coach, and unpermitted coach event types", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const messaging = createMockMessaging();

    await db.collection("notificationPreferences").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      fcmTokens: ["fcm_valid_token_1"],
    });

    // Case A: Student attempts to inject a notification document
    const studentInjected = {
      notificationId: "notif_student_inject",
      recipientUserId: STUDENT_MAIN_UID,
      branchId: "branch_main",
      type: "belt_promotion",
      title: "Fake Black Belt Promotion",
      body: "Injected by student",
      createdByUid: STUDENT_MAIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_student_inject").set(studentInjected);

    const resA = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_student_inject",
      notificationData: studentInjected,
    });
    assert.equal(resA.status, "rejected_unauthorized");
    assert.equal(resA.reason, "unauthorized_creator_role");

    // Case B: Coach attempts to send a payment_receipt notification (admin-only type)
    const coachFeeAttempt = {
      notificationId: "notif_coach_fee",
      recipientUserId: STUDENT_MAIN_UID,
      branchId: "branch_main",
      type: "payment_receipt",
      title: "Fake Payment Receipt",
      body: "Coach trying to issue payment receipt",
      createdByUid: COACH_MAIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_coach_fee").set(coachFeeAttempt);

    const resB = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_coach_fee",
      notificationData: coachFeeAttempt,
    });
    assert.equal(resB.status, "rejected_unauthorized");
    assert.equal(resB.reason, "coach_not_permitted_for_type:payment_receipt");

    // Case C: Coach from branch_main attempts to send class_cancelled for branch_west
    const coachWrongBranch = {
      notificationId: "notif_coach_wrong_branch",
      recipientUserId: STUDENT_WEST_UID,
      branchId: "branch_west",
      type: "class_cancelled",
      title: "West Class Cancelled",
      body: "Cross-branch coach alert",
      createdByUid: COACH_MAIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_coach_wrong_branch").set(coachWrongBranch);

    const resC = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_coach_wrong_branch",
      notificationData: coachWrongBranch,
    });
    assert.equal(resC.status, "rejected_unauthorized");
    assert.equal(resC.reason, "coach_outside_assigned_branch");

    // Case D: Branch-scoped admin for branch_main attempts to send notification for branch_west
    const branchAdminWrongBranch = {
      notificationId: "notif_badmin_wrong_branch",
      recipientUserId: STUDENT_WEST_UID,
      branchId: "branch_west",
      type: "fee_reminder",
      title: "West Fee Reminder",
      body: "Cross-branch admin alert",
      createdByUid: BRANCH_ADMIN_MAIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_badmin_wrong_branch").set(branchAdminWrongBranch);

    const resD = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_badmin_wrong_branch",
      notificationData: branchAdminWrongBranch,
    });
    assert.equal(resD.status, "rejected_unauthorized");
    assert.equal(resD.reason, "branch_admin_outside_assigned_branch");

    assert.equal(messaging.getCallCount(), 0);
  });
});

test("3. Opted-out categories: skips push delivery when recipient disabled category preference", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const messaging = createMockMessaging();

    await db.collection("notificationPreferences").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      classRemindersEnabled: true,
      feeDueRemindersEnabled: false, // Opted out of fee alerts!
      gradingAndBeltsEnabled: true,
      announcementsEnabled: false, // Opted out of announcements!
      fcmTokens: ["fcm_token_active"],
    });

    const feeNotif = {
      notificationId: "notif_opted_out_fee",
      recipientUserId: STUDENT_MAIN_UID,
      branchId: "branch_main",
      type: "fee_reminder",
      channelId: "fees",
      title: "Fee Due",
      body: "Fee reminder",
      createdByUid: ADMIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_opted_out_fee").set(feeNotif);

    const res = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_opted_out_fee",
      notificationData: feeNotif,
    });

    assert.equal(res.status, "skipped_opted_out");
    assert.equal(res.preferenceKey, "feeDueRemindersEnabled");
    assert.equal(messaging.getCallCount(), 0);
  });
});

test("4. Idempotency & duplicate prevention: blocks duplicate triggers and duplicate dedupKey events", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const messaging = createMockMessaging();

    await db.collection("notificationPreferences").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      classRemindersEnabled: true,
      fcmTokens: ["fcm_token_student_1"],
    });

    const notif1 = {
      notificationId: "notif_session_1",
      recipientUserId: STUDENT_MAIN_UID,
      branchId: "branch_main",
      type: "class_rescheduled",
      channelId: "classes",
      title: "Morning Warriors Rescheduled",
      body: "Moved to 7:00 AM",
      createdByUid: COACH_MAIN_UID,
      dedupKey: "session_resched_101",
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_session_1").set(notif1);

    // First delivery succeeds
    const firstRes = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_session_1",
      notificationData: notif1,
    });
    assert.equal(firstRes.status, "sent");
    assert.equal(messaging.getCallCount(), 1);

    // Re-invoking on the same notificationId is a no-op (prevents duplicate/recursive delivery)
    const repeatSameDoc = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_session_1",
      notificationData: notif1,
    });
    assert.equal(repeatSameDoc.status, "skipped_already_processed");
    assert.equal(messaging.getCallCount(), 1);

    // Creating a second notification document with the same dedupKey is skipped
    const notif2DuplicateKey = {
      ...notif1,
      notificationId: "notif_session_2_dup",
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_session_2_dup").set(notif2DuplicateKey);

    const dupKeyRes = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_session_2_dup",
      notificationData: notif2DuplicateKey,
    });
    assert.equal(dupKeyRes.status, "skipped_duplicate");
    assert.equal(dupKeyRes.duplicateOfNotificationId, "notif_session_1");
    assert.equal(messaging.getCallCount(), 1);
  });
});

test("5. Invalid token cleanup & transient retry: retries transient failure and prunes unregistered tokens", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();

    await db.collection("notificationPreferences").doc(STUDENT_MAIN_UID).set({
      userId: STUDENT_MAIN_UID,
      gradingAndBeltsEnabled: true,
      fcmTokens: ["valid_token_keep", "stale_token_unregistered", "invalid_token_format"],
    });

    const promoNotif = {
      notificationId: "notif_promo_cleanup",
      recipientUserId: STUDENT_MAIN_UID,
      branchId: "branch_main",
      type: "belt_promotion",
      channelId: "gradings",
      title: "Yellow Belt Promotion Approved",
      body: "Congratulations on your promotion!",
      data: { assessmentId: "assess_99", certificateId: "cert_99" },
      createdByUid: ADMIN_UID,
      deliveryStatus: "pending",
    };
    await db.collection("notifications").doc("notif_promo_cleanup").set(promoNotif);

    // Simulate 1st attempt failing with transient server error, 2nd attempt succeeding for 1 token and returning invalid-token errors for 2 tokens
    const messaging = createMockMessaging((msg, attempt) => {
      if (attempt === 1) {
        const transientErr = new Error("FCM temporarily unavailable");
        transientErr.code = "messaging/server-unavailable";
        throw transientErr;
      }
      return {
        successCount: 1,
        failureCount: 2,
        responses: [
          { success: true, messageId: "msg_1" },
          {
            success: false,
            error: { code: "messaging/registration-token-not-registered" },
          },
          {
            success: false,
            error: { code: "messaging/invalid-registration-token" },
          },
        ],
      };
    });

    const res = await processNotificationCreated({
      db,
      messaging,
      notificationId: "notif_promo_cleanup",
      notificationData: promoNotif,
    });

    assert.equal(res.status, "sent");
    assert.equal(res.attempts, 2);
    assert.equal(res.sentCount, 1);
    assert.equal(res.failureCount, 2);
    assert.deepEqual(res.invalidTokensRemoved, [
      "stale_token_unregistered",
      "invalid_token_format",
    ]);

    // Verify stale tokens were removed from Firestore /notificationPreferences/{userId}
    const updatedPrefsSnap = await db
      .collection("notificationPreferences")
      .doc(STUDENT_MAIN_UID)
      .get();
    const remainingTokens = updatedPrefsSnap.data().fcmTokens;
    assert.deepEqual(remainingTokens, ["valid_token_keep"]);
  });
});
