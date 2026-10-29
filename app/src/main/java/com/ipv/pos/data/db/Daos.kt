package com.ipv.pos.data.db
class UsersDao {
    private val map = mutableMapOf<String, UserEntity>()
    suspend fun insert(e: UserEntity) { map[e.id] = e }
    suspend fun update(e: UserEntity) { map[e.id] = e }
    suspend fun byId(id: String): UserEntity? = map[id]
    suspend fun countActive(): Int = map.values.count { it.deleted == 0 && it.active == 1 }
}
class SystemDao {
    private val metas = mutableMapOf<String, String>()
    suspend fun upsertMeta(e: SchemaMetaEntity) { metas[e.key] = e.value }
    suspend fun meta(key: String): String? = metas[key]
    val audits = mutableListOf<String>()
    suspend fun insertAudit(e: AuditLogStub) { audits.add(e.action) }
}
data class AuditLogStub(val action: String)
