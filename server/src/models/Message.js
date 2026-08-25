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
    reactions: { type: Map, of: String, default: {} }
  },
  { versionKey: false, _id: false }
);

module.exports = mongoose.model("Message", messageSchema);
