// In-memory online/last-seen tracking, keyed by uid. Resets on server
// restart (lastSeenAt would just read as "never" until someone connects
// again) — an accepted tradeoff for a school-project scale app; a real
// deployment would persist this in Mongo alongside Chat/Message.
//
// connectionCount matters because one user can have more than one socket
// open (e.g. the app reconnecting after a network blip before the old
// socket has timed out) — only go offline when the LAST connection closes.
const state = new Map(); // uid -> { online: boolean, lastSeenAt: number|null, connectionCount: number }

function get(uid) {
  return state.get(uid) || { online: false, lastSeenAt: null };
}

function onConnect(uid) {
  const entry = state.get(uid) || { online: false, lastSeenAt: null, connectionCount: 0 };
  entry.connectionCount += 1;
  entry.online = true;
  state.set(uid, entry);
}

/** Returns true if this was the user's last active connection (i.e. they just went offline). */
function onDisconnect(uid) {
  const entry = state.get(uid);
  if (!entry) return false;
  entry.connectionCount = Math.max(0, entry.connectionCount - 1);
  if (entry.connectionCount === 0) {
    entry.online = false;
    entry.lastSeenAt = Date.now();
    state.set(uid, entry);
    return true;
  }
  state.set(uid, entry);
  return false;
}

module.exports = { get, onConnect, onDisconnect };
