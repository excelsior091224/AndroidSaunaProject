package com.totonoi.sauna.mobile.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.totonoi.sauna.mobile.notification.SessionNotifier
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Wear側から送られてきたセッションデータを受信し、スマホ側のRoom DBに保存する。
 * スマホアプリがフォアグラウンドで起動していなくてもシステムがこのServiceを起動して呼び出すため、
 * 通知もここで出すことで「アプリを開かないと通知が来ない」問題を避ける。
 */
class SessionSyncListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "SessionSyncListener"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != DataLayerKeys.SESSION_NOTIFICATION_PATH) return

        val fields = messageEvent.data.decodeToString().split('\t')
        val score = fields.getOrNull(0)?.toIntOrNull()
        val cycleCount = fields.getOrNull(1)?.toIntOrNull()
        if (score == null || cycleCount == null) {
            Log.w(TAG, "Ignored invalid session notification payload")
            return
        }

        Log.i(TAG, "Received immediate notification: score=$score cycles=$cycleCount")
        SessionNotifier(applicationContext).notifySessionReceived(score, cycleCount)
    }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        Log.i(TAG, "onDataChanged received ${dataEvents.count} event(s)")
        val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(applicationContext).saunaDao())
        val notifier = SessionNotifier(applicationContext)
        val ackSender = SessionAckSender(applicationContext)
        val deletedSessionStore = DeletedSessionStore(applicationContext)

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (!event.dataItem.uri.path.orEmpty().startsWith(DataLayerKeys.SESSION_PATH_PREFIX)) return@forEach

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val session = SaunaSession(
                id = dataMap.getString(DataLayerKeys.KEY_SESSION_ID) ?: return@forEach,
                startMs = dataMap.getLong(DataLayerKeys.KEY_START_MS),
                endMs = dataMap.getLong(DataLayerKeys.KEY_END_MS),
                segments = json.decodeFromString<List<PhaseSegment>>(
                    dataMap.getString(DataLayerKeys.KEY_SEGMENTS_JSON) ?: "[]",
                ),
                totonoiScore = dataMap.getDouble(DataLayerKeys.KEY_TOTONOI_SCORE),
                cycleCount = dataMap.getInt(DataLayerKeys.KEY_CYCLE_COUNT),
            )

            scope.launch {
                runCatching {
                    if (deletedSessionStore.contains(session.id)) {
                        ackSender.sendAck(session.id)
                        return@runCatching
                    }
                    repository.saveSession(session)
                    notifier.notifyNewSession(session)
                    ackSender.sendAck(session.id)
                }.onSuccess {
                    Log.i(TAG, "Saved and notified session ${session.id}")
                }.onFailure { error ->
                    Log.e(TAG, "Could not process session ${session.id}", error)
                }
            }
        }
    }
}
