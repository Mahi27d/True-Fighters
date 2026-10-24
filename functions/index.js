"use strict";

/**
 * Firebase Cloud Functions v2 entry point for True Fighters Martial Arts Academy.
 *
 * Triggered exclusively on document creation in `/notifications/{notificationId}`.
 * Uses the Firebase Admin SDK default service account on Google Cloud (no embedded keys).
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const { processNotificationCreated } = require("./pushRelay");

const DATABASE_ID = "ai-studio-android-truefigh-0d444016-a20d-4c16-a6bc-19d38e7bb9a0";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

exports.relayNotificationOnCreate = onDocumentCreated(
  {
    document: "notifications/{notificationId}",
    database: DATABASE_ID,
    region: "asia-southeast1",
    retry: false,
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      logger.warn("No snapshot data in onDocumentCreated event.");
      return null;
    }

    const notificationId = event.params.notificationId;
    const notificationData = snapshot.data() || {};
    const db = getFirestore(DATABASE_ID);
    const messaging = admin.messaging();

    try {
      const result = await processNotificationCreated({
        db,
        messaging,
        notificationId,
        notificationData,
      });
      logger.info("Processed notification relay", {
        notificationId,
        status: result.status,
        sentCount: result.sentCount || 0,
        invalidTokensRemoved: (result.invalidTokensRemoved || []).length,
      });
      return result;
    } catch (error) {
      logger.error("Failed to relay notification", {
        notificationId,
        error: error.message,
      });
      throw error;
    }
  }
);
