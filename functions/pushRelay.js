"use strict";

/**
 * Pure, testable server-side Firebase Cloud Function push relay logic for
 * True Fighters Martial Arts Academy.
 *
 * Enforces:
 * 1. Source, role, and branch authorization verification (never trusts a
 *    client-created /notifications/{notificationId} document blindly).
 * 2. Recipient eligibility, active status, and branch scope verification.
 * 3. Category opt-in preference checks (/notificationPreferences/{userId}).
 * 4. Idempotent delivery (via deliveryStatus and dedupKey) and non-recursive
 *    document status updates.
 * 5. Strict PII, guardian, and financial data stripping from push titles,
 *    bodies, and data payloads, sending only minimal deep-link routing fields.
 * 6. Safe transient retry handling and automatic removal of invalid/unregistered
 *    FCM tokens from /notificationPreferences/{userId}.
 */

const BOOTSTRAP_ADMIN_EMAIL = "Mahi27d@gmail.com";
const BOOTSTRAP_ADMIN_PHONE = "+919876500001";

const COACH_PERMITTED_TYPES = new Set([
  "schedule",
  "attendance",
  "grading",
  "class_reminder",
  "class_rescheduled",
  "class_cancelled",
  "attendance_alert",
  "grading_submitted",
  "announcement",
]);

const ADMIN_PERMITTED_TYPES = new Set([
  ...COACH_PERMITTED_TYPES,
  "general",
  "admission",
  "admission_request",
  "admission_approved",
  "admission_rejected",
  "fee",
  "fee_reminder",
  "overdue_fee",
  "payment_receipt",
  "belt_promotion",
  "certificate_issued",
]);

const TERMINAL_DELIVERY_STATUSES = new Set([
  "sent",
  "processing",
  "skipped_duplicate",
  "rejected_unauthorized",
  "skipped_opted_out",
  "skipped_ineligible_recipient",
  "no_valid_tokens",
]);

const INVALID_TOKEN_ERROR_CODES = new Set([
  "messaging/invalid-registration-token",
  "messaging/registration-token-not-registered",
  "messaging/invalid-argument",
]);

const TRANSIENT_FCM_ERROR_CODES = new Set([
  "messaging/internal-error",
  "messaging/server-unavailable",
  "messaging/Unavailable",
]);

const ALLOWED_DEEP_LINK_ENTITY_KEYS = [
  "requestId",
  "sessionId",
  "invoiceId",
  "paymentId",
  "assessmentId",
  "certificateId",
];

/**
 * Maps notification type / channelId to the user preference field and in-app target screen.
 */
function resolveCategoryMetadata(type = "", channelId = "") {
  const normType = String(type || "").toLowerCase().trim();
  const normChannel = String(channelId || "").toLowerCase().trim();

  if (
    normChannel === "admissions" ||
    normType.startsWith("admission")
  ) {
    return {
      preferenceKey: "admissionsAlertsEnabled",
      channelId: "admissions",
      targetScreen: "admissions",
    };
  }

  if (
    normChannel === "fees" ||
    normType === "fee" ||
    normType === "fee_reminder" ||
    normType === "overdue_fee" ||
    normType === "payment_receipt"
  ) {
    return {
      preferenceKey: "feeDueRemindersEnabled",
      channelId: "fees",
      targetScreen: "fees",
    };
  }

  if (
    normChannel === "gradings" ||
    normType === "grading" ||
    normType === "grading_submitted" ||
    normType === "belt_promotion" ||
    normType === "certificate_issued"
  ) {
    return {
      preferenceKey: "gradingAndBeltsEnabled",
      channelId: "gradings",
      targetScreen: normType === "certificate_issued" ? "certificates" : "gradings",
    };
  }

  if (
    normChannel === "classes" ||
    normType === "schedule" ||
    normType === "class_reminder" ||
    normType === "class_rescheduled" ||
    normType === "class_cancelled" ||
    normType === "attendance" ||
    normType === "attendance_alert"
  ) {
    return {
      preferenceKey: "classRemindersEnabled",
      channelId: "classes",
      targetScreen: normType.startsWith("attendance") ? "attendance" : "schedule",
    };
  }

  return {
    preferenceKey: "announcementsEnabled",
    channelId: "announcements",
    targetScreen: "announcements",
  };
}

/**
 * Scrubs phone numbers, email addresses, currency/paise values, and guardian PII
 * from push notification text so lock-screen banners never leak sensitive details.
 */
