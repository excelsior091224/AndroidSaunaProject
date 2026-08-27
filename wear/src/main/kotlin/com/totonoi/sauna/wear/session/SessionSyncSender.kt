package com.totonoi.sauna.wear.session

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.sync.DataLayerKeys
import com.totonoi.sauna.shared.sync.SampleDownsampler
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 計測完了したセッションを Wearable Data Layer 経由でスマホ側アプリへ送信する。 */
class SessionSyncSender(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dataClient = Wearable.getDataClient(context)

    suspend fun sendSession(session: SaunaSession) {
        // 大量サンプル(数百〜千件)のまま送るとBluetooth転送が遅くなるため、送信用に間引く。
        // ローカル(Wear側Room DB)には既にフルサンプルで保存済みなのでここでは送信専用コピーのみ変更する。
        val transferSegments = SampleDownsampler.downsampleForTransfer(session.segments)

        val request = PutDataMapRequest.create("${DataLayerKeys.SESSION_PATH_PREFIX}/${session.id}").apply {
            dataMap.putString(DataLayerKeys.KEY_SESSION_ID, session.id)
            dataMap.putLong(DataLayerKeys.KEY_START_MS, session.startMs)
            dataMap.putLong(DataLayerKeys.KEY_END_MS, session.endMs)
            dataMap.putDouble(DataLayerKeys.KEY_TOTONOI_SCORE, session.totonoiScore)
            dataMap.putInt(DataLayerKeys.KEY_CYCLE_COUNT, session.cycleCount)
            dataMap.putString(DataLayerKeys.KEY_SEGMENTS_JSON, json.encodeToString<List<PhaseSegment>>(transferSegments))
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request).await()
    }
}
