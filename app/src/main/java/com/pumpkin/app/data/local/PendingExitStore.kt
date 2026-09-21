package com.pumpkin.app.data.local

import android.content.Context

/**
 * Durable record of "exited after read" events (PRD 4.4) that haven't been
 * confirmed by the server yet. markExitedAfterRead fires from ON_STOP —
 * right as the app backgrounds and ChatRepository disconnects the socket —
 * so a plain fire-and-forget emit routinely loses the race: the process can
 * be frozen/killed before the ack comes back, and the "exited" state (needed
 * for the other side's message to ever qualify for auto-delete) is gone for
 * good. Entries here survive that, and get retried on the next socket
 * connect until the server confirms them.
 */
class PendingExitStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pumpkin_pending_exits", Context.MODE_PRIVATE)

    private fun key(chatId: String, messageId: String) = "$chatId|$messageId"

    fun add(chatId: String, messageId: String) {
        val current = prefs.getStringSet("pending", emptySet()).orEmpty()
        prefs.edit().putStringSet("pending", current + key(chatId, messageId)).commit()
    }

    fun remove(chatId: String, messageId: String) {
        val current = prefs.getStringSet("pending", emptySet()).orEmpty()
        prefs.edit().putStringSet("pending", current - key(chatId, messageId)).apply()
    }

    /** Returns each pending entry as (chatId, messageId). */
    fun all(): List<Pair<String, String>> =
        prefs.getStringSet("pending", emptySet()).orEmpty().mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
}
