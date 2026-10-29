package com.ipv.pos.data.repo
import com.ipv.pos.data.db.IpvDatabase
class AuditWriter(private val db: IpvDatabase) {
    object Action { const val USER_MANAGE = "USER_MANAGE"; const val LOGIN_FAIL = "LOGIN_FAIL" }
    suspend fun log(userId: String?, action: String, entity: String? = null, entityId: String? = null,
        before: Any? = null, after: Any? = null, at: Long = System.currentTimeMillis()) {}
    companion object { fun obj(vararg fields: Pair<String, Any?>): Any = fields.toMap() }
}
