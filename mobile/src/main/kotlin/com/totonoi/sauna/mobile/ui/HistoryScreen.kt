package com.totonoi.sauna.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.totonoi.sauna.mobile.theme.ThemeMode
import com.totonoi.sauna.mobile.theme.ThemePreferences
import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(), themePreferences: ThemePreferences? = null) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()
    var selectedSession by remember { mutableStateOf<SaunaSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<SaunaSession?>(null) }
    var selectedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showSelectedDeletionConfirmation by remember { mutableStateOf(false) }
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    val selectionMode = isSelectionMode

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri != null && csv != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(csv)
            }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("記録を削除しますか？") },
            text = { Text("このスマホの履歴から削除します。時計側の元データは削除されません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        sessionToDelete = null
                        selectedSession = null
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("キャンセル") }
            },
        )
    }

    if (showSelectedDeletionConfirmation) {
        val selectedCount = selectedSessionIds.size
        AlertDialog(
            onDismissRequest = { showSelectedDeletionConfirmation = false },
            title = { Text("${selectedCount}件の記録を削除しますか？") },
            text = { Text("このスマホの履歴から削除します。時計側の元データは削除されません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSessions(selectedSessionIds)
                        selectedSessionIds = emptySet()
                        showSelectedDeletionConfirmation = false
                        isSelectionMode = false
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showSelectedDeletionConfirmation = false }) { Text("キャンセル") }
            },
        )
    }

    val current = selectedSession
    if (current != null) {
        BackHandler(onBack = { selectedSession = null })
        SessionDetailScreen(
            session = current,
            onBack = { selectedSession = null },
            onDelete = { sessionToDelete = current },
        )
        return
    }

    BackHandler(enabled = selectionMode) {
        selectedSessionIds = emptySet()
        showSelectedDeletionConfirmation = false
        isSelectionMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "${selectedSessionIds.size}件を選択中" else "サウナ ととのい記録",
                    )
                },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                selectedSessionIds = if (selectedSessionIds.size == sessions.size) {
                                    emptySet()
                                } else {
                                    sessions.mapTo(linkedSetOf()) { it.id }
                                }
                            },
                        ) {
                            Text(if (selectedSessionIds.size == sessions.size) "選択解除" else "全選択")
                        }
                        TextButton(
                            enabled = selectedSessionIds.isNotEmpty(),
                            onClick = { showSelectedDeletionConfirmation = true },
                        ) { Text("削除") }
                        TextButton(
                            onClick = {
                                selectedSessionIds = emptySet()
                                isSelectionMode = false
                            },
                        ) { Text("完了") }
                    } else {
                        TextButton(onClick = { isSelectionMode = true }) { Text("選択") }
                        TextButton(
                            enabled = sessions.isNotEmpty(),
                            onClick = {
                                pendingExportCsv = sessions.toCsv()
                                exportLauncher.launch("sauna-sessions.csv")
                            },
                        ) { Text("CSV出力") }
                        if (themePreferences != null) ThemeMenuButton(themePreferences)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { NotificationPermissionBanner() }
            if (sessions.isEmpty()) {
                item { EmptyStateCard() }
            } else {
                items(sessions) { session ->
                        SessionCard(
                            session = session,
                            selectionMode = selectionMode,
                            isSelected = session.id in selectedSessionIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedSessionIds = if (session.id in selectedSessionIds) {
                                        selectedSessionIds - session.id
                                    } else {
                                        selectedSessionIds + session.id
                                    }
                                } else {
                                    selectedSession = session
                                }
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun ThemeMenuButton(themePreferences: ThemePreferences) {
    var expanded by remember { mutableStateOf(false) }
    val currentMode by themePreferences.themeMode.collectAsState()

    Row {
        TextButton(onClick = { expanded = true }) {
            Text(
                when (currentMode) {
                    ThemeMode.SYSTEM -> "表示: 端末設定"
                    ThemeMode.LIGHT -> "表示: ライト"
                    ThemeMode.DARK -> "表示: ダーク"
                },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("端末設定に合わせる") },
                onClick = { themePreferences.setThemeMode(ThemeMode.SYSTEM); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("ライトモード") },
                onClick = { themePreferences.setThemeMode(ThemeMode.LIGHT); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("ダークモード") },
                onClick = { themePreferences.setThemeMode(ThemeMode.DARK); expanded = false },
            )
        }
    }
}

/** 通知が届かず不安にならないよう、通知許可の状態をアプリ内で明示的に確認できるようにする。 */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    if (granted) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "通知が許可されていません", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "このままだと時計からの受信が完了しても通知が表示されません。許可すると受信完了がすぐ分かります。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("通知を許可する")
                }
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                ) {
                    Text("設定を開く")
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
private fun SessionCard(
    session: SaunaSession,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
    val stats = remember(session) { session.toDetailStats() }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                }
                Text(text = dateFormat.format(Date(session.startMs)), style = MaterialTheme.typography.bodyMedium)
            }
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

private fun List<SaunaSession>.toCsv(): String = buildString {
    appendLine("session_id,start_ms,end_ms,score,cycle_count,phase,phase_start_ms,phase_end_ms,sample_timestamp_ms,bpm")
    this@toCsv.forEach { session ->
        session.segments.forEach { segment ->
            segment.samples.forEach { sample ->
                appendLine(
                    listOf(
                        session.id,
                        session.startMs,
                        session.endMs,
                        session.totonoiScore,
                        session.cycleCount,
                        segment.phase.name,
                        segment.startMs,
                        segment.endMs,
                        sample.timestampMs,
                        sample.bpm,
                    ).joinToString(",") { it.toString().csvEscape() },
                )
            }
        }
    }
}

private fun String.csvEscape(): String = if (contains(',') || contains('"') || contains('\n')) {
    "\"${replace("\"", "\"\"")}\""
} else {
    this
}
