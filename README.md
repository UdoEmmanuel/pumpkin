# Pumpkin

A private messaging app built for a school project — hidden entry point, app
lock, real-time 1:1 chat, and messages that auto-delete once both people have
read and left the conversation. Full requirements live in `pumpkin.docx`
(the PRD this build follows).

## What it does

- **Hidden entry**: no "Pumpkin" icon anywhere — the launcher is a working
  decoy calculator. A secret sequence opens the real app.
- **App lock**: biometric (fingerprint/face) with a salted-hash PIN fallback,
  shown before any chat content renders.
- **1:1 real-time chat**: sent/delivered/read receipts, typing indicator,
  online/last-seen status.
- **Auto-delete**: a message disappears from both devices only once *every*
  participant — sender included — has read (where applicable) and exited the
  chat screen. A server-side TTL sweep also purges anything left over after
  14 days.
- **Pairing by email**: no public directory — you start a chat by entering
  the other person's email, same as the PRD's "manually provisioned
  participants" model.

## Architecture

```
app/      Android client — Kotlin, Jetpack Compose, Room (local cache)
server/   Self-hosted backend — Node/Express/Socket.IO/MongoDB
```

- **Identity**: Firebase Authentication (email/password + Google). This is
  the *only* thing Firebase is used for.
- **Everything else** — chats, messages, read/delivered/typing/presence,
  user profiles — lives in MongoDB behind `server/`, reached over REST
  (one-shot reads, pairing) and Socket.IO (everything real-time).

This is a deliberate split: an earlier version put chat data in Firestore
too, which kept exhausting the free tier's daily write quota during active
development. Firebase Auth's token issuance is a separate product from the
Firestore database and isn't subject to that quota, so it stays; the data
that was actually causing the problem moved to a backend with no such cap.

## Repo layout

```
app/            Android app module
server/         Node backend (REST + Socket.IO + MongoDB)
  src/models/       Mongoose schemas (Chat, Message, User)
  src/routes/       REST endpoints (/api/chats, /api/users)
  src/socket/       Real-time event handlers
  src/presence.js   In-memory online/last-seen tracking
  src/ttlSweep.js   14-day fallback purge (PRD 4.4)
pumpkin.docx    Original PRD
```

## One-time setup

You'll need your own Firebase project and MongoDB Atlas cluster — this repo
intentionally contains no credentials.

### 1. Firebase (identity only)
1. Create a free ("Spark") Firebase project.
2. Add an Android app with package name `com.pumpkin.app`, download
   `google-services.json`, place it at `app/google-services.json`.
3. Enable **Authentication** → Email/Password and Google sign-in providers.
4. For Google Sign-In to actually work, register your debug (and later,
   release) keystore's SHA-1 fingerprint under Project Settings → Your apps,
   then copy the generated **Web client ID** into
   `app/src/main/res/values/strings.xml`'s `google_web_client_id` string.
5. Project Settings → Service accounts → **Generate new private key** →
   save the downloaded JSON as `server/firebase-service-account.json`
   (gitignored). The server uses this only to verify the ID tokens the app
   sends — it never touches Firestore.

### 2. MongoDB Atlas
1. Create a free M0 cluster.
2. Add a database user, and allow network access from wherever the server
   runs (`0.0.0.0/0` is fine for a school project; scope it down for
   anything real).
3. Copy the connection string.

### 3. Server
```bash
cd server
cp .env.example .env
# fill in MONGODB_URI from step 2
npm install
npm start
```
You should see `Connected to MongoDB` and `Pumpkin server listening on :4000`.

### 4. Android app
Open the project root in Android Studio and sync. To test against a server
running on your own machine, the emulator reaches it at `10.0.2.2` — this is
already the default in `app/src/main/java/com/pumpkin/app/data/remote/ServerConfig.kt`.
Point it at a real deployed URL before distributing a build (see below).

Full flow once both are running: launcher (decoy calculator) → secret code
→ lock screen → sign in → chat list → chat.

See `server/README.md` for more detail on the backend, including deploying
it (Render/Railway) instead of running it locally.

## Building a signed, installable APK

```bash
keytool -genkeypair -v -keystore pumpkin-release.jks \
  -alias pumpkin -keyalg RSA -keysize 2048 -validity 10000

cp keystore.properties.example keystore.properties
# edit keystore.properties: point storeFile at pumpkin-release.jks and fill
# in the passwords you just chose

./gradlew assembleRelease
```

The signed APK lands at `app/build/outputs/apk/release/app-release.apk` —
that's the one file to hand off. Skipping the keystore step still builds,
but produces an unsigned APK most devices will refuse to install.

For quick iteration: `./gradlew installDebug`, or the Android Studio Run
button.

## Known limitations / not built yet

- No end-to-end encryption (standard TLS in transit only — a stretch goal
  per the PRD, not required for Phase 1).
- Voice/video calls, media messages, stickers/emoji: explicitly out of
  scope for this phase (PRD section 10).
- 1:1 only — no group chats.
- The hidden launcher icon is genuinely hidden from the app drawer, but
  still visible in Settings → Apps by design of Android; there's no
  root-free way around that.
