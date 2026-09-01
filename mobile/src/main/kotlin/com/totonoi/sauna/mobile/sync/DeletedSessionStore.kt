package com.totonoi.sauna.mobile.sync

import android.content.Context

class DeletedSessionStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun contains(sessionId: String): Boolean = sessionId in preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()

    fun add(sessionId: String) {
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty() + sessionId
        preferences.edit().putStringSet(KEY_IDS, ids).apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "deleted_sessions"
        private const val KEY_IDS = "ids"
    }
}