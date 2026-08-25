package com.totonoi.sauna.mobile.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Wear側から送られてきたセッションデータを受信し、スマホ側のRoom DBに保存する。 */
class SessionSyncListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: com.google.android.gms.wearable.DataEventBuffer) {
        val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(applicationContext).saunaDao())

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

            scope.launch { repository.saveSession(session) }
        }
    }
}
