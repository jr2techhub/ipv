package com.ipv.pos.data.repo
import com.ipv.pos.data.db.IpvDatabase
class OutboxWriter(private val db: IpvDatabase) {
    suspend inline fun <reified T> enqueue(entity: String, entityId: String, operation: Op, payload: T) {}
    suspend fun <R> withTransaction(block: suspend () -> R): R = block()
    enum class Op { INSERT, UPDATE, DELETE }
}
