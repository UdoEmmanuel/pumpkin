const express = require("express");
const { requireAuth } = require("../middleware/auth");
const { asyncHandler } = require("../asyncHandler");

const router = express.Router();
// Unauthenticated diagnostic router — TEMPORARY, for debugging the
// update-download corruption issue directly (no Firebase token needed).
// Remove once resolved. Reveals only byte counts / a hex prefix of an
// asset the account already owns, not the token or repo contents.
const diagRouter = express.Router();

// Proxies GitHub Releases so the Android app never needs a GitHub credential
// of its own — GITHUB_TOKEN lives only here, as a server env var, never
// shipped in the APK. GITHUB_REPO is "owner/repo", e.g. "UdoEmmanuel/pumpkin".
const GITHUB_REPO = process.env.GITHUB_REPO;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;

diagRouter.get(
  "/download-diag",
  asyncHandler(async (_req, res) => {
    const diag = { GITHUB_REPO, hasToken: !!GITHUB_TOKEN, tokenLength: GITHUB_TOKEN ? GITHUB_TOKEN.length : 0 };
    try {
      const release = await fetchLatestRelease();
      diag.releaseTag = release.tag_name;
      const asset = apkAssetOf(release);
      diag.assetFound = !!asset;
      if (!asset) return res.json(diag);
      diag.declaredAssetSize = asset.size;
      diag.assetApiUrl = asset.url;

      const assetRes = await fetch(asset.url, { headers: githubHeaders("application/octet-stream") });
      diag.assetFetchStatus = assetRes.status;
      diag.assetFetchOk = assetRes.ok;
      diag.assetContentLengthHeader = assetRes.headers.get("content-length");
      diag.assetContentTypeHeader = assetRes.headers.get("content-type");

      if (assetRes.ok && assetRes.body) {
        const buf = Buffer.from(await assetRes.arrayBuffer());
        diag.actualBytesRead = buf.length;
        diag.first16BytesHex = buf.subarray(0, 16).toString("hex");
        diag.last16BytesHex = buf.subarray(-16).toString("hex");
      } else {
        diag.errorBodyPreview = await assetRes.text().catch(() => null);
      }
    } catch (e) {
      diag.error = e.message;
      diag.stack = e.stack;
    }
    res.json(diag);
  })
);

router.use(requireAuth);

function githubHeaders(accept) {
  return {
    Authorization: `Bearer ${GITHUB_TOKEN}`,
    Accept: accept,
    "X-GitHub-Api-Version": "2022-11-28"
  };
}

async function fetchLatestRelease() {
  const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`, {
    headers: githubHeaders("application/vnd.github+json")
  });
  if (!res.ok) {
    throw new Error(`GitHub releases request failed: ${res.status}`);
  }
  return res.json();
}

function apkAssetOf(release) {
  return (release.assets || []).find((a) => a.name.endsWith(".apk"));
}

// GET /api/app/latest — { version, notes, hasApk }. The app compares
// `version` (a git tag like "v0.5.0") against its own BuildConfig.VERSION_NAME.
router.get(
  "/latest",
  asyncHandler(async (_req, res) => {
    if (!GITHUB_REPO || !GITHUB_TOKEN) {
      return res.status(503).json({ error: "Update checking isn't configured on the server" });
    }
    const release = await fetchLatestRelease();
    const asset = apkAssetOf(release);
    res.json({
      version: release.tag_name,
      notes: release.body || "",
      hasApk: !!asset
    });
  })
);

// GET /api/app/download — streams the latest release's .apk asset through
// this server. GitHub's asset URLs require the same auth as the release
// metadata, so the client can't hit them directly either.
router.get(
  "/download",
  asyncHandler(async (_req, res) => {
    if (!GITHUB_REPO || !GITHUB_TOKEN) {
      return res.status(503).json({ error: "Update checking isn't configured on the server" });
    }
    const release = await fetchLatestRelease();
    const asset = apkAssetOf(release);
    if (!asset) {
      return res.status(404).json({ error: "Latest release has no APK asset" });
    }

    const assetRes = await fetch(asset.url, { headers: githubHeaders("application/octet-stream") });
    if (!assetRes.ok || !assetRes.body) {
      return res.status(502).json({ error: "Couldn't fetch the release asset from GitHub" });
    }

    res.setHeader("Content-Type", "application/vnd.android.package-archive");
    res.setHeader("Content-Disposition", `attachment; filename="${asset.name}"`);
    // Trust the ACTUAL response's own Content-Length, not the release
    // metadata's asset.size — those can disagree (e.g. after GitHub's
    // redirect to the real blob storage URL), and declaring a Content-Length
    // that doesn't match what's actually streamed silently truncates or
    // corrupts the download client-side with no error on either end. Safer
    // to omit it entirely (falls back to chunked transfer-encoding) than to
    // risk asserting a wrong number.
    const actualLength = assetRes.headers.get("content-length");
    if (actualLength) res.setHeader("Content-Length", actualLength);

    const { Readable } = require("stream");
    const { pipeline } = require("stream/promises");
    try {
      await pipeline(Readable.fromWeb(assetRes.body), res);
    } catch (e) {
      console.error("[app/download] stream failed:", e.message);
      if (!res.headersSent) res.status(502).json({ error: "Download failed mid-stream" });
    }
  })
);

module.exports = { router, diagRouter };
