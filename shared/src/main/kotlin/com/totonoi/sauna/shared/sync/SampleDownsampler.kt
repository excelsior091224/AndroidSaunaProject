package com.totonoi.sauna.shared.sync

import com.totonoi.sauna.shared.model.HeartRateSample
import com.totonoi.sauna.shared.model.PhaseSegment

/**
 * Wear→MobileのData Layer送信サイズを抑えるため、心拍サンプルを均等間引きする。
 * 大量セット計測(数百〜千サンプル)時の同期遅延対策。時計側のRoom DBには元データをそのまま保存し、
 * 送信時にのみ間引く。
 */
object SampleDownsampler {

    /** 1フェーズあたりの最大サンプル数。グラフ表示に十分な密度を保ちつつサイズを抑える。 */
    private const val MAX_SAMPLES_PER_SEGMENT = 150

    fun downsampleForTransfer(segments: List<PhaseSegment>): List<PhaseSegment> =
        segments.map { segment ->
            segment.copy(samples = downsample(segment.samples, MAX_SAMPLES_PER_SEGMENT))
        }

    private fun downsample(samples: List<HeartRateSample>, maxCount: Int): List<HeartRateSample> {
        if (samples.size <= maxCount) return samples

        val step = samples.size.toDouble() / maxCount
        return (0 until maxCount).map { i -> samples[(i * step).toInt().coerceAtMost(samples.size - 1)] }
    }
}
