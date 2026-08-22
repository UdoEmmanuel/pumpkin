package com.pumpkin.app.data.remote

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// PRD 4.3: "No notification tray entries for incoming messages (silent/data-only
// push if the app needs to wake in the background)". This service deliberately
// never calls NotificationManager / NotificationCompat — it only wakes the app
// so it can pull fresh state from Firestore next time a chat screen is open.
class PumpkinMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only payload expected, e.g. {"type": "new_message", "chatId": "..."}.
        // No RemoteMessage.notification handling on purpose — see class doc.
        val chatId = message.data["chatId"] ?: return
        // Hook point: nudge an active repository/sync layer to refetch this
        // chat's messages. Left as a TODO since it depends on how DI/singleton
        // access to ChatRepository is wired up in the rest of the app.
    }

    override fun onNewToken(token: String) {
        // TODO: push token to the user's Firestore user doc so peers can be
        // notified. Skipped in this scaffold — wire up once Firebase Auth
        // identity (PRD 6) is in place.
    }
}
