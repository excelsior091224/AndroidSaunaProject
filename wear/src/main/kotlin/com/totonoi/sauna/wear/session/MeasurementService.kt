package com.totonoi.sauna.wear.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.totonoi.sauna.shared.calculator.TotonoiCalculator
import com.totonoi.sauna.shared.db.SaunaDatabase
import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import com.totonoi.sauna.shared.model.SessionPhase
import com.totonoi.sauna.shared.repository.RoomSaunaSessionRepository
import com.totonoi.sauna.wear.MainActivity
import com.totonoi.sauna.wear.health.HeartRateMeasurer
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 心拍計測をActivity/ViewModelのライフサイクルから切り離し、Foreground Serviceとして実行する。
 *
 * 背景: 計測処理がActivityに紐づいていると、画面消灯や更衣室⇔浴場間の移動でシステムに
 * プロセスごと停止され、心拍計測の途中停止やスマホへの送信失敗を引き起こす。
 * Foreground Service + 常時通知にすることでOSに殺されにくくし、同時に計測中通知の要望も満たす。
 */
class MeasurementService : Service() {

    companion object {
        private const val ACTION_START = "com.totonoi.sauna.wear.action.START"
        private const val ACTION_SWITCH_PHASE = "com.totonoi.sauna.wear.action.SWITCH_PHASE"
        private const val ACTION_END = "com.totonoi.sauna.wear.action.END"
        private const val EXTRA_PHASE = "phase"

        private const val NOTIFICATION_CHANNEL_ID = "measurement"
        private const val NOTIFICATION_ID = 1001

        private val _uiState = MutableStateFlow(SessionUiState())
        val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, MeasurementService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun switchPhase(context: Context, phase: SessionPhase) {
            val intent = Intent(context, MeasurementService::class.java)
                .setAction(ACTION_SWITCH_PHASE)
                .putExtra(EXTRA_PHASE, phase.name)
            context.startForegroundService(intent)
        }

        fun end(context: Context) {
            val intent = Intent(context, MeasurementService::class.java).setAction(ACTION_END)
            context.startForegroundService(intent)
        }

        fun resetToHome() {
            _uiState.value = SessionUiState()
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var heartRateMeasurer: HeartRateMeasurer
    private lateinit var repository: RoomSaunaSessionRepository
    private lateinit var syncSender: SessionSyncSender

    private var measureJob: Job? = null
    private var sessionStartMs = 0L
    private val completedSegments = mutableListOf<PhaseSegment>()
    private var currentSegmentStartMs = 0L
    private var currentSamples = mutableListOf<HeartRateSample>()

    override fun onCreate() {
        super.onCreate()
        heartRateMeasurer = HeartRateMeasurer(this)
        repository = RoomSaunaSessionRepository(SaunaDatabase.getInstance(this).saunaDao())
        syncSender = SessionSyncSender(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_SWITCH_PHASE -> {
                val phase = intent.getStringExtra(EXTRA_PHASE)?.let { SessionPhase.valueOf(it) }
                if (phase != null) handleSwitchPhase(phase)
            }
            ACTION_END -> handleEnd()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun handleStart() {
        sessionStartMs = System.currentTimeMillis()
        completedSegments.clear()
        beginPhase(SessionPhase.SAUNA)
        _uiState.value = _uiState.value.copy(screen = SaunaScreen.Measuring, currentPhase = SessionPhase.SAUNA)
        startForeground(NOTIFICATION_ID, buildNotification())

        measureJob = scope.launch {
            heartRateMeasurer.heartRateBpmFlow().collect { bpm ->
                currentSamples.add(HeartRateSample(System.currentTimeMillis(), bpm))
                _uiState.value = _uiState.value.copy(latestBpm = bpm)
                updateNotification()
            }
        }
    }

    private fun handleSwitchPhase(nextPhase: SessionPhase) {
        finishCurrentSegment()
        beginPhase(nextPhase)
        updateNotification()
    }

    private fun handleEnd() {
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

        scope.launch {
            repository.saveSession(session)
            runCatching { syncSender.sendSession(session) }
            // Data Layerへのローカル投入自体はネットワーク未接続でも完了するため、
            // 送信失敗時もローカル保存は残る。再接続時にOS側で自動同期される。

            _uiState.value = _uiState.value.copy(screen = SaunaScreen.Result(result), currentPhase = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "サウナ計測中",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val phase = _uiState.value.currentPhase
        val bpm = _uiState.value.latestBpm
        val phaseLabel = when (phase) {
            SessionPhase.SAUNA -> "サウナ"
            SessionPhase.COLD_BATH -> "水風呂"
            SessionPhase.REST -> "休憩"
            null -> "計測中"
        }
        val bpmText = if (bpm != null) "$bpm bpm" else "心拍取得中..."

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("サウナ計測中: $phaseLabel")
            .setContentText(bpmText)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
