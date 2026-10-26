package com.ipv.pos.data.repo

import androidx.room.withTransaction
import com.ipv.pos.data.db.IpvDatabase
import com.ipv.pos.data.db.OutboxEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Patrón Outbox desde el MVP (arquitectura §5.1): toda escritura de negocio encola su payload
 * en la MISMA transacción SQLite. Nadie lo consume hasta Fase 2, pero así no hay migración
 * posterior y se garantiza que ningún cambio se pierde por quedar fuera de la cola.
 */
class OutboxWriter(private val db: IpvDatabase) {

    /** Debe llamarse DENTRO de [withTransaction] del llamador (misma transacción SQLite). */
    suspend inline fun <reified T> enqueue(
        entity: String,
        entityId: String,
        operation: Op,
        payload: T,
    ) {
        db.systemDao().insertOutbox(
            OutboxEntity(
                id = com.ipv.pos.domain.Uuidv7.new(),
                entity = entity,
                entity_id = entityId,
                operation = operation.name,
                payload_json = json.encodeToString(payload),
                queued_at = System.currentTimeMillis(),
            )
        )
    }

    /** Ejecuta [block] dentro de una transacción Room; cualquier excepción revierte TODO. */
    suspend fun <R> withTransaction(block: suspend () -> R): R = db.withTransaction(block)

    enum class Op { INSERT, UPDATE, DELETE }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
