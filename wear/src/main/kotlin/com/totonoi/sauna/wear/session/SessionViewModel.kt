package com.totonoi.sauna.wear.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totonoi.sauna.shared.calculator.TotonoiCalculator
import com.totonoi.sauna.shared.calculator.TotonoiResult
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.model.SessionPhase
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.wear.health.HeartRateMeasurer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SaunaScreen {
    data object Home : SaunaScreen()
    data object Measuring : SaunaScreen()
    data class Result(val result: TotonoiResult) : SaunaScreen()
}

data class SessionUiState(
    val screen: SaunaScreen = SaunaScreen.Home,
    val currentPhase: SessionPhase? = null,
    val latestBpm: Int? = null,
    val elapsedPhaseMs: Long = 0L,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val heartRateMeasurer = HeartRateMeasurer(application)
    private val repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(application).saunaDao())
    private val syncSender = SessionSyncSender(application)

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var sessionStartMs = 0L
    private val completedSegments = mutableListOf<PhaseSegment>()
    private var currentSegmentStartMs = 0L
    private var currentSamples = mutableListOf<HeartRateSample>()
    private var measureJob: kotlinx.coroutines.Job? = null

    /** サウナ計測開始。まず「サウナ」フェーズからスタートする。 */
    fun startSession() {
        sessionStartMs = System.currentTimeMillis()
        completedSegments.clear()
        beginPhase(SessionPhase.SAUNA)
        _uiState.value = _uiState.value.copy(screen = SaunaScreen.Measuring)

        measureJob = viewModelScope.launch {
            heartRateMeasurer.heartRateBpmFlow().collect { bpm ->
                currentSamples.add(HeartRateSample(System.currentTimeMillis(), bpm))
                _uiState.value = _uiState.value.copy(latestBpm = bpm)
            }
        }
    }

    /** ユーザーがフェーズ切り替えボタン(水風呂/休憩)を押した時に呼ぶ。 */
    fun switchPhase(nextPhase: SessionPhase) {
        finishCurrentSegment()
        beginPhase(nextPhase)
    }

    /** セッション終了。最後のフェーズを確定し、ととのい値を計算・保存・スマホへ同期する。 */
    fun endSession() {
        finishCurrentSegment()
        measureJob?.cancel()

        val result = TotonoiCalculator.calculate(completedSegments)
        val session = SaunaSession(
            id = UUID.randomUUID().toString(),
            startMs = sessionStartMs,
            endMs = System.currentTimeMillis(),
            segments = completedSegments.toList(),
            totonoiScore = result.totalScore,
            cycleCount = result.cycleCount,
        )

        viewModelScope.launch {
            repository.saveSession(session)
            syncSender.sendSession(session)
        }

        _uiState.value = _uiState.value.copy(screen = SaunaScreen.Result(result), currentPhase = null)
    }

    fun backToHome() {
        _uiState.value = SessionUiState()
    }

    private fun beginPhase(phase: SessionPhase) {
        currentSegmentStartMs = System.currentTimeMillis()
        currentSamples = mutableListOf()
        _uiState.value = _uiState.value.copy(currentPhase = phase)
    }

    private fun finishCurrentSegment() {
        val phase = _uiState.value.currentPhase ?: return
        completedSegments.add(
            PhaseSegment(
                phase = phase,
                startMs = currentSegmentStartMs,
                endMs = System.currentTimeMillis(),
                samples = currentSamples.toList(),
            ),
        )
    }
}
