const mongoose = require("mongoose");

// Mirrors data/model/Chat.kt. _id is a plain string (client-generated UUID
// on migration, server-generated ObjectId-as-string for new chats) so it
// slots into the same "chatId" concept the Android app already uses.
const chatSchema = new mongoose.Schema(
  {
    _id: { type: String, required: true },
    participantIds: { type: [String], required: true },
    participantNames: { type: Map, of: String, default: {} },
    // uid -> last-typed-at epoch millis (0 = not typing). Ephemeral by
    // nature — fine to keep on the chat doc rather than a separate store.
    typing: { type: Map, of: Number, default: {} },
    createdAt: { type: Number, required: true }
  },
  { versionKey: false, _id: false }
);

module.exports = mongoose.model("Chat", chatSchema);
