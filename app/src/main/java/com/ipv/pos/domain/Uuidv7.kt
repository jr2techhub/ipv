package com.ipv.pos.domain

import java.security.SecureRandom

/**
 * UUIDv7 generado en dispositivo (ADR-001 §2): 48 bits de timestamp ms + versión 7
 * + variante RFC 9562 + 74 bits aleatorios. Ordenable por tiempo → índices SQLite eficientes.
 * Sin dependencias externas (~30 líneas, requisito de APK liviano).
 */
object Uuidv7 {
    private val random = SecureRandom()

    fun new(nowMs: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        // 48-bit big-endian unix_ts_ms
        bytes[0] = (nowMs shr 40).toByte()
        bytes[1] = (nowMs shr 32).toByte()
        bytes[2] = (nowMs shr 24).toByte()
        bytes[3] = (nowMs shr 16).toByte()
        bytes[4] = (nowMs shr 8).toByte()
        bytes[5] = nowMs.toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // ver=7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variante RFC9562
        return buildString(36) {
            for (i in bytes.indices) {
                if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                val v = bytes[i].toInt() and 0xFF
                append(HEX[v ushr 4]); append(HEX[v and 0x0F])
            }
        }
    }

    /** Extrae el timestamp de creación (para ordenamiento/debug y reloj lógico híbrido Fase 2). */
    fun timestampOf(id: String): Long {
        val hex = id.replace("-", "").substring(0, 12)
        return hex.toLong(16)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
