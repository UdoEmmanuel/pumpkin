// PRD 4.4: "eligible once both of the following are true for both
// participants: message marked read, AND participant has exited the chat
// screen after reading it." Read literally, "both participants" includes the
// sender — a message must not disappear just because the recipient read and
// left while the sender is still sitting in the chat. "Read" only makes
// sense for recipients (you don't mark your own message read), so the rule
// splits into two parts: every recipient must have read AND exited, and
// EVERY participant — sender included — must have exited at least once.
function isEligibleForAutoDelete(message, participantIds) {
  const readAt = message.readAt instanceof Map ? message.readAt : new Map(Object.entries(message.readAt || {}));
  const exitedAt =
    message.exitedAtAfterRead instanceof Map
      ? message.exitedAtAfterRead
      : new Map(Object.entries(message.exitedAtAfterRead || {}));

  const recipients = participantIds.filter((id) => id !== message.senderId);
  const recipientsSatisfied =
    recipients.length > 0 && recipients.every((id) => readAt.has(id) && exitedAt.has(id));
  const everyoneExited = participantIds.every((id) => exitedAt.has(id));

  return recipientsSatisfied && everyoneExited;
}

module.exports = { isEligibleForAutoDelete };
