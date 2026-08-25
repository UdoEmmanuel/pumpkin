package com.pumpkin.app.data.remote

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pumpkin.app.data.local.NotificationToneStore
import com.pumpkin.app.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// PRD 4.3: "No notification tray entries for incoming messages (silent/data-only
// push if the app needs to wake in the background)". This service deliberately
// never calls NotificationManager / NotificationCompat — the only user-visible
// effect of a push landing here is a short tone, never a tray entry, banner,
// or lock-screen text.
class PumpkinMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only payload: {"type": "new_message"|"reaction", "chatId": "..."}.
        // No RemoteMessage.notification handling on purpose — see class doc.
        val type = message.data["type"]
        if (type != "new_message" && type != "reaction") return

        val chatId = message.data["chatId"]
        scope.launch(Dispatchers.Main) {
            val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            // Only suppress the sound when the user is actually looking at
            // THIS chat right now — being foregrounded on the chat list, the
            // decoy calculator, or the lock screen is still a case where
            // nothing on screen shows the new message, so it still needs a
            // cue. (Previously this suppressed on any foreground state,
            // which meant no sound ever played while sitting on the chat
            // list — see CurrentChatTracker kdoc.)
            val viewingThisChat = isForeground && chatId != null && CurrentChatTracker.openChatId == chatId
            if (viewingThisChat) return@launch

            val toneUri = chatId?.let { NotificationToneStore(applicationContext).get(it) }
            if (toneUri != null) playChosenTone(toneUri) else playBackgroundMessageTone()
        }
    }

    /** The user's own picked tone (see ChatScreen's overflow menu) — an existing phone tone, not a bundled asset. */
    private fun playChosenTone(toneUri: String) {
        runCatching {
            val player = MediaPlayer()
            player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
            player.setDataSource(applicationContext, Uri.parse(toneUri))
            player.setOnCompletionListener { it.release() }
            player.prepare()
            player.start()
        }.onFailure { playBackgroundMessageTone() }
    }

    override fun onNewToken(token: String) {
        // Reuses AuthRepository's own registration logic (and its scope) so
        // there's exactly one code path that talks to the fcm-token endpoint.
        AuthRepository().registerFcmTokenAsync()
    }

    private fun playBackgroundMessageTone() {
        runCatching {
            // ToneGenerator instead of a bundled sound file: no asset to
            // ship, and TONE_PROP_BEEP2 isn't a tone Android uses for
            // anything else on-device, so it reads as distinctly "this app"
            // rather than blending into system notification sounds.
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            MainScope().launch {
                kotlinx.coroutines.delay(300)
                toneGenerator.release()
            }
        }
    }
}