function sanitizeNotificationText(text = "", maxLen = 140) {
  if (!text || typeof text !== "string") return "";
  let cleaned = text
    // Strip Indian / international phone numbers (with or without +91 / spaces)
    .replace(/(?:\+?\d{1,3}[\s-]?)?\d{10}\b/g, "[redacted]")
    // Strip email addresses
    .replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, "[redacted]")
    // Strip rupee / currency amounts (e.g. ₹2,500.00, Rs. 2500, INR 2500, 250000 paise)
    .replace(/(?:₹|Rs\.?|INR)\s*[\d,]+(?:\.\d{1,2})?/gi, "your fee amount")
    .replace(/\b\d+\s*paise\b/gi, "your fee amount")
    .trim();

  if (cleaned.length > maxLen) {
    cleaned = cleaned.slice(0, maxLen - 3) + "...";
  }
  return cleaned;
}

/**
 * Builds the minimal data & notification payload for FCM without sensitive PII or financial data.
 */
function buildSanitizedPushPayload({
  notificationId,
  notificationData,
  categoryMeta,
}) {
  const rawData =
    notificationData && typeof notificationData.data === "object" && notificationData.data !== null
      ? notificationData.data
      : {};

  const safeData = {
    notificationId: String(notificationId || ""),
    type: String(notificationData.type || "general"),
    channelId: String(categoryMeta.channelId),
    targetScreen: String(categoryMeta.targetScreen),
    branchId: String(notificationData.branchId || ""),
  };

  for (const key of ALLOWED_DEEP_LINK_ENTITY_KEYS) {
    if (
      typeof rawData[key] === "string" &&
      rawData[key].trim().length > 0 &&
      rawData[key].trim().length <= 64
    ) {
      safeData[key] = rawData[key].trim();
    }
  }

  const safeTitle =
    sanitizeNotificationText(notificationData.title || "True Fighters Academy", 72) ||
    "True Fighters Academy";
  const safeBody =
    sanitizeNotificationText(
      notificationData.body || "Open the True Fighters app to view this update.",
      140
    ) || "Open the True Fighters app to view this update.";

  return {
    notification: {
      title: safeTitle,
      body: safeBody,
    },
    data: safeData,
    android: {
      priority: "high",
      notification: {
        channelId: categoryMeta.channelId,
      },
    },
  };
}

/**
 * Verifies that the creator of the notification document is an authenticated,
 * active Admin or Coach authorized for the notification's branchId and event type.
 */
async function verifyNotificationSourceAuthorization(db, notificationData) {
  const createdByUid = String(notificationData.createdByUid || "").trim();
  const createdByEmail = String(notificationData.createdByEmail || "").trim();
  const branchId = String(notificationData.branchId || "").trim();
  const eventType = String(notificationData.type || "").toLowerCase().trim();

  if (!createdByUid || createdByUid.startsWith("demo_")) {
    return {
      authorized: false,
      reason: "missing_or_demo_creator_uid",
    };
  }

  if (!eventType || !ADMIN_PERMITTED_TYPES.has(eventType)) {
    return {
      authorized: false,
      reason: `unpermitted_event_type:${eventType || "empty"}`,
    };
  }

  const [adminSnap, userSnap] = await Promise.all([
    db.collection("admins").doc(createdByUid).get(),
    db.collection("users").doc(createdByUid).get(),
  ]);

  const adminDoc = adminSnap.exists ? adminSnap.data() || {} : null;
  const userDoc = userSnap.exists ? userSnap.data() || {} : null;

  // Check if bootstrap admin
  const isBootstrap =
    createdByEmail === BOOTSTRAP_ADMIN_EMAIL ||
    (userDoc &&
      (userDoc.email === BOOTSTRAP_ADMIN_EMAIL ||
        userDoc.phoneNumber === BOOTSTRAP_ADMIN_PHONE));

  if (isBootstrap) {
    return { authorized: true, actorRole: "admin", reason: "bootstrap_admin" };
  }

  // Check if active Admin in /admins or /users
  const isAdminInAdmins = adminDoc && adminDoc.active !== false;
  const isAdminInUsers =
    userDoc &&
    String(userDoc.role || "").toLowerCase() === "admin" &&
    userDoc.active !== false &&
    !["suspended", "inactive", "rejected", "pending"].includes(
      String(userDoc.status || "active").toLowerCase()
    );

  if (isAdminInAdmins || isAdminInUsers) {
    const scope = String(
      (adminDoc && adminDoc.scope) || (userDoc && userDoc.scope) || "all"
    ).toLowerCase();
    const adminBranches = Array.isArray(adminDoc && adminDoc.branchIds)
      ? adminDoc.branchIds
      : Array.isArray(userDoc && userDoc.branchIds)
      ? userDoc.branchIds
      : [];

    if (scope === "branch" || adminBranches.length > 0) {
      if (!branchId || !adminBranches.includes(branchId)) {
        return {
          authorized: false,
          reason: "branch_admin_outside_assigned_branch",
        };
      }
    }
    return { authorized: true, actorRole: "admin", reason: "verified_admin" };
  }

  // Check if active Coach in /users
  const isActiveCoach =
    userDoc &&
    String(userDoc.role || "").toLowerCase() === "coach" &&
    userDoc.active !== false &&
    String(userDoc.status || "active").toLowerCase() === "active";

  if (isActiveCoach) {
    if (!COACH_PERMITTED_TYPES.has(eventType)) {
      return {
        authorized: false,
        reason: `coach_not_permitted_for_type:${eventType}`,
      };
    }
    const coachBranches = Array.isArray(userDoc.branchIds)
      ? userDoc.branchIds
      : [];
    if (!branchId || !coachBranches.includes(branchId)) {
      return {
        authorized: false,
        reason: "coach_outside_assigned_branch",
      };
    }
    return { authorized: true, actorRole: "coach", reason: "verified_coach" };
  }

  return {
    authorized: false,
    reason: "unauthorized_creator_role",
  };
}

