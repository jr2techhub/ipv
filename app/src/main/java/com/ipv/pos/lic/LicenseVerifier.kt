package com.ipv.pos.lic

import android.content.Context
import com.google.crypto.tink.subtle.Ed25519Verify
import com.ipv.pos.domain.LicenseState
import com.ipv.pos.domain.LicenseTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Licenciamiento offline (arquitectura §9, spec §4.8): archivo .ipvlicense =
 *   payload JSON canónico + "\n.sig:" + base64(Ed25519(payload)).
 * La app embebe SOLO la clave pública del emisor; la privada vive en tools/license-issuer
 * y NUNCA se distribuye. Sin phone-home: la verificación es 100% local.
 */
object LicenseVerifier {

    /** Clave pública Ed25519 del emisor IPV (base64, 32 bytes). Test key de desarrollo —
     *  reemplazar por la clave de producción antes del release (generar con issuer --genkey). */
    const val PUBLIC_KEY_B64 = "CHANGE_ME_PROD_PUBLIC_KEY"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class LicensePayload(
        val business: String,
        val tier: String,                    // FREE|PREMIUM
        val modules: List<String> = emptyList(),
        val deviceId: String,
        val expiresAt: Long? = null,         // epoch ms UTC; null = perpetua
        val issuedAt: Long,
        val schema: Int = 1,
    )

    /** @return null si firma inválida, esquema desconocido o device no coincide. */
    fun verify(fileContent: String, currentDeviceId: String, context: Context? = null): LicenseState? {
        val idx = fileContent.lastIndexOf("\n.sig:")
        if (idx < 0) return null
        val payloadStr = fileContent.substring(0, idx)
        val sigB64 = fileContent.substring(idx + "\n.sig:".length).trim()
        return try {
            val sig = android.util.Base64.decode(sigB64, android.util.Base64.NO_WRAP)
            val pub = android.util.Base64.decode(PUBLIC_KEY_B64, android.util.Base64.NO_WRAP)
            Ed25519Verify(pub).verify(sig, payloadStr.toByteArray(Charsets.UTF_8))

            val p = json.decodeFromString<LicensePayload>(payloadStr)
            if (p.deviceId != currentDeviceId) null   // licencia ligada al dispositivo
            else LicenseState(
                tier = runCatching { LicenseTier.valueOf(p.tier) }.getOrDefault(LicenseTier.FREE),
                businessName = p.business,
                deviceId = p.deviceId,
                expiresAt = p.expiresAt,
                enabledModules = p.modules.toSet(),
            )
        } catch (_: Exception) {
            null // firma corrupta / payload malformado → FREE silencioso (degradación elegante)
        }
    }
}
