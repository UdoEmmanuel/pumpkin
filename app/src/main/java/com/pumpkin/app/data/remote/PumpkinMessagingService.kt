package com.pumpkin.app.data.remote

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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
        // Data-only payload: {"type": "new_message", "chatId": "..."}. No
        // RemoteMessage.notification handling on purpose — see class doc.
        if (message.data["type"] != "new_message") return

        // Foregrounded: the socket connection already delivered this message
        // and the open chat screen (or list) already reflects it — playing a
        // sound on top would be redundant noise, not a useful cue.
        scope.launch(Dispatchers.Main) {
            val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            if (!isForeground) playBackgroundMessageTone()
        }
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
