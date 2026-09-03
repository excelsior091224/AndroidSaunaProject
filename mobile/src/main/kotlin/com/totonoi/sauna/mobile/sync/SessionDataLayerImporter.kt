package com.totonoi.sauna.mobile.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * 取りこぼし対策として、Data Layer上に残っているセッションDataItemをアプリ起動時に再取り込みする。
 * 本来のリアルタイムプッシュ(SessionSyncListenerService)が機能しなかった場合の最終防波堤。
 */
class SessionDataLayerImporter(context: Context) {

    private val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(context).saunaDao())
    private val dataClient = Wearable.getDataClient(context)
    private val ackSender = SessionAckSender(context)
    private val deletedSessionStore = DeletedSessionStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    /** 新規に取り込んだセッションを返す。呼び出し側で通知要否を判断できるようにするため。 */
    suspend fun importPendingSessions(): List<SaunaSession> {
        val imported = mutableListOf<SaunaSession>()
        val existingIds = repository.observeSessions().first().map { it.id }.toSet()

        Log.i(TAG, "Starting fallback import; existingSessions=${existingIds.size}")
        val buffer = dataClient.dataItems.await()
        buffer.use { dataItems ->
            dataItems.forEach { item ->
                val path = item.uri.path.orEmpty()
                if (!path.startsWith(DataLayerKeys.SESSION_PATH_PREFIX)) return@forEach

                val dataMap = DataMapItem.fromDataItem(item).dataMap
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
                if (deletedSessionStore.contains(session.id)) {
                    ackSender.sendAck(session.id)
                    return@forEach
                }
                repository.saveSession(session)
                ackSender.sendAck(session.id)
                if (session.id !in existingIds) {
                    imported += session
                }
            }
        }
        Log.i(TAG, "Fallback import completed; imported=${imported.size}")
        return imported
    }

    private companion object {
        private const val TAG = "SessionDataLayerImporter"
    }
}
