const express = require("express");
const User = require("../models/User");
const Chat = require("../models/Chat");
const { requireAuth } = require("../middleware/auth");
const { asyncHandler } = require("../asyncHandler");

const router = express.Router();
router.use(requireAuth);

// POST /api/users/sync — called once after every sign-in/sign-up (see
// AuthRepository on the Android side). Creates the Mongo profile doc on
// first sign-in; on later sign-ins, only backfills displayName if it was
// ever left blank — an explicit "Edit name" save is the only thing allowed
// to overwrite an existing name.
router.post(
  "/sync",
  asyncHandler(async (req, res) => {
    const displayName = (req.body.displayName || "").trim();
    let user = await User.findById(req.userId);
    if (!user) {
      user = await User.create({
        _id: req.userId,
        email: req.userEmail,
        displayName: displayName || req.userEmail,
        createdAt: Date.now()
      });
    } else if (!user.displayName && displayName) {
      user.displayName = displayName;
      await user.save();
    }
    res.json({ id: user._id, email: user.email, displayName: user.displayName });
  })
);

// GET /api/users/me — used to prefill the "Edit name" screen.
router.get(
  "/me",
  asyncHandler(async (req, res) => {
    const user = await User.findById(req.userId);
    if (!user) return res.status(404).json({ error: "User not found" });
    res.json({ id: user._id, email: user.email, displayName: user.displayName });
  })
);

// PATCH /api/users/me { displayName } — the actual "Edit name" save. Also
// propagates the new name into every chat's denormalized participantNames
// map (previously a separate /api/chats/participant-name endpoint) so the
// other participant's client picks it up without re-running "start a chat".
router.patch(
  "/me",
  asyncHandler(async (req, res) => {
    const displayName = (req.body.displayName || "").trim();
    if (!displayName) {
      return res.status(400).json({ error: "Name can't be empty" });
    }

    await User.updateOne({ _id: req.userId }, { $set: { displayName } }, { upsert: false });

    const chats = await Chat.find({ participantIds: req.userId });
    const io = req.app.get("io");
    for (const chat of chats) {
      chat.participantNames.set(req.userId, displayName);
      await chat.save();
      io.to(`chat:${chat._id}`).emit("chat:updated", {
        id: chat._id,
        participantIds: chat.participantIds,
        participantNames: Object.fromEntries(chat.participantNames),
        createdAt: chat.createdAt
      });
    }
    res.json({ ok: true, updatedChats: chats.length });
  })
);

// POST /api/users/me/fcm-token { token } — registers/overwrites this user's
// FCM registration token, called on every app start and token refresh (see
// PumpkinMessagingService.onNewToken).
router.post(
  "/me/fcm-token",
  asyncHandler(async (req, res) => {
    const token = (req.body.token || "").trim();
    if (!token) {
      return res.status(400).json({ error: "Missing token" });
    }
    await User.updateOne({ _id: req.userId }, { $set: { fcmToken: token } }, { upsert: false });
    res.json({ ok: true });
  })
);

module.exports = { router };
