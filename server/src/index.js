require("dotenv").config();
const express = require("express");
const http = require("http");
const cors = require("cors");
const mongoose = require("mongoose");
const { Server } = require("socket.io");

const { router: chatsRouter } = require("./routes/chats");
const { router: usersRouter } = require("./routes/users");
const { router: appRouter } = require("./routes/app");
const { attachSocketHandlers } = require("./socket");
const { startTtlSweep } = require("./ttlSweep");

const app = express();
app.use(cors());
app.use(express.json());

app.get("/health", (_req, res) => res.json({ ok: true }));
app.use("/api/chats", chatsRouter);
app.use("/api/users", usersRouter);
app.use("/api/app", appRouter);

// Safety net: without this, an error thrown from an async route handler
// (even one wrapped in asyncHandler, which forwards it here via next(err))
// would otherwise get Express's default HTML error page — which the
// Android client's error-body JSON parser then fails to parse, silently
// swallowing the real error message.
app.use((err, _req, res, _next) => {
  console.error("[error]", err);
  res.status(500).json({ error: err.message || "Internal server error" });
});

const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });
app.set("io", io);
attachSocketHandlers(io);

async function main() {
  if (!process.env.MONGODB_URI) {
    throw new Error("MONGODB_URI is not set — copy .env.example to .env and fill it in.");
  }
  await mongoose.connect(process.env.MONGODB_URI);
  console.log("Connected to MongoDB");

  startTtlSweep(io);

  const port = process.env.PORT || 4000;
  server.listen(port, () => console.log(`Pumpkin server listening on :${port}`));
}

main().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});
