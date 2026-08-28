package com.pumpkin.app.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pumpkin.app.BuildConfig
import com.pumpkin.app.data.remote.NetworkModule
import com.pumpkin.app.data.remote.api.ChatApi
import com.pumpkin.app.data.remote.api.dto.AppVersionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Real release APKs have run well over 1MB every time so far — anything
// smaller than this is almost certainly a truncated download, not a
// legitimately tiny build.
private const val MIN_PLAUSIBLE_APK_BYTES = 500_000L

/**
 * "Update app" (chat list overflow menu). Checks/downloads the latest
 * GitHub release through the server's proxy (server/src/routes/app.js) —
 * the repo is private, so the app itself never holds a GitHub credential;
 * only the server does, as an env var. Installing still requires the user
 * to confirm the system package installer prompt (and, the first time,
 * grant "install unknown apps" to Pumpkin) — there's no silent-install path
 * for a non-system app, by Android design.
 */
class UpdateRepository(private val api: ChatApi = NetworkModule.chatApi) {

    suspend fun checkForUpdate(): Result<AppVersionDto> = runCatching { api.getLatestVersion() }

    /**
     * Not full semver comparison — just "does the latest tag differ from
     * what's installed." Good enough for a single-maintainer release
     * process where versions only ever move forward.
     */
    fun isDifferentFromInstalled(remoteVersion: String): Boolean =
        remoteVersion.removePrefix("v") != BuildConfig.VERSION_NAME

    /** Downloads the latest APK into the app's cache dir and hands it to the system installer. */
    suspend fun downloadAndInstall(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = api.downloadLatestApk()
            val expectedLength = body.contentLength() // -1 if the server didn't declare one
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "pumpkin-update.apk")
            val actualLength = body.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            // A silently truncated/corrupted download (see server/src/routes/app.js)
            // reaches the system installer as "package appears to be invalid"
            // with no useful error — catching a short file here instead gives
            // an actionable failure and avoids leaving a broken .apk around.
            val looksTruncated = (expectedLength >= 0 && actualLength != expectedLength) || actualLength < MIN_PLAUSIBLE_APK_BYTES
            if (looksTruncated) {
                file.delete()
                error("Download was incomplete ($actualLength of ${if (expectedLength >= 0) expectedLength else "?"} bytes) — try again")
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }
}
