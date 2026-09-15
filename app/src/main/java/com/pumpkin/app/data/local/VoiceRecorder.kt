package com.pumpkin.app.data.local

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

/**
 * Thin wrapper around MediaRecorder for press-and-hold voice notes. Records
 * to a temp file in the app's cache dir; the caller reads + base64-encodes
 * that file and deletes it once sent (see ChatScreen's mic button).
 *
 * AAC in an M4A container at a low bitrate — a 2-minute clip (the
 * client-enforced cap, see MAX_DURATION_MS) lands well under ~1MB, which is
 * what keeps storing it inline on the Message document (see
 * server/src/models/Message.js) reasonable against MongoDB's 16MB
 * document limit.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    companion object {
        const val MAX_DURATION_MS = 120_000L
    }

    /**
     * Returns the file recording is about to write to, or null if recording
     * couldn't start at all (mic held by another app/call, no storage, a
     * device/OS combination that rejects this encoder setup, etc.) —
     * previously an exception here (MediaRecorder.prepare()/start() both
     * throw on failure) went uncaught and crashed the app the moment
     * someone pressed the mic button, which is about as common an action as
     * a chat app has.
     */
    fun start(): File? {
        val dir = File(context.cacheDir, "voice_out").apply { mkdirs() }
        val file = File(dir, "recording_${System.currentTimeMillis()}.m4a")

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(32_000)
            r.setAudioSamplingRate(44_100)
            r.setMaxDuration(MAX_DURATION_MS.toInt())
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            startedAt = System.currentTimeMillis()
            file
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            runCatching { r.release() }
            recorder = null
            outputFile = null
            file.delete()
            null
        }
    }

    /** Returns the recorded file and its duration, or null if nothing usable was recorded. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val file = outputFile
        val durationMs = System.currentTimeMillis() - startedAt
        return try {
            r.stop()
            r.release()
            recorder = null
            if (file != null && file.exists() && file.length() > 0) file to durationMs else null
        } catch (e: Exception) {
            // stop() throws if called too soon after start() with no data
            // recorded yet — a normal outcome for an accidental tap, not an
            // error worth surfacing.
            r.release()
            recorder = null
            file?.delete()
            null
        }
    }

    /** minSdk is 24, exactly where MediaRecorder.pause()/resume() were introduced — no version gate needed. */
    fun pause() {
        try {
            recorder?.pause()
        } catch (_: Exception) {
            // Nothing usable to do if the recorder wasn't in a pausable state — ignore.
        }
    }

    fun resume() {
        try {
            recorder?.resume()
        } catch (_: Exception) {
            // Same as pause() — ignore.
        }
    }

    /**
     * Peak input level since the last call to this method, 0..32767 (per
     * MediaRecorder's own scale) — polled periodically to drive the live
     * waveform while recording. Returns 0 if there's nothing to sample
     * rather than throwing, since this is purely cosmetic.
     */
    fun getMaxAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Same as above — fine to ignore.
        }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
