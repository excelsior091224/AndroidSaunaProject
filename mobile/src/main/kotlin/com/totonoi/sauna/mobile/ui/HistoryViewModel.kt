package com.totonoi.sauna.mobile.ui

import android.app.Application
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totonoi.sauna.mobile.notification.SessionNotifier
import com.totonoi.sauna.mobile.sync.SessionDataLayerImporter
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.shared.sync.DataLayerKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(application).saunaDao())
    private val importer = SessionDataLayerImporter(application)
    private val dataClient = Wearable.getDataClient(application)
    private val notifier = SessionNotifier(application)

    // 同期経路(リアルタイム受信/起動時再取り込み)に関わらず、DBの差分だけを見て新規セッションを検知する。
    private var knownSessionIds: Set<String>? = null

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
        .onEach { list -> detectAndNotifyNewSessions(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun detectAndNotifyNewSessions(sessions: List<SaunaSession>) {
        val currentIds = sessions.map { it.id }.toSet()
        val previousIds = knownSessionIds
        if (previousIds != null) {
            val newIds = currentIds - previousIds
            sessions.filter { it.id in newIds }.forEach { notifier.notifyNewSession(it) }
        }
        knownSessionIds = currentIds
    }

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
