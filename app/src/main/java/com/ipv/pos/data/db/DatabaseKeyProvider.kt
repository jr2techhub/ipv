package com.ipv.pos.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Llave de cifrado SQLCipher derivada del Android Keystore (arquitectura §8):
 * - Se genera una data key AES-256 con un flag aleatorio y se cifra con la clave maestra
 *   del Keystore (encrypt/decrypt in-device; la clave maestra nunca sale del hardware).
 * - La blob cifrada se guarda en SharedPreferences. Si el Keystore pierde la clave
 *   (factory reset / desinstalación), la BD es irrecuperable — comportamiento esperado:
 *   protege los datos del negocio en dispositivos compartidos.
 */
object DatabaseKeyProvider {
    private const val PREFS = "ipv_db_keys"
    private const val KEY_ENCRYPTED_BLOB = "db_key_enc_v1"
    private const val MASTER_ALIAS = "ipv_pos_master_key"

    fun getOrCreateDataKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ENCRYPTED_BLOB, null)
        return if (stored != null) {
            decryptWithMaster(stored)
        } else {
            val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            prefs.edit().putString(KEY_ENCRYPTED_BLOB, encryptWithMaster(fresh)).apply()
            fresh
        }
    }

    /** Factory Room con SQLCipher (net.zetetic:sqlcipher-android 4.5.x). */
    fun sqlCipherFactory(context: Context): androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher") // requerido por sqlcipher-android antes de abrir
        val key = getOrCreateDataKey(context)
        return SupportOpenHelperFactory(key)
    }

    // --- Keystore maestro AES-GCM ---

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encryptWithMaster(data: ByteArray): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        return android.util.Base64.encodeToString(iv + ct, android.util.Base64.NO_WRAP)
    }

    private fun decryptWithMaster(blob: String): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val raw = android.util.Base64.decode(blob, android.util.Base64.NO_WRAP)
        val spec = javax.crypto.spec.GCMParameterSpec(128, raw, 0, 12)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, masterKey(), spec)
        return cipher.doFinal(raw, 12, raw.size - 12)
    }
}
