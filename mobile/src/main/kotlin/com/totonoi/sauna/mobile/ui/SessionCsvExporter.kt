package com.totonoi.sauna.mobile.ui

import com.totonoi.sauna.shared.model.SaunaSession

internal fun List<SaunaSession>.toCsv(): String = buildString {
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
