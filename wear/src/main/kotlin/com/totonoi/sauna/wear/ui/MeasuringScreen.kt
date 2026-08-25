package com.totonoi.sauna.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.totonoi.sauna.shared.model.SessionPhase

@Composable
fun MeasuringScreen(
    currentPhase: SessionPhase?,
    latestBpm: Int?,
    onSwitchPhase: (SessionPhase) -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(text = phaseLabel(currentPhase), style = MaterialTheme.typography.caption1)
        Text(text = "${latestBpm ?: "--"} bpm", style = MaterialTheme.typography.display2)

        when (currentPhase) {
            SessionPhase.SAUNA -> Button(onClick = { onSwitchPhase(SessionPhase.COLD_BATH) }) {
                Text(text = "水風呂へ")
            }
            SessionPhase.COLD_BATH -> Button(onClick = { onSwitchPhase(SessionPhase.REST) }) {
                Text(text = "休憩へ")
            }
            SessionPhase.REST -> Button(onClick = { onSwitchPhase(SessionPhase.SAUNA) }) {
                Text(text = "もう1セット")
            }
            null -> Unit
        }

        Button(onClick = onEnd) {
            Text(text = "終了して記録")
        }
    }
}

private fun phaseLabel(phase: SessionPhase?): String = when (phase) {
    SessionPhase.SAUNA -> "サウナ"
    SessionPhase.COLD_BATH -> "水風呂"
    SessionPhase.REST -> "休憩(外気浴)"
    null -> ""
}
