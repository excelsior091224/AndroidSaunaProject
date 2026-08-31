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
import kotlinx.coroutines.launch

class SessionSyncListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        retryPending("service created")
    }

    override fun onPeerConnected(peer: Node) {
        Log.i(TAG, "Peer connected: ${peer.displayName} (${peer.id})")
        retryPending("peer connected")
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
            Log.i(TAG, "Retry requested: $reason")
            SessionRetrySyncer(applicationContext).retryPending()
        }
    }

    private companion object {
        private const val TAG = "WearSessionSyncListener"
    }
}