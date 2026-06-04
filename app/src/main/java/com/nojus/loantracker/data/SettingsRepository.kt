package com.nojus.loantracker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    val showHistory: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHOW_HISTORY) {
                trySend(prefs.getBoolean(KEY_SHOW_HISTORY, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_SHOW_HISTORY, false))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setShowHistory(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HISTORY, value).apply()
    }

    companion object {
        private const val NAME = "loantracker_settings"
        private const val KEY_SHOW_HISTORY = "show_history"
    }
}
