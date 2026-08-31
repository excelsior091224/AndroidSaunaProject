package com.totonoi.sauna.wear.session

import android.content.Context

class PendingSessionSyncQueue(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun add(sessionId: String) {
        update { ids -> ids + sessionId }
    }

    fun remove(sessionId: String) {
        update { ids -> ids - sessionId }
    }

    fun ids(): Set<String> = preferences.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()

    private fun update(transform: (Set<String>) -> Set<String>) {
        val current = preferences.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
        preferences.edit().putStringSet(KEY_PENDING_IDS, transform(current)).apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "pending_session_sync"
        private const val KEY_PENDING_IDS = "pending_ids"
    }
}