package com.ipv.pos.data.repo

import com.ipv.pos.data.db.AuditLogEntity
import com.ipv.pos.data.db.IpvDatabase
import com.ipv.pos.domain.Uuidv7
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Auditoría inmutable (spec §4.9): se escribe en la misma transacción que la operación auditada.
 * before/after como JSON snapshot — esencial para confianza del dueño con varios empleados.
 */
class AuditWriter(private val db: IpvDatabase) {

    object Action {
        const val VOID_SALE = "VOID_SALE"
        const val RETURN_SALE = "RETURN_SALE"
        const val ADJUST_STOCK = "ADJUST_STOCK"
        const val PRICE_CHANGE = "PRICE_CHANGE"
        const val USER_MANAGE = "USER_MANAGE"
        const val ROLE_CHANGE = "ROLE_CHANGE"
        const val RESTORE_BACKUP = "RESTORE_BACKUP"
        const val EXPORT_BACKUP = "EXPORT_BACKUP"
        const val CASH_OPEN = "CASH_OPEN"
        const val CASH_CLOSE = "CASH_CLOSE"
        const val LOGIN_FAIL = "LOGIN_FAIL"
        const val LICENSE_ACTIVATE = "LICENSE_ACTIVATE"
        const val INVOICE_VOID = "INVOICE_VOID"
    }

    suspend fun log(
        userId: String?,
        action: String,
        entity: String? = null,
        entityId: String? = null,
        before: JsonObject? = null,
        after: JsonObject? = null,
        at: Long = System.currentTimeMillis(),
    ) {
        // Dentro de la transacción del llamador (misma unidad atómica que el cambio).
        db.systemDao().insertAudit(
            AuditLogEntity(
                id = Uuidv7.new(at),
                user_id = userId,
                action = action,
                entity = entity,
                entity_id = entityId,
                before_json = before?.toString(),
                after_json = after?.toString(),
                at = at,
            )
        )
    }

    companion object {
        fun obj(vararg fields: Pair<String, Any?>): JsonObject = buildJsonObject {
            fields.forEach { (k, v) -> if (v == null) put(k, "") else when (v) {
                is Number -> put(k, v.toLong().takeIf { v % 1 == 0 && v is Long } ?: v.toDouble())
                is Boolean -> put(k, v)
                else -> put(k, v.toString())
            } }
        }
    }
}
