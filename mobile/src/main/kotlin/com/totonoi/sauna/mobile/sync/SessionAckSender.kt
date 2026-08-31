package com.totonoi.sauna.mobile.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.tasks.await

class SessionAckSender(context: Context) {

    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)

    suspend fun sendAck(sessionId: String) {
        val payload = sessionId.encodeToByteArray()
        val nodes = nodeClient.connectedNodes.await()
        Log.i(TAG, "Sending ack for $sessionId to ${nodes.size} node(s)")
        nodes.forEach { node ->
            runCatching {
                messageClient.sendMessage(node.id, DataLayerKeys.SESSION_ACK_PATH, payload).await()
            }.onFailure { error ->
                Log.w(TAG, "Could not send ack for $sessionId to ${node.id}", error)
            }
        }
    }

    private companion object {
        private const val TAG = "SessionAckSender"
    }
}