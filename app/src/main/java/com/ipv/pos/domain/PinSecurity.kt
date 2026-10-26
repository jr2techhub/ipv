package com.ipv.pos.domain

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de PIN con salt por usuario (spec §2): PBKDF2-HMAC-SHA256, 120k iteraciones,
 * 16 bytes salt, 256 bits derivadas. Implementación JDK pura → funciona en Android 7+
 * sin librerías extra y es idéntica en tests JVM.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): String {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN debe tener 4-6 dígitos" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return "$ITERATIONS:" + salt.toHex() + ":" + derived.toHex()
    }

    /** Verificación en tiempo constante (evita timing attacks sobre intentos de PIN). */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = parts[1].hexToBytes() ?: return false
        val expected = parts[2].hexToBytes() ?: return false
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
        val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return constantTimeEquals(actual, expected)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            null
        }
    }
}

/** Política de bloqueo por intentos fallidos (spec §2): 5 fallos → 5 minutos, registrado en auditoría. */
object LoginLockoutPolicy {
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCK_DURATION_MS = 5 * 60 * 1000L

    data class State(val failedAttempts: Int, val lockedUntil: Long?)

    fun onLoginFailed(nowMs: Long): State =
        State(failedAttempts = 0, lockedUntil = nowMs + LOCK_DURATION_MS) // al llegar a MAX

    fun attemptAfterFailure(state: State, nowMs: Long): Pair<State, Boolean /*lockedNow*/> {
        val attempts = state.failedAttempts + 1
        return if (attempts >= MAX_FAILED_ATTEMPTS)
            State(0, nowMs + LOCK_DURATION_MS) to true
        else
            State(attempts, state.lockedUntil) to false
    }

    fun isLocked(state: State, nowMs: Long): Boolean =
        state.lockedUntil != null && nowMs < state.lockedUntil

    fun onLoginSuccess(): State = State(0, null)
}
