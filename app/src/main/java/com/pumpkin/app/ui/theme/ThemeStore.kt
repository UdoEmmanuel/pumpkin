package com.pumpkin.app.ui.theme

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Local, per-device appearance preference — deliberately not synced to the
 * server or the other participant; each person picks their own, the way a
 * device's own display settings work, not a shared chat setting. Backed by
 * a mutableStateOf so PumpkinTheme (wrapping everything except the decoy
 * calculator) recomposes live the instant the selection changes.
 */
object ThemeStore {
    private const val PREFS_NAME = "pumpkin_theme"
    private const val KEY_THEME = "selected_theme"

    private val _selected = mutableStateOf(AppColorTheme.PUMPKIN_RED)
    val selected: State<AppColorTheme> get() = _selected

    fun load(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null)
        _selected.value = AppColorTheme.fromId(saved)
    }

    fun select(context: Context, theme: AppColorTheme) {
        _selected.value = theme
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.name).apply()
    }
}
