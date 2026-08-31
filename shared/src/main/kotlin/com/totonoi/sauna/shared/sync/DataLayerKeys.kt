package com.totonoi.sauna.shared.sync

/** Wear ⇔ Mobile 間でWearable Data Layer APIを使ってセッションをやり取りする際のパス/キー定義。 */
object DataLayerKeys {
    const val SESSION_PATH_PREFIX = "/sauna/session"
    const val SESSION_NOTIFICATION_PATH = "$SESSION_PATH_PREFIX/notification"
    const val SESSION_ACK_PATH = "$SESSION_PATH_PREFIX/ack"
    const val KEY_SESSION_ID = "session_id"
    const val KEY_START_MS = "start_ms"
    const val KEY_END_MS = "end_ms"
    const val KEY_TOTONOI_SCORE = "totonoi_score"
    const val KEY_CYCLE_COUNT = "cycle_count"
    const val KEY_SEGMENTS_JSON = "segments_json"
    const val KEY_SYNC_ATTEMPT_MS = "sync_attempt_ms"
}
