package com.totonoi.sauna.shared.model

import kotlinx.serialization.Serializable

/** サウナ入浴中のフェーズ。ユーザーがWear側で手動タグ付けする想定。 */
@Serializable
enum class SessionPhase {
    SAUNA,
    COLD_BATH,
    REST,
}

@Serializable
data class HeartRateSample(
    val timestampMs: Long,
    val bpm: Int,
)

/** 1つのフェーズ(例: 休憩/外気浴)の開始〜終了と、その間の心拍サンプル列。 */
@Serializable
data class PhaseSegment(
    val phase: SessionPhase,
    val startMs: Long,
    val endMs: Long,
    val samples: List<HeartRateSample>,
)

/** 1回のサウナセッション全体(複数セット分のフェーズ列)。 */
@Serializable
data class SaunaSession(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val segments: List<PhaseSegment>,
    val totonoiScore: Double,
    val cycleCount: Int,
)
