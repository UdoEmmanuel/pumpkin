const mongoose = require("mongoose");

// Replaces the Firestore `users` collection (PRD 7 User model). Firebase
// Auth is still the identity provider (sign-in/token issuance) — this is
// purely profile data (display name, email for the "start a chat" lookup),
// moved here because Firestore's Spark-plan write quota kept getting
// exhausted during testing even at this collection's low volume.
const userSchema = new mongoose.Schema(
  {
    _id: { type: String, required: true }, // Firebase uid
    email: { type: String, required: true, index: true },
    displayName: { type: String, default: "" },
    createdAt: { type: Number, required: true }
  },
  { versionKey: false, _id: false }
);

module.exports = mongoose.model("User", userSchema);
