package com.totonoi.sauna.wear.session

import android.content.Context
import android.util.Log
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository

class SessionRetrySyncer(context: Context) {

    private val applicationContext = context.applicationContext
    private val queue = PendingSessionSyncQueue(applicationContext)
    private val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(applicationContext).saunaDao())
    private val syncSender = SessionSyncSender(applicationContext)

    suspend fun retryPending(reason: String) {
        val pendingIds = queue.ids()
        if (pendingIds.isEmpty()) {
            Log.i(TAG, "No pending sessions to sync; trigger=$reason")
            return
        }

        Log.i(TAG, "Retrying ${pendingIds.size} pending session(s); trigger=$reason")
        pendingIds.forEach { sessionId ->
            val session = repository.getSession(sessionId)
            if (session == null) {
                Log.w(TAG, "Dropping missing pending session $sessionId")
                queue.remove(sessionId)
                return@forEach
            }

            runCatching { syncSender.sendSession(session) }
                .onSuccess {
                    Log.i(TAG, "Republished pending session $sessionId; awaiting mobile ack")
                }
                .onFailure { error ->
                    Log.e(TAG, "Could not retry session $sessionId", error)
                }
        }
    }

    private companion object {
        private const val TAG = "SessionRetrySyncer"
    }
}