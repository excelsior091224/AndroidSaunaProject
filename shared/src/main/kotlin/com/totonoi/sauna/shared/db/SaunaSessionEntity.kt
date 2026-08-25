package com.totonoi.sauna.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * セッション1件分のレコード。フェーズ・心拍サンプルの詳細は [segmentsJson] にJSONとして格納する
 * (Wear/Mobile間でDataLayer経由でやり取りする形式とそのまま揃えるため)。
 */
@Entity(tableName = "sauna_sessions")
data class SaunaSessionEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val totonoiScore: Double,
    val cycleCount: Int,
    val segmentsJson: String,
)
