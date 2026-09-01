package com.totonoi.sauna.wear.session

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionSyncListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        retryPending("service created")
    }

    override fun onPeerConnected(peer: Node) {
        Log.i(TAG, "Peer connected: ${peer.displayName} (${peer.id})")
        retryPendingAfterConnection()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != DataLayerKeys.SESSION_ACK_PATH) return

        val sessionId = messageEvent.data.decodeToString()
        if (sessionId.isBlank()) {
            Log.w(TAG, "Ignored blank session ack")
            return
        }

        PendingSessionSyncQueue(applicationContext).remove(sessionId)
        Log.i(TAG, "Received mobile ack for session $sessionId")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun retryPending(reason: String) {
        scope.launch {
            SessionRetrySyncer(applicationContext).retryPending(reason)
        }
    }

    private fun retryPendingAfterConnection() {
        scope.launch {
            val retrySyncer = SessionRetrySyncer(applicationContext)
            listOf(0L, 5_000L, 20_000L).forEachIndexed { attempt, delayMs ->
                if (delayMs > 0) delay(delayMs)
                retrySyncer.retryPending("peer connected attempt ${attempt + 1}")
            }
        }
    }

    private companion object {
        private const val TAG = "WearSessionSyncListener"
    }
}