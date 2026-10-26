package com.ipv.pos.data.repo

import android.content.Context
import com.ipv.pos.data.db.IpvDatabase
import com.ipv.pos.data.db.SettingEntity
import com.ipv.pos.domain.BusinessProfile
import com.ipv.pos.domain.LicenseState
import com.ipv.pos.domain.LicenseTier
import com.ipv.pos.lic.LicenseVerifier
import kotlinx.serialization.json.Json

/** Perfil de negocio, configuración y estado de licencia (tabla settings + schema_meta). */
class SettingsRepository(private val db: IpvDatabase, private val context: Context) {
    private val dao get() = db.systemDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun businessProfile(): BusinessProfile =
        dao.setting("business_profile")?.let { json.decodeFromString(it) } ?: BusinessProfile()

    suspend fun saveBusinessProfile(p: BusinessProfile) =
        dao.upsertSetting(SettingEntity("business_profile", json.encodeToString(p), System.currentTimeMillis()))

    suspend fun <T : Any> put(key: String, value: T) =
        dao.upsertSetting(SettingEntity(key, json.encodeToString(value), System.currentTimeMillis()))

    suspend fun <T> get(key: String, deserializer: () -> T?): T? = try {
        deserializer()
    } catch (_: Exception) { null }

    /** device_id estable por instalación; prefijo de folio derivado (2 letras + 3 dígitos pseudoaleatorios). */
    suspend fun deviceId(): String {
        dao.meta("device_id")?.let { return it }
        val fresh = com.ipv.pos.domain.Uuidv7.new()
        dao.upsertMeta(com.ipv.pos.data.db.SchemaMetaEntity("device_id", fresh))
        return fresh
    }

    suspend fun folioPrefix(): String {
        dao.setting("business_profile")?.let { raw ->
            runCatching { json.decodeFromString<BusinessProfile>(raw).folioPrefix }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        }
        // fallback determinista por instalación
        val id = deviceId().replace("-", "")
        return ("A" + id.substring(0, 3)).uppercase()
    }

    /** Estado de licencia cacheado en settings tras verificar firma localmente. */
    suspend fun licenseState(): LicenseState {
        val raw = dao.setting("license_data") ?: return LicenseState.FREE_DEFAULT
        val verified = LicenseVerifier.verify(raw, deviceId(), context) ?: return LicenseState.FREE_DEFAULT
        return verified.copy(tier = if (verified.isValidAt(System.currentTimeMillis()))
            LicenseTier.PREMIUM else LicenseTier.FREE)
    }

    suspend fun saveLicenseFile(content: String): Result<LicenseState> = runCatching {
        val state = LicenseVerifier.verify(content, deviceId(), context)
            ?: error("Licencia inválida o no válida para este dispositivo")
        dao.upsertSetting(SettingEntity("license_data", content, System.currentTimeMillis()))
        state
    }
}
