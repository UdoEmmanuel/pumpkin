const express = require("express");
const { randomUUID } = require("crypto");
const Chat = require("../models/Chat");
const Message = require("../models/Message");
const User = require("../models/User");
const { requireAuth } = require("../middleware/auth");
const { asyncHandler } = require("../asyncHandler");

const router = express.Router();
router.use(requireAuth);

// GET /api/chats — every chat this user is a participant in.
router.get(
  "/",
  asyncHandler(async (req, res) => {
    const chats = await Chat.find({ participantIds: req.userId }).sort({ createdAt: -1 });
    res.json(chats.map(toChatJson));
  })
);

// GET /api/chats/:chatId/messages — full history for one chat.
router.get(
  "/:chatId/messages",
  asyncHandler(async (req, res) => {
    const messages = await Message.find({ chatId: req.params.chatId }).sort({ sentAt: 1 });
    res.json(messages.map(toMessageJson));
  })
);

// POST /api/chats/start { partnerEmail } — PRD 3 pairing step. Reuses an
// existing 1:1 chat with that partner if one exists, otherwise creates one.
router.post(
  "/start",
  asyncHandler(async (req, res) => {
    const partnerEmail = (req.body.partnerEmail || "").trim();
    if (!partnerEmail) {
      return res.status(400).json({ error: "Enter an email address" });
    }

    const partner = await User.findOne({ email: partnerEmail });
    if (!partner) {
      return res.status(404).json({ error: "No Pumpkin user found for that email" });
    }
    if (partner._id === req.userId) {
      return res.status(400).json({ error: "That's your own email" });
    }

    const existing = await Chat.findOne({
      participantIds: { $all: [req.userId, partner._id] }
    });
    if (existing) {
      return res.json(toChatJson(existing));
    }

    const currentUser = await User.findById(req.userId);
    const chat = await Chat.create({
      _id: randomUUID(),
      participantIds: [req.userId, partner._id],
      participantNames: {
        [partner._id]: partner.displayName || partnerEmail,
        [req.userId]: currentUser?.displayName || currentUser?.email || ""
      },
      createdAt: Date.now()
    });

    req.app.get("io").to(chatRoomFor(partner._id)).emit("chat:new", toChatJson(chat));
    res.status(201).json(toChatJson(chat));
  })
);

// DELETE /api/chats/:chatId — removes the chat and all its messages for
// BOTH participants. There's no per-user "delete for me" concept here (the
// same tradeoff as the rest of this app's ephemeral-by-design messaging) —
// deleting a conversation deletes it, full stop, and notifies whichever
// participant didn't initiate it so their client drops it too.
router.delete(
  "/:chatId",
  asyncHandler(async (req, res) => {
    const chat = await Chat.findById(req.params.chatId);
    if (!chat) {
      return res.status(404).json({ error: "Chat not found" });
    }
    if (!chat.participantIds.includes(req.userId)) {
      return res.status(403).json({ error: "Not a participant in this chat" });
    }

    await Message.deleteMany({ chatId: chat._id });
    await Chat.deleteOne({ _id: chat._id });

    const io = req.app.get("io");
    chat.participantIds.forEach((uid) => {
      io.to(chatRoomFor(uid)).emit("chat:deleted", { chatId: chat._id });
    });

    res.json({ ok: true });
  })
);

// PATCH /api/chats/:chatId/nickname { targetUserId, nickname } — sets (or,
// with an empty nickname, clears) what the OTHER participant is called
// within this one chat. Visible to both people in the chat (it's stored on
// the shared chat doc, same as participantNames), but never touches
// targetUserId's actual User.displayName — a nickname set here has no
// effect on any other conversation that user is part of.
router.patch(
  "/:chatId/nickname",
  asyncHandler(async (req, res) => {
    const chat = await Chat.findById(req.params.chatId);
    if (!chat) {
      return res.status(404).json({ error: "Chat not found" });
    }
    if (!chat.participantIds.includes(req.userId)) {
      return res.status(403).json({ error: "Not a participant in this chat" });
    }
    const targetUserId = req.body.targetUserId;
    if (!chat.participantIds.includes(targetUserId)) {
      return res.status(400).json({ error: "Not a participant in this chat" });
    }

    const nickname = (req.body.nickname || "").trim();
    if (nickname) {
      chat.nicknames.set(targetUserId, nickname);
    } else {
      chat.nicknames.delete(targetUserId);
    }
    await chat.save();

    const io = req.app.get("io");
    chat.participantIds.forEach((uid) => {
      io.to(chatRoomFor(uid)).emit("chat:updated", toChatJson(chat));
    });
    res.json(toChatJson(chat));
  })
);

function toChatJson(chat) {
  return {
    id: chat._id,
    participantIds: chat.participantIds,
    participantNames: Object.fromEntries(chat.participantNames || new Map()),
    nicknames: Object.fromEntries(chat.nicknames || new Map()),
    createdAt: chat.createdAt
  };
}

function toMessageJson(message) {
  return {
    id: message._id,
    chatId: message.chatId,
    senderId: message.senderId,
    text: message.text,
    sentAt: message.sentAt,
    deliveredAt: message.deliveredAt,
    readAt: Object.fromEntries(message.readAt || new Map()),
    exitedAtAfterRead: Object.fromEntries(message.exitedAtAfterRead || new Map()),
    replyToMessageId: message.replyToMessageId || null,
    replyToSenderId: message.replyToSenderId || null,
    replyToText: message.replyToText || null,
    reactions: Object.fromEntries(message.reactions || new Map()),
    editedAt: message.editedAt || null,
    type: message.type || "text",
    audioData: message.audioData || null,
    audioDurationMs: message.audioDurationMs || null
  };
}

// A per-user room ("user:<uid>") that every one of a user's connected
// sockets joins — lets the server push "a new chat exists" to someone who
// wasn't in any specific chat room yet.
function chatRoomFor(userId) {
  return `user:${userId}`;
}

module.exports = { router, toChatJson, toMessageJson, chatRoomFor };