/**
 * Validates that a target recipient user is eligible (not a demo user, active,
 * and within the notification's branch scope when branchId is set).
 */
async function validateRecipientEligibility(db, recipientUserId, branchId) {
  const uid = String(recipientUserId || "").trim();
  if (!uid || uid.startsWith("demo_")) {
    return { eligible: false, reason: "invalid_or_demo_recipient" };
  }

  const userSnap = await db.collection("users").doc(uid).get();
  if (!userSnap.exists) {
    return { eligible: false, reason: "recipient_user_not_found" };
  }

  const userData = userSnap.data() || {};
  const status = String(userData.status || "active").toLowerCase();
  if (
    userData.active === false ||
    ["suspended", "inactive", "rejected"].includes(status)
  ) {
    return { eligible: false, reason: `recipient_inactive_status:${status}` };
  }

  const targetBranch = String(branchId || "").trim();
  const role = String(userData.role || "student").toLowerCase();
  if (targetBranch && role !== "admin") {
    const userBranches = Array.isArray(userData.branchIds)
      ? userData.branchIds
      : [];
    const preferredBranch = String(userData.preferredBranchId || "").trim();
    const belongsToBranch =
      userBranches.includes(targetBranch) || preferredBranch === targetBranch;

    if (!belongsToBranch) {
      // Also check student record in /students if present
      const studentQuery = await db
        .collection("students")
        .where("userId", "==", uid)
        .limit(1)
        .get();
      const studentDoc = !studentQuery.empty ? studentQuery.docs[0].data() : null;
      if (!studentDoc || studentDoc.branchId !== targetBranch) {
        return { eligible: false, reason: "recipient_outside_notification_branch" };
      }
    }
  }

  return { eligible: true, userData };
}

/**
 * Sends multicast FCM payload with transient retry and returns response summary.
 */
async function sendMulticastWithRetry(messaging, multicastMessage, maxRetries = 1) {
  let attempt = 0;
  while (true) {
    attempt++;
    try {
      const response = await messaging.sendEachForMulticast(multicastMessage);
      return { response, attempts: attempt };
    } catch (err) {
      const code = String((err && err.code) || "");
      if (attempt <= maxRetries && TRANSIENT_FCM_ERROR_CODES.has(code)) {
        continue;
      }
      throw err;
    }
  }
}

/**
 * Main handler invoked when /notifications/{notificationId} is created.
 * Updates deliveryStatus on the notification document without re-triggering
 * (since the Cloud Function only subscribes to onDocumentCreated).
 */
