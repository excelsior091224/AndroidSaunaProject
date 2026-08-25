package com.totonoi.sauna.shared.calculator

import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SessionPhase
import kotlin.math.abs

/**
 * 1サイクル(サウナ→水風呂→休憩)ごとの内訳スコア。0〜100に正規化した合計値を [total] で返す。
 */
data class CycleScore(
    val cycleIndex: Int,
    /** 休憩開始後、心拍が落ち着くまでの速さ(副交感神経の立ち上がりの速さ) 0〜40点 */
    val recoveryScore: Double,
    /** 温冷交代による心拍の振れ幅(交感神経→副交感神経の切り替えの大きさ) 0〜30点 */
    val swingScore: Double,
    /** 休憩後半での心拍の安定度(整った状態の持続) 0〜30点 */
    val stabilityScore: Double,
) {
    val total: Double get() = (recoveryScore + swingScore + stabilityScore).coerceIn(0.0, 100.0)
}

data class TotonoiResult(
    val totalScore: Double,
    val cycleScores: List<CycleScore>,
    val cycleCount: Int,
)

/**
 * 手首PPGの心拍数(bpm)の時系列だけを入力として「ととのい値」を推定するヒューリスティックな計算器。
 *
 * 本来のHRV(心拍変動)解析はRR間隔の精度が必要だが、Wear OSの心拍センサーはbpm値しか
 * 安定して取得できないため、代わりに以下3つの代理指標を用いる:
 *
 * 1. 回復速度 (recoveryScore): 水風呂を出て休憩に入ってから心拍が落ち着く(直近の最小値付近に収束する)
 *    までの時間。速いほど副交感神経の立ち上がりが強く、ととのいやすいとされる。
 * 2. 振れ幅 (swingScore): 温冷交代で生じた心拍の最大-最小差。寒暖差刺激が大きく自律神経が
 *    大きく揺さぶられているほど、その後の反動(ととのい)も大きくなりやすい。
 * 3. 安定度 (stabilityScore): 休憩後半での心拍の変動係数とトレンド傾きの小ささ。乱高下せず
 *    落ち着いた状態が続いているかを見る。
 *
 * これらはサウォッチ等の公開アルゴリズムを模したものではなく、独自に設計した推定モデル。
 * 実測データで係数を調整していくことを想定している。
 */
object TotonoiCalculator {

    fun calculate(segments: List<PhaseSegment>): TotonoiResult {
        val cycles = groupIntoCycles(segments)
        if (cycles.isEmpty()) {
            return TotonoiResult(totalScore = 0.0, cycleScores = emptyList(), cycleCount = 0)
        }

        val cycleScores = cycles.mapIndexed { index, cycle -> scoreCycle(index, cycle) }
        val average = cycleScores.map { it.total }.average()
        // 複数サイクルをこなすほど加点(2セット目以降+3点、最大+9点)
        val cycleBonus = (cycles.size - 1).coerceAtMost(3) * 3.0
        val totalScore = (average + cycleBonus).coerceIn(0.0, 100.0)

        return TotonoiResult(totalScore = totalScore, cycleScores = cycleScores, cycleCount = cycles.size)
    }

    /** サウナ→水風呂→休憩の並びが揃っているものを1サイクルとして抽出する。 */
    private fun groupIntoCycles(segments: List<PhaseSegment>): List<Triple<PhaseSegment, PhaseSegment, PhaseSegment>> {
        val cycles = mutableListOf<Triple<PhaseSegment, PhaseSegment, PhaseSegment>>()
        var i = 0
        while (i <= segments.size - 3) {
            val (a, b, c) = Triple(segments[i], segments[i + 1], segments[i + 2])
            if (a.phase == SessionPhase.SAUNA && b.phase == SessionPhase.COLD_BATH && c.phase == SessionPhase.REST) {
                cycles += Triple(a, b, c)
                i += 3
            } else {
                i += 1
            }
        }
        return cycles
    }

    private fun scoreCycle(index: Int, cycle: Triple<PhaseSegment, PhaseSegment, PhaseSegment>): CycleScore {
        val (sauna, coldBath, rest) = cycle
        val restSamples = rest.samples

        val recoveryScore = if (restSamples.size >= 3) recoveryScore(restSamples) else 0.0
        val swingScore = swingScore(sauna, coldBath, rest)
        val stabilityScore = if (restSamples.size >= 4) stabilityScore(restSamples) else 0.0

        return CycleScore(index, recoveryScore, swingScore, stabilityScore)
    }

    private fun recoveryScore(restSamples: List<HeartRateSample>): Double {
        val startBpm = restSamples.take(3).map { it.bpm }.average()
        // 落ち着き先の目安として、休憩終盤20%区間の平均bpmを使う
        val tailCount = (restSamples.size * 0.2).toInt().coerceAtLeast(2)
        val settledBpm = restSamples.takeLast(tailCount).map { it.bpm }.average()
        val threshold = settledBpm + abs(startBpm - settledBpm) * 0.1

        val recoveredSample = restSamples.firstOrNull { it.bpm <= threshold } ?: restSamples.last()
        val recoveryTimeSec = (recoveredSample.timestampMs - restSamples.first().timestampMs) / 1000.0

        // 20秒以内で満点、180秒以上でゼロ点の線形マッピング
        val ratio = ((180.0 - recoveryTimeSec) / (180.0 - 20.0)).coerceIn(0.0, 1.0)
        return ratio * 40.0
    }

    private fun swingScore(sauna: PhaseSegment, coldBath: PhaseSegment, rest: PhaseSegment): Double {
        val peakBpm = (sauna.samples + coldBath.samples).maxOfOrNull { it.bpm } ?: return 0.0
        val tailCount = (rest.samples.size * 0.2).toInt().coerceAtLeast(1)
        val troughBpm = rest.samples.takeLast(tailCount).map { it.bpm }.average()

        val amplitude = peakBpm - troughBpm
        // 20bpm〜80bpmの振れ幅を0〜30点にマッピング
        val ratio = ((amplitude - 20.0) / (80.0 - 20.0)).coerceIn(0.0, 1.0)
        return ratio * 30.0
    }

    private fun stabilityScore(restSamples: List<HeartRateSample>): Double {
        val settledCount = (restSamples.size * 0.6).toInt().coerceAtLeast(4)
        val settled = restSamples.takeLast(settledCount)

        val slope = linearRegressionSlopePerSecond(settled)
        // 傾きが0に近いほど安定。0.05bpm/秒以下で満点、0.5bpm/秒以上でゼロ点
        val ratio = ((0.5 - abs(slope)) / (0.5 - 0.05)).coerceIn(0.0, 1.0)
        return ratio * 30.0
    }

    /** 心拍(bpm) vs 経過秒 の単回帰直線の傾きを求める。 */
    private fun linearRegressionSlopePerSecond(samples: List<HeartRateSample>): Double {
        if (samples.size < 2) return 0.0
        val t0 = samples.first().timestampMs
        val xs = samples.map { (it.timestampMs - t0) / 1000.0 }
        val ys = samples.map { it.bpm.toDouble() }

        val n = xs.size
        val meanX = xs.average()
        val meanY = ys.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            numerator += dx * (ys[i] - meanY)
            denominator += dx * dx
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}
