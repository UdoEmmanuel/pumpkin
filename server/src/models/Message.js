const mongoose = require("mongoose");

// Mirrors data/model/Message.kt exactly, including PRD 4.4's auto-delete
// tracking fields (readAt / exitedAtAfterRead as per-recipient maps).
const messageSchema = new mongoose.Schema(
  {
    _id: { type: String, required: true },
    chatId: { type: String, required: true, index: true },
    senderId: { type: String, required: true },
    text: { type: String, required: true },
    sentAt: { type: Number, required: true },
    deliveredAt: { type: Number, default: null },
    readAt: { type: Map, of: Number, default: {} },
    exitedAtAfterRead: { type: Map, of: Number, default: {} },
    // Swipe-to-reply. Snapshotted (not just an id reference) because the
    // original message can auto-delete out from under this one (PRD 4.4) —
    // the quote needs to keep making sense even after that happens.
    replyToMessageId: { type: String, default: null },
    replyToSenderId: { type: String, default: null },
    replyToText: { type: String, default: null },
    // uid -> emoji. One reaction per user per message (re-reacting replaces
    // it, same emoji again clears it) — see socket message:react.
    reactions: { type: Map, of: String, default: {} },
    // Set the moment the sender edits this message's text — non-null is
    // what the client uses to show an "edited" label. Only ever settable by
    // the original sender, and only while the message still exists (there's
    // nothing else to check: a deleted message can't be found to edit).
    editedAt: { type: Number, default: null },
    // Voice notes, stored inline (base64) on the message document itself —
    // no separate object storage, deliberately: clips are short (client
    // caps recording length) and this way a deleted/auto-deleted message
    // takes its audio with it automatically, for free, via the exact same
    // Message.deleteOne/deleteMany calls that already handle text messages.
    // No second delete path to keep in sync, no external storage bill.
    type: { type: String, enum: ["text", "voice"], default: "text" },
    audioData: { type: String, default: null },
    audioDurationMs: { type: Number, default: null }
  },
  { versionKey: false, _id: false }
);

module.exports = mongoose.model("Message", messageSchema);
