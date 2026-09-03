package com.totonoi.sauna.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.totonoi.sauna.shared.calculator.CycleScore
import com.totonoi.sauna.shared.calculator.TotonoiCalculator
import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.model.SessionPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(session: SaunaSession, onBack: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    val stats = remember(session) { session.toDetailStats() }
    val cycleScores = remember(session) { TotonoiCalculator.calculate(session.segments).cycleScores }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateFormat.format(Date(session.startMs))) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 戻る")
                    }
                },
                actions = {
                    TextButton(onClick = onDelete) {
                        Text("削除")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ととのい値 ${session.totonoiScore.toInt()} / ${session.cycleCount}セット",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(text = "計測時間 ${stats.totalDurationMin}分", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "心拍数 ${stats.avgBpm} bpm (min ${stats.minBpm} / max ${stats.maxBpm})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(text = "サンプル数 ${stats.sampleCount}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text(text = "フェーズ別", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "サウナ ${stats.saunaMin}分 / 水風呂 ${stats.coldMin}分 / 休憩 ${stats.restMin}分",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "サイクル別スコア", style = MaterialTheme.typography.titleSmall)
                    if (cycleScores.isEmpty()) {
                        Text(text = "サイクル別スコアはありません", style = MaterialTheme.typography.bodySmall)
                    } else {
                        cycleScores.forEach { score ->
                            CycleScoreText(score)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "心拍数の推移", style = MaterialTheme.typography.titleSmall)
                    HeartRateChart(
                        segments = session.segments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleScoreText(score: CycleScore) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${score.cycleIndex + 1}セット目 ${score.total.roundToInt()}点",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "回復 ${score.recoveryScore.roundToInt()} / 振れ幅 ${score.swingScore.roundToInt()} / 安定 ${score.stabilityScore.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** フェーズごとに背景色分けした簡易な心拍推移の折れ線グラフ。 */
@Composable
private fun HeartRateChart(segments: List<PhaseSegment>, modifier: Modifier = Modifier) {
    val allSamples = segments.flatMap(PhaseSegment::samples).sortedBy { it.timestampMs }

    if (allSamples.size < 2) {
        Text(text = "グラフ表示に十分なデータがありません", style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }

    val minTime = allSamples.first().timestampMs
    val maxTime = allSamples.last().timestampMs
    val minBpm = allSamples.minOf { it.bpm }
    val maxBpm = allSamples.maxOf { it.bpm }
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        fun xOf(timestampMs: Long): Float = ((timestampMs - minTime).toFloat() / timeRange) * width
        fun yOf(bpm: Int): Float = height - ((bpm - minBpm).toFloat() / bpmRange) * height

        // フェーズごとの背景帯(視認性のため濃いめのアルファにしている)
        for (segment in segments) {
            val startX = xOf(segment.startMs)
            val endX = xOf(segment.endMs)
            drawRect(
                color = phaseColor(segment.phase).copy(alpha = 0.38f),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(width = (endX - startX).coerceAtLeast(1f), height = height),
            )
        }

        // 心拍折れ線
        for (i in 0 until allSamples.size - 1) {
            val a = allSamples[i]
            val b = allSamples[i + 1]
            drawLine(
                color = Color(0xFF880E4F),
                start = Offset(xOf(a.timestampMs), yOf(a.bpm)),
                end = Offset(xOf(b.timestampMs), yOf(b.bpm)),
                strokeWidth = 5f,
            )
        }
    }
}

private fun phaseColor(phase: SessionPhase): Color = when (phase) {
    SessionPhase.SAUNA -> Color(0xFFFF6D00)
    SessionPhase.COLD_BATH -> Color(0xFF0091EA)
    SessionPhase.REST -> Color(0xFF00C853)
}