async function processNotificationCreated({
  db,
  messaging,
  notificationId,
  notificationData,
  nowIso = () => new Date().toISOString(),
}) {
  if (!db || !messaging || !notificationId) {
    throw new Error("db, messaging, and notificationId are required.");
  }

  const notifRef = db.collection("notifications").doc(notificationId);
  const freshSnap = await notifRef.get();
  const docData = freshSnap.exists
    ? freshSnap.data() || {}
    : notificationData || {};

  // 1. Prevent duplicate execution or recursive processing
  const currentStatus = String(docData.deliveryStatus || "pending");
  if (TERMINAL_DELIVERY_STATUSES.has(currentStatus)) {
    return {
      status: "skipped_already_processed",
      previousStatus: currentStatus,
    };
  }

  // 2. Check dedupKey across already-sent notifications
  const dedupKey = String(docData.dedupKey || "").trim();
  if (dedupKey) {
    const dupQuery = await db
      .collection("notifications")
      .where("dedupKey", "==", dedupKey)
      .get();

    const alreadySentDoc = dupQuery.docs.find(
      (d) =>
        d.id !== notificationId &&
        String((d.data() || {}).deliveryStatus || "") === "sent"
    );
    if (alreadySentDoc) {
      await notifRef.set(
        {
          deliveryStatus: "skipped_duplicate",
          duplicateOfNotificationId: alreadySentDoc.id,
          processedAt: nowIso(),
        },
        { merge: true }
      );
      return {
        status: "skipped_duplicate",
        duplicateOfNotificationId: alreadySentDoc.id,
      };
    }
  }

  // 3. Verify creator source, role, permitted event type, and branch authorization
  const authCheck = await verifyNotificationSourceAuthorization(db, docData);
  if (!authCheck.authorized) {
    await notifRef.set(
      {
        deliveryStatus: "rejected_unauthorized",
        deliveryError: authCheck.reason,
        processedAt: nowIso(),
      },
      { merge: true }
    );
    return {
      status: "rejected_unauthorized",
      reason: authCheck.reason,
    };
  }

  // 4. Validate recipient eligibility & branch scope
  const recipientUserId = String(docData.recipientUserId || "").trim();
  const branchId = String(docData.branchId || "").trim();
  const eligibility = await validateRecipientEligibility(
    db,
    recipientUserId,
    branchId
  );

  if (!eligibility.eligible) {
    await notifRef.set(
      {
        deliveryStatus: "skipped_ineligible_recipient",
        deliveryError: eligibility.reason,
        processedAt: nowIso(),
      },
      { merge: true }
    );
    return {
      status: "skipped_ineligible_recipient",
      reason: eligibility.reason,
    };
  }

  // 5. Check recipient notification preferences & category opt-in
  const categoryMeta = resolveCategoryMetadata(docData.type, docData.channelId);
  const prefRef = db.collection("notificationPreferences").doc(recipientUserId);
  const prefSnap = await prefRef.get();
  const prefData = prefSnap.exists ? prefSnap.data() || {} : {};

  const isOptedIn = prefData[categoryMeta.preferenceKey] !== false;
  if (!isOptedIn) {
    await notifRef.set(
      {
        deliveryStatus: "skipped_opted_out",
        optedOutPreference: categoryMeta.preferenceKey,
        processedAt: nowIso(),
      },
      { merge: true }
    );
    return {
      status: "skipped_opted_out",
      preferenceKey: categoryMeta.preferenceKey,
    };
  }

  // 6. Read valid, non-demo FCM tokens
  const rawTokens = Array.isArray(prefData.fcmTokens) ? prefData.fcmTokens : [];
  const validTokens = Array.from(
    new Set(
      rawTokens
        .map((t) => String(t || "").trim())
        .filter((t) => t.length > 0 && !t.startsWith("demo_"))
    )
  );

  if (validTokens.length === 0) {
    await notifRef.set(
      {
        deliveryStatus: "no_valid_tokens",
        processedAt: nowIso(),
      },
      { merge: true }
    );
    return {
      status: "no_valid_tokens",
      sentCount: 0,
    };
  }

  // 7. Build PII-scrubbed, minimal deep-link push payload
  const sanitizedPayload = buildSanitizedPushPayload({
    notificationId,
    notificationData: docData,
    categoryMeta,
  });

  const multicastMessage = {
    tokens: validTokens,
    notification: sanitizedPayload.notification,
    data: sanitizedPayload.data,
    android: sanitizedPayload.android,
  };

  // 8. Dispatch via Firebase Admin Messaging & clean up invalid tokens
  const { response, attempts } = await sendMulticastWithRetry(
    messaging,
    multicastMessage,
    1
  );

  const invalidTokensToRemove = [];
  if (response && Array.isArray(response.responses)) {
    response.responses.forEach((res, idx) => {
      if (!res.success && res.error) {
        const errCode = String(res.error.code || "");
        if (INVALID_TOKEN_ERROR_CODES.has(errCode)) {
          invalidTokensToRemove.push(validTokens[idx]);
        }
      }
    });
  }

  if (invalidTokensToRemove.length > 0) {
    const remainingTokens = validTokens.filter(
      (t) => !invalidTokensToRemove.includes(t)
    );
    await prefRef.set(
      {
        fcmTokens: remainingTokens,
        updatedAt: nowIso(),
      },
      { merge: true }
    );
  }

  const successCount = Number((response && response.successCount) || 0);
  const failureCount = Number((response && response.failureCount) || 0);

  await notifRef.set(
    {
      deliveryStatus: "sent",
      sentCount: successCount,
      failureCount,
      deliveryAttempts: attempts,
      invalidTokensRemoved: invalidTokensToRemove,
      deliveredAt: nowIso(),
    },
    { merge: true }
  );

  return {
    status: "sent",
    sentCount: successCount,
    failureCount,
    attempts,
    invalidTokensRemoved: invalidTokensToRemove,
    payloadSent: sanitizedPayload,
  };
}

module.exports = {
  processNotificationCreated,
  verifyNotificationSourceAuthorization,
  validateRecipientEligibility,
  buildSanitizedPushPayload,
  sanitizeNotificationText,
  resolveCategoryMetadata,
};
