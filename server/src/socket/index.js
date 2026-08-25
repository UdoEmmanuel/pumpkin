const { randomUUID } = require("crypto");
const Chat = require("../models/Chat");
const Message = require("../models/Message");
const User = require("../models/User");
const { verifyIdToken, sendDataMessage } = require("../firebaseAdmin");
const { isEligibleForAutoDelete } = require("../messageEligibility");
const { toMessageJson, chatRoomFor } = require("../routes/chats");
const presence = require("../presence");

function chatMessageRoom(chatId) {
  return `chat:${chatId}`;
}

// Shared by message:send and message:react — always sent regardless of the
// recipient's live socket status (a foregrounded recipient's client just
// gets a harmless duplicate signal alongside the socket event it already
// received). Fire-and-forget: never let a push failure affect the calling
// handler's already-sent response.
async function pushToOtherParticipant(chatId, actingUserId, type) {
  const chat = await Chat.findById(chatId);
  const recipientId = chat?.participantIds.find((id) => id !== actingUserId);
  if (!recipientId) return;
  const recipient = await User.findById(recipientId);
  if (recipient?.fcmToken) {
    sendDataMessage(recipient.fcmToken, { type, chatId });
  }
}

function attachSocketHandlers(io) {
  io.use(async (socket, next) => {
    try {
      const decoded = await verifyIdToken(socket.handshake.auth?.token);
      socket.userId = decoded.uid;
      next();
    } catch (e) {
      next(new Error("unauthorized"));
    }
  });

  io.on("connection", async (socket) => {
    socket.join(chatRoomFor(socket.userId));

    // Auto-join every chat this user is already part of, so message events
    // reach them without a separate explicit join per chat screen visit.
    const chats = await Chat.find({ participantIds: socket.userId });
    chats.forEach((chat) => socket.join(chatMessageRoom(chat._id)));

    presence.onConnect(socket.userId);
    chats.forEach((chat) => {
      io.to(chatMessageRoom(chat._id)).emit("presence:update", {
        userId: socket.userId,
        online: true,
        lastSeenAt: null
      });
    });

    socket.on("disconnect", async () => {
      const wentOffline = presence.onDisconnect(socket.userId);
      if (!wentOffline) return;
      const theirChats = await Chat.find({ participantIds: socket.userId });
      const lastSeenAt = presence.get(socket.userId).lastSeenAt;
      theirChats.forEach((chat) => {
        io.to(chatMessageRoom(chat._id)).emit("presence:update", {
          userId: socket.userId,
          online: false,
          lastSeenAt
        });
      });
    });

    // One-shot fetch for when a chat screen opens and needs the partner's
    // CURRENT status immediately, before any live presence:update arrives.
    socket.on("presence:get", (userId, ack) => {
      ack?.(presence.get(userId));
    });

    socket.on("chat:join", (chatId) => socket.join(chatMessageRoom(chatId)));

    socket.on("message:send", async ({ chatId, text, replyToMessageId, replyToSenderId, replyToText }, ack) => {
      console.log(`[send] chat=${chatId} from=${socket.userId} text=${JSON.stringify(text)}`);
      try {
        const message = await Message.create({
          _id: randomUUID(),
          chatId,
          senderId: socket.userId,
          text,
          sentAt: Date.now(),
          replyToMessageId: replyToMessageId || null,
          replyToSenderId: replyToSenderId || null,
          replyToText: replyToText || null
        });
        io.to(chatMessageRoom(chatId)).emit("message:new", toMessageJson(message));
        ack?.({ ok: true, message: toMessageJson(message) });

        pushToOtherParticipant(chatId, socket.userId, "new_message");
      } catch (e) {
        ack?.({ ok: false, error: e.message });
      }
    });

    // Tap an emoji on a message bubble. One reaction per user per message —
    // the same emoji again clears it, a different one replaces it.
    socket.on("message:react", async ({ chatId, messageId, emoji }, ack) => {
      try {
        const existing = await Message.findById(messageId);
        if (!existing) return ack?.({ ok: false, error: "Message not found" });

        const current = existing.reactions.get(socket.userId);
        if (emoji && current !== emoji) {
          existing.reactions.set(socket.userId, emoji);
        } else {
          existing.reactions.delete(socket.userId);
        }
        await existing.save();

        io.to(chatMessageRoom(chatId)).emit("message:updated", toMessageJson(existing));
        ack?.({ ok: true, message: toMessageJson(existing) });

        pushToOtherParticipant(chatId, socket.userId, "reaction");
      } catch (e) {
        ack?.({ ok: false, error: e.message });
      }
    });

    // PRD 4.4: recipient's client marks a message read as soon as it's visible.
    socket.on("message:read", async ({ chatId, messageId }, ack) => {
      console.log(`[read] chat=${chatId} message=${messageId} by=${socket.userId}`);
      try {
        const message = await Message.findOneAndUpdate(
          { _id: messageId },
          { $set: { [`readAt.${socket.userId}`]: Date.now() } },
          { new: true }
        );
        if (message) {
          io.to(chatMessageRoom(chatId)).emit("message:updated", toMessageJson(message));
        }
        ack?.({ ok: true });
      } catch (e) {
        ack?.({ ok: false, error: e.message });
      }
    });

    // PRD 4.4: fires once, when the recipient's chat screen truly closes
    // after having read the message — completes the "read AND exited"
    // condition and triggers the eligibility check for auto-delete.
    socket.on("message:exit", async ({ chatId, messageId }, ack) => {
      console.log(`[exit] chat=${chatId} message=${messageId} by=${socket.userId}`);
      try {
        const message = await Message.findOneAndUpdate(
          { _id: messageId },
          { $set: { [`exitedAtAfterRead.${socket.userId}`]: Date.now() } },
          { new: true }
        );
        if (!message) {
          console.log(`[exit]   message ${messageId} not found (already deleted?)`);
          return ack?.({ ok: true });
        }

        const chat = await Chat.findById(chatId);
        const eligible = chat ? isEligibleForAutoDelete(message, chat.participantIds) : false;
        console.log(
          `[exit]   chatFound=${!!chat} readAt=${JSON.stringify(Object.fromEntries(message.readAt))} ` +
            `exitedAt=${JSON.stringify(Object.fromEntries(message.exitedAtAfterRead))} eligible=${eligible}`
        );
        if (eligible) {
          await Message.deleteOne({ _id: messageId });
          console.log(`[exit]   DELETED message ${messageId}`);
          io.to(chatMessageRoom(chatId)).emit("message:deleted", { chatId, messageId });
        } else {
          io.to(chatMessageRoom(chatId)).emit("message:updated", toMessageJson(message));
        }
        ack?.({ ok: true });
      } catch (e) {
        ack?.({ ok: false, error: e.message });
      }
    });

    // Delivered marking: fired by the recipient's client the moment a new
    // message arrives via 'message:new' (see the Android client changes).
    socket.on("message:delivered", async ({ chatId, messageId }, ack) => {
      try {
        const message = await Message.findOneAndUpdate(
          { _id: messageId, deliveredAt: null },
          { $set: { deliveredAt: Date.now() } },
          { new: true }
        );
        if (message) {
          io.to(chatMessageRoom(chatId)).emit("message:updated", toMessageJson(message));
        }
        ack?.({ ok: true });
      } catch (e) {
        ack?.({ ok: false, error: e.message });
      }
    });

    socket.on("typing", async ({ chatId, isTyping }) => {
      try {
        await Chat.updateOne(
          { _id: chatId },
          { $set: { [`typing.${socket.userId}`]: isTyping ? Date.now() : 0 } }
        );
        socket.to(chatMessageRoom(chatId)).emit("typing:update", {
          chatId,
          userId: socket.userId,
          isTyping
        });
      } catch (e) {
        // Best-effort — a missed typing update isn't worth an error round-trip.
      }
    });
  });
}

module.exports = { attachSocketHandlers };
