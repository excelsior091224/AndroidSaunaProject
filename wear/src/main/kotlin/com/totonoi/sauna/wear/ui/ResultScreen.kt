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
import com.totonoi.sauna.shared.calculator.TotonoiResult
import kotlin.math.roundToInt

@Composable
fun ResultScreen(result: TotonoiResult, onBackToHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(text = "ととのい値", style = MaterialTheme.typography.caption1)
        Text(text = "${result.totalScore.roundToInt()}", style = MaterialTheme.typography.display1)
        Text(text = "${result.cycleCount}セット", style = MaterialTheme.typography.caption2)
        Button(onClick = onBackToHome) {
            Text(text = "ホームへ")
        }
    }
}
