package com.totonoi.sauna.wear.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.totonoi.sauna.shared.calculator.TotonoiResult
import com.totonoi.sauna.shared.model.SessionPhase
import kotlinx.coroutines.flow.StateFlow

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

/**
 * 計測処理の実体は [MeasurementService] (Foreground Service) に置き、
 * このViewModelはUIからのイベントをServiceへ橋渡しするだけの薄い層にする。
 * Activity/ViewModelのライフサイクルに計測が引きずられて途中停止するのを防ぐため。
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    val uiState: StateFlow<SessionUiState> = MeasurementService.uiState

    fun startSession() {
        MeasurementService.start(getApplication())
    }

    fun switchPhase(nextPhase: SessionPhase) {
        MeasurementService.switchPhase(getApplication(), nextPhase)
    }

    fun endSession() {
        MeasurementService.end(getApplication())
    }

    fun backToHome() {
        MeasurementService.resetToHome()
    }
}
