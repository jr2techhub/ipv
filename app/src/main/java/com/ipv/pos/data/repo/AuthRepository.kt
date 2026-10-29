package com.ipv.pos.data.repo

import com.ipv.pos.data.db.IpvDatabase
import com.ipv.pos.data.db.UserEntity
import com.ipv.pos.domain.LoginLockoutPolicy
import com.ipv.pos.domain.PinHasher
import com.ipv.pos.domain.Uuidv7
import com.ipv.pos.domain.UserRole
import com.ipv.pos.domain.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Autenticación local (Sprint 0): login por usuario + PIN PBKDF2, bloqueo tras 5 intentos,
 * LOGIN_FAIL en auditoría. Sin red, sin dependencias externas.
 */
class AuthRepository(
    private val db: IpvDatabase,
    private val outbox: OutboxWriter,
    private val audit: AuditWriter,
) {
    private val users get() = db.usersDao()
    private val system get() = db.systemDao()

    /** Onboarding primer arranque: crea el ADMIN inicial. Solo si NO existe ningún usuario. */
    suspend fun bootstrapAdmin(name: String, pin: String): Result<UserEntity> = runCatching {
        require(db.usersDao().countActive() == 0) { "Ya existen usuarios" }
        require(name.isNotBlank()) { "Nombre requerido" }
        val salt = PinHasher.newSalt()
        val now = System.currentTimeMillis()
        val user = UserEntity(
            id = Uuidv7.new(now),
            name = name.trim(),
            role = UserRole.ADMIN.name,
            pin_hash = PinHasher.hash(pin, salt),
            pin_salt = salt.toHex(),
            created_at = now, updated_at = now,
        )
        outbox.withTransaction {
            users.insert(user)
            outbox.enqueue("users", user.id, OutboxWriter.Op.INSERT, user)
            audit.log(user.id, AuditWriter.Action.USER_MANAGE, "users", user.id,
                after = AuditWriter.obj("name" to user.name, "role" to "ADMIN", "bootstrap" to true))
            // device_id se fija una sola vez por instalación (schema_meta, arquitectura §13)
            if (system.meta("device_id") == null) {
                system.upsertMeta(com.ipv.pos.data.db.SchemaMetaEntity("device_id", Uuidv7.new()))
                system.upsertMeta(com.ipv.pos.data.db.SchemaMetaEntity("install_date", now.toString()))
            }
        }
        user
    }

    sealed interface LoginResult {
        data class Success(val user: UserEntity) : LoginResult
        data object WrongPin : LoginResult
        data class Locked(val untilMs: Long) : LoginResult
        data object UnknownUser : LoginResult
    }

    suspend fun login(userId: String, pin: String, nowMs: Long = System.currentTimeMillis()): LoginResult {
        val user = users.byId(userId) ?: return LoginResult.UnknownUser
        if (user.active == 0 || user.deleted == 1) return LoginResult.UnknownUser

        val state = LoginLockoutPolicy.State(user.failed_attempts, user.locked_until)
        if (LoginLockoutPolicy.isLocked(state, nowMs)) {
            return LoginResult.Locked(user.locked_until!!)
        }

        // PBKDF2 (120k iteraciones ≈ decenas de ms en gama baja): se deriva a Dispatchers.Default
        // para no bloquear el hilo principal si el llamador corre en Main. withContext es
        // transparente cuando ya se está fuera de Main (no añade latencia perceptible).
        val pinOk = withContext(Dispatchers.Default) { PinHasher.verify(pin, user.pin_hash) }
        if (pinOk) {
            val clean = LoginLockoutPolicy.onLoginSuccess()
            if (user.failed_attempts != 0 || user.locked_until != null) {
                users.update(user.copy(failed_attempts = clean.failedAttempts,
                    locked_until = clean.lockedUntil, updated_at = nowMs, version = user.version + 1))
            }
            return LoginResult.Success(user)
        }

        // PIN incorrecto → contador/bloqueo + auditoría
        val (newState, justLocked) = LoginLockoutPolicy.attemptAfterFailure(state, nowMs)
        users.update(user.copy(failed_attempts = newState.failedAttempts,
            locked_until = newState.lockedUntil, updated_at = nowMs, version = user.version + 1))
        outbox.withTransaction {
            audit.log(userId, AuditWriter.Action.LOGIN_FAIL, "users", userId,
                after = AuditWriter.obj("locked" to justLocked))
        }
        return if (justLocked) LoginResult.Locked(newState.lockedUntil!!) else LoginResult.WrongPin
    }

    /** Cambio de PIN (solo ADMIN lo invoca; el permiso se valida en la capa de casos de uso). */
    suspend fun changePin(adminUserId: String, targetUserId: String, newPin: String): Result<Unit> = runCatching {
        val target = users.byId(targetUserId) ?: error("Usuario inexistente")
        val salt = PinHasher.newSalt()
        val now = System.currentTimeMillis()
        outbox.withTransaction {
            users.update(target.copy(
                pin_hash = PinHasher.hash(newPin, salt),
                pin_salt = salt.toHex(),
                failed_attempts = 0, locked_until = null,
                updated_at = now, version = target.version + 1,
            ))
            outbox.enqueue("users", targetUserId, OutboxWriter.Op.UPDATE, target.copy(pin_hash = "***"))
            audit.log(adminUserId, AuditWriter.Action.USER_MANAGE, "users", targetUserId,
                after = AuditWriter.obj("change" to "pin"))
        }
    }
}
