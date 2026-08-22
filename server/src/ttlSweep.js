const cron = require("node-cron");
const Message = require("./models/Message");

const TTL_DAYS = 14;

// PRD 4.4 edge case: "One participant may never return to trigger their
// 'exit' event... fallback TTL (e.g. auto-purge after 14 days regardless of
// read/exit state) so messages don't accumulate indefinitely server-side."
function startTtlSweep(io) {
  cron.schedule("0 3 * * *", async () => {
    const cutoff = Date.now() - TTL_DAYS * 24 * 60 * 60 * 1000;
    const stale = await Message.find({ sentAt: { $lt: cutoff } });
    for (const message of stale) {
      await Message.deleteOne({ _id: message._id });
      io.to(`chat:${message.chatId}`).emit("message:deleted", {
        chatId: message.chatId,
        messageId: message._id
      });
    }
  });
}

module.exports = { startTtlSweep };
