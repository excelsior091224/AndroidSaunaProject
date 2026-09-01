package com.totonoi.sauna.shared.repository

import com.totonoi.sauna.shared.db.SaunaDao
import com.totonoi.sauna.shared.db.SaunaSessionEntity
import com.totonoi.sauna.shared.model.PhaseSegment
import com.totonoi.sauna.shared.model.SaunaSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SaunaSessionRepository {
    fun observeSessions(): Flow<List<SaunaSession>>
    suspend fun getSession(id: String): SaunaSession?
    suspend fun saveSession(session: SaunaSession)
    suspend fun deleteSession(id: String)
}

class RoomSaunaSessionRepository(private val dao: SaunaDao) : SaunaSessionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeSessions(): Flow<List<SaunaSession>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain(json) } }

    override suspend fun getSession(id: String): SaunaSession? =
        dao.getById(id)?.toDomain(json)

    override suspend fun saveSession(session: SaunaSession) {
        dao.upsert(session.toEntity(json))
    }

    override suspend fun deleteSession(id: String) {
        dao.deleteById(id)
    }

    private fun SaunaSessionEntity.toDomain(json: Json): SaunaSession =
        SaunaSession(
            id = id,
            startMs = startMs,
            endMs = endMs,
            segments = json.decodeFromString(segmentsJson),
            totonoiScore = totonoiScore,
            cycleCount = cycleCount,
        )

    private fun SaunaSession.toEntity(json: Json): SaunaSessionEntity =
        SaunaSessionEntity(
            id = id,
            startMs = startMs,
            endMs = endMs,
            totonoiScore = totonoiScore,
            cycleCount = cycleCount,
            segmentsJson = json.encodeToString<List<PhaseSegment>>(segments),
        )
}
