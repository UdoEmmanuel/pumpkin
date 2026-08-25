package com.pumpkin.app.data.local

import android.content.Context

/**
 * Remembers which release version the user last dismissed the chat list's
 * update banner for, so it doesn't reappear every time they revisit the
 * screen — but it DOES reappear for any version newer than the dismissed
 * one, and stops needing to be dismissed at all once the app is actually
 * updated (the installed version will then match the "latest" version).
 */
class UpdateDismissStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pumpkin_update_dismiss", Context.MODE_PRIVATE)

    fun isDismissed(version: String): Boolean = prefs.getString("dismissed_version", null) == version

    fun dismiss(version: String) {
        prefs.edit().putString("dismissed_version", version).apply()
    }
}
