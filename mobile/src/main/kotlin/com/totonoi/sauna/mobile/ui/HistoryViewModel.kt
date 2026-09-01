package com.totonoi.sauna.mobile.ui

import android.app.Application
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totonoi.sauna.mobile.notification.SessionNotifier
import com.totonoi.sauna.mobile.sync.DeletedSessionStore
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
    private val notifier = SessionNotifier(application)
    private val deletedSessionStore = DeletedSessionStore(application)

    // 本来はバックグラウンドでも動く SessionSyncListenerService が通知を担当するはずだが、
    // OS/OEMの背景実行制限で呼ばれない場合があるため、アプリ起動時の取りこぼし救済経路でも
    // 新規セッションを検知したら念のため通知を出す(二重通知は同一IDで上書きされるだけなので無害)。

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
                importer.importPendingSessions().forEach { notifier.notifyNewSession(it) }
            }
        }
    }

    val sessions: StateFlow<List<SaunaSession>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        dataClient.addListener(dataListener)
        viewModelScope.launch {
            // 時計側からのDataItemを起動時に再取り込みし、配送タイミング差の取りこぼしを防ぐ。
            importer.importPendingSessions().forEach { notifier.notifyNewSession(it) }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(dataListener)
        super.onCleared()
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deletedSessionStore.add(sessionId)
            repository.deleteSession(sessionId)
        }
    }

    fun deleteSessions(sessionIds: Set<String>) {
        viewModelScope.launch {
            sessionIds.forEach { sessionId ->
                deletedSessionStore.add(sessionId)
                repository.deleteSession(sessionId)
            }
        }
    }
}
