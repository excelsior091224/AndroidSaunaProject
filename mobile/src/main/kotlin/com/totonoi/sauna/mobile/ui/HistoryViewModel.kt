package com.totonoi.sauna.mobile.ui

import android.app.Application
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totonoi.sauna.mobile.sync.SessionDataLayerImporter
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(application).saunaDao())
    private val importer = SessionDataLayerImporter(application)
    private val dataClient = Wearable.getDataClient(application)

    // 通知自体はバックグラウンドでも動く SessionSyncListenerService 側が担当する。
    // ここではフォアグラウンド中の一覧即時更新のためだけにDataItem変化を監視する。

    private val dataListener = DataClient.OnDataChangedListener { dataEvents ->
        var shouldImport = false
        try {
            dataEvents.forEach { event ->
                val isSessionEvent = event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path.orEmpty().startsWith(DataLayerKeys.SESSION_PATH_PREFIX)
                if (isSessionEvent) {
                    shouldImport = true
                    return@forEach
                }
            }
        } finally {
            dataEvents.release()
        }

        if (shouldImport) {
            viewModelScope.launch {
                importer.importPendingSessions()
            }
        }
    }

    val sessions: StateFlow<List<SaunaSession>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        dataClient.addListener(dataListener)
        viewModelScope.launch {
            // 時計側からのDataItemを起動時に再取り込みし、配送タイミング差の取りこぼしを防ぐ。
            importer.importPendingSessions()
        }
    }

    override fun onCleared() {
        dataClient.removeListener(dataListener)
        super.onCleared()
    }
}
