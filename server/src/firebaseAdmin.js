const admin = require("firebase-admin");
const path = require("path");

// Used for ONE thing only: verifying the ID token the Android app sends
// with every request (a local JWT signature check against Google's public
// certs — a Firebase Authentication operation, not a Firestore database
// one, so it's not subject to the Firestore write-quota problem this
// migration exists to get away from). User profile data (email,
// displayName) lives in MongoDB now (see models/User.js) — it used to be
// read from Firestore's `users` collection here, but that collection kept
// exhausting the same quota even at low volume.
const serviceAccountPath = path.resolve(
  process.env.FIREBASE_SERVICE_ACCOUNT_PATH || "./firebase-service-account.json"
);

admin.initializeApp({
  credential: admin.credential.cert(require(serviceAccountPath))
});

async function verifyIdToken(idToken) {
  return admin.auth().verifyIdToken(idToken);
}

/**
 * Silent/data-only push (PRD 4.3) — no `notification` block, so it never
 * shows a tray entry on its own. The client's FirebaseMessagingService
 * decides what to do with it (play a sound if backgrounded). Swallows send
 * failures (expired/invalid token, user never registered one, etc.) since a
 * missed background ping isn't worth failing the message-send flow over.
 */
async function sendDataMessage(token, data) {
  if (!token) return;
  try {
    await admin.messaging().send({
      token,
      data,
      android: { priority: "high" }
    });
  } catch (e) {
    console.error("[fcm] send failed:", e.message);
  }
}

module.exports = { verifyIdToken, sendDataMessage };
