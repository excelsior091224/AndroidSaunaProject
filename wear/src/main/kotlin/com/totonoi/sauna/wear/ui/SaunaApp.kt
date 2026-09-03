package com.totonoi.sauna.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.totonoi.sauna.wear.session.SaunaScreen
import com.totonoi.sauna.wear.session.SessionViewModel

@Composable
fun SaunaApp(viewModel: SessionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = uiState.screen) {
            is SaunaScreen.Home -> HomeScreen(onStart = viewModel::startSession)

            is SaunaScreen.Measuring -> MeasuringScreen(
                currentPhase = uiState.currentPhase,
                latestBpm = uiState.latestBpm,
                elapsedPhaseMs = uiState.elapsedPhaseMs,
                onSwitchPhase = viewModel::switchPhase,
                onEnd = viewModel::endSession,
            )

            is SaunaScreen.Result -> ResultScreen(
                result = screen.result,
                onBackToHome = viewModel::backToHome,
            )
        }
    }
}
