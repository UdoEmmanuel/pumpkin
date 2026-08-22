# Pumpkin server

Self-hosted replacement for Firestore's `chats`/`messages` collections —
built because the Spark (free) Firestore plan's daily write quota kept
getting exhausted during active development/testing. This does **not**
replace Firebase Auth or the Firestore `users` collection — those stay
exactly as they are (identity was never the problem, and Auth reads/writes
are cheap and infrequent).

REST for one-shot reads (chat list, message history, starting a chat).
Socket.IO for everything real-time (sending, read/delivered receipts,
auto-delete, typing) — this is what Firestore's snapshot listeners were
doing before.

## One-time setup

### 1. MongoDB Atlas (free M0 cluster)
1. [mongodb.com/cloud/atlas/register](https://www.mongodb.com/cloud/atlas/register) → create a free account.
2. Create a free **M0** cluster (any region).
3. Database Access → add a database user (username/password).
4. Network Access → add `0.0.0.0/0` (allow from anywhere) — fine for a school project; a real deployment would restrict this to your host's IP.
5. Database → Connect → Drivers → copy the connection string. It looks like:
   `mongodb+srv://<user>:<password>@<cluster>.mongodb.net/pumpkin?retryWrites=true&w=majority`

### 2. Firebase service account key
1. Firebase console → your `pumpkin-a1a49` project → ⚙️ Project Settings → **Service accounts**.
2. Click **Generate new private key** → downloads a JSON file.
3. Save it as `server/firebase-service-account.json` (already gitignored — never commit it).

### 3. Environment
```bash
cp .env.example .env
```
Fill in `MONGODB_URI` from step 1. `FIREBASE_SERVICE_ACCOUNT_PATH` can stay as the default if you saved the key file where step 2 says.

### 4. Install and run
```bash
npm install
npm start
```
You should see `Connected to MongoDB` and `Pumpkin server listening on :4000`.

## Testing against the Android emulator locally

No deployment needed to test end to end — the Android emulator can reach
your dev machine's `localhost` via the special alias `10.0.2.2`. Point the
Android app's server base URL at `http://10.0.2.2:4000` (see
`app/src/main/java/com/pumpkin/app/data/remote/ServerConfig.kt`) while the
server is running with `npm start`.

## Deploying (for the actual handoff build)

When ready to hand off a build that doesn't depend on your dev machine
being on:

1. Push this `server/` folder to its own Git repo (or a subfolder deploy on Render/Railway).
2. [render.com](https://render.com) (or [railway.app](https://railway.app)) → New Web Service → connect the repo.
3. Build command: `npm install`. Start command: `npm start`.
4. Add environment variables `MONGODB_URI` and `PORT` in the dashboard. For
   `FIREBASE_SERVICE_ACCOUNT_PATH`, either add the whole JSON as a secret
   file in the platform's dashboard, or switch `firebaseAdmin.js` to read
   the JSON from an env var instead of a file path — whichever the
   platform's secret-file support makes easier.
5. Once deployed you'll get a public URL like `https://pumpkin-server.onrender.com` — put that in the Android app's `ServerConfig.kt` instead of the `10.0.2.2` dev URL.

**Known tradeoff (per the PRD)**: Render/Railway free tiers sleep the
service when idle, so the first request after a period of inactivity will
be slow (the server has to wake up) — fine for a demo, not for "always
responsive" production use.
