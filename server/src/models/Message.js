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
    exitedAtAfterRead: { type: Map, of: Number, default: {} }
  },
  { versionKey: false, _id: false }
);

module.exports = mongoose.model("Message", messageSchema);
