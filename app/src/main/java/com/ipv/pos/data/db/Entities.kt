package com.ipv.pos.data.db
data class UserEntity(val id: String, val name: String, val role: String, val pin_hash: String, val pin_salt: String,
    val failed_attempts: Int = 0, val locked_until: Long? = null, val active: Int = 1,
    val created_at: Long, val updated_at: Long, val version: Int = 1, val deleted: Int = 0, val sync_state: String = "pending")
data class SchemaMetaEntity(val key: String, val value: String)
