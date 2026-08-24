package com.pumpkin.app.data.local

import android.content.Context

/**
 * Per-chat notification tone, chosen from the phone's own existing tones via
 * RingtoneManager.ACTION_RINGTONE_PICKER (see ChatScreen's overflow menu) —
 * not a bundled sound asset. Plain (unencrypted) local prefs: a tone choice
 * isn't sensitive the way the PIN hash is, and it never leaves the device —
 * PumpkinMessagingService is the only reader, deciding what to play for a
 * background push naming this chat.
 */
class NotificationToneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pumpkin_notification_tones", Context.MODE_PRIVATE)

    fun get(chatId: String): String? = prefs.getString(chatId, null)

    fun set(chatId: String, toneUri: String?) {
        prefs.edit().apply {
            if (toneUri.isNullOrBlank()) remove(chatId) else putString(chatId, toneUri)
        }.apply()
    }
}
