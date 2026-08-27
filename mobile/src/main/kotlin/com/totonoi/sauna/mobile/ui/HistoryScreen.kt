package com.totonoi.sauna.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsState()
    var selectedSession by remember { mutableStateOf<SaunaSession?>(null) }

    val current = selectedSession
    if (current != null) {
        BackHandler(onBack = { selectedSession = null })
        SessionDetailScreen(session = current, onBack = { selectedSession = null })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("サウナ ととのい記録") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sessions.isEmpty()) {
                item { EmptyStateCard() }
            } else {
                items(sessions) { session ->
                    SessionCard(session = session, onClick = { selectedSession = session })
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(modifier = Modifier.padding(4.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "まだ計測データがありません", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "時計でセッションを終了すると自動で受信して一覧に追加されます。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionCard(session: SaunaSession, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    val stats = remember(session) { session.toDetailStats() }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = dateFormat.format(Date(session.startMs)), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "ととのい値 ${session.totonoiScore.toInt()} / ${session.cycleCount}セット",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(text = "計測時間 ${stats.totalDurationMin}分", style = MaterialTheme.typography.bodyMedium)
            Text(text = "心拍数 ${stats.avgBpm} bpm (min ${stats.minBpm} / max ${stats.maxBpm})", style = MaterialTheme.typography.bodyMedium)
            Text(text = "サンプル数 ${stats.sampleCount}", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()

            Text(text = "フェーズ別", style = MaterialTheme.typography.titleSmall)
            Text(text = "サウナ ${stats.saunaMin}分 / 水風呂 ${stats.coldMin}分 / 休憩 ${stats.restMin}分", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal data class DetailStats(
    val totalDurationMin: Long,
    val sampleCount: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val avgBpm: Int,
    val saunaMin: Long,
    val coldMin: Long,
    val restMin: Long,
)

internal fun SaunaSession.toDetailStats(): DetailStats {
    val samples = segments.flatMap(PhaseSegment::samples)
    val sampleCount = samples.size

    val avgBpm = if (samples.isEmpty()) 0 else samples.map(HeartRateSample::bpm).average().roundToInt()
    val minBpm = samples.minOfOrNull(HeartRateSample::bpm) ?: 0
    val maxBpm = samples.maxOfOrNull(HeartRateSample::bpm) ?: 0

    val totalDurationMin = ((endMs - startMs).coerceAtLeast(0L) / 1000L / 60L)

    fun durationMin(phase: com.totonoi.sauna.shared.model.SessionPhase): Long {
        val ms = segments
            .filter { it.phase == phase }
            .sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        return ms / 1000L / 60L
    }

    return DetailStats(
        totalDurationMin = totalDurationMin,
        sampleCount = sampleCount,
        minBpm = minBpm,
        maxBpm = maxBpm,
        avgBpm = avgBpm,
        saunaMin = durationMin(com.totonoi.sauna.shared.model.SessionPhase.SAUNA),
        coldMin = durationMin(com.totonoi.sauna.shared.model.SessionPhase.COLD_BATH),
        restMin = durationMin(com.totonoi.sauna.shared.model.SessionPhase.REST),
    )
}
