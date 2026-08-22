const { verifyIdToken } = require("../firebaseAdmin");

/** Expects "Authorization: Bearer <Firebase ID token>". Sets req.userId/req.userEmail on success. */
async function requireAuth(req, res, next) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: "Missing Authorization bearer token" });
  }
  try {
    const decoded = await verifyIdToken(token);
    req.userId = decoded.uid;
    req.userEmail = decoded.email || "";
    next();
  } catch (e) {
    res.status(401).json({ error: "Invalid or expired token" });
  }
}

module.exports = { requireAuth };
