package com.ipv.pos.domain

import java.math.BigDecimal

/** Niveles comerciales (arquitectura §9). */
enum class LicenseTier { FREE, PREMIUM }

/** Límites del nivel FREE aplicados en dominio (spec §4.8), no en UI. */
data class LicenseLimits(
    val maxProducts: Int?,          // null = sin límite (PREMIUM)
    val maxUsers: Int?,
    val allowCredit: Boolean,
    val watermarkPdf: Boolean,
) {
    companion object {
        val FREE = LicenseLimits(maxProducts = 200, maxUsers = 1, allowCredit = false, watermarkPdf = true)
        val PREMIUM = LicenseLimits(null, null, true, false)
        fun forTier(tier: LicenseTier) = if (tier == LicenseTier.PREMIUM) PREMIUM else FREE
    }
}

/** Estado de licencia resuelto localmente (verificación Ed25519 offline, sin phone-home). */
data class LicenseState(
    val tier: LicenseTier,
    val businessName: String?,
    val deviceId: String?,
    val expiresAt: Long?,           // null = perpetua
    val enabledModules: Set<String>,
) {
    val limits get() = LicenseLimits.forTier(tier)
    fun isValidAt(nowMs: Long): Boolean =
        tier == LicenseTier.PREMIUM && (expiresAt == null || nowMs < expiresAt)

    companion object {
        /** Sin licencia válida → FREE (spec §4.8). */
        val FREE_DEFAULT = LicenseState(LicenseTier.FREE, null, null, null, emptySet())
    }
}

/** Validaciones de límites FREE para use cases (Sprint 5 las conecta; reglas puras desde ya). */
object LicenseGuard {
    sealed interface Denial { data object ProductLimitReached : Denial; data object UserLimitReached : Denial; data object CreditNotAllowed : Denial }

    fun checkCanAddProduct(state: LicenseState, currentActiveProducts: Int): Denial? =
        state.limits.maxProducts?.let { if (currentActiveProducts >= it) Denial.ProductLimitReached else null }

    fun checkCanAddUser(state: LicenseState, currentActiveUsers: Int): Denial? =
        state.limits.maxUsers?.let { if (currentActiveUsers >= it) Denial.UserLimitReached else null }

    fun checkCanSellOnCredit(state: LicenseState): Denial? =
        if (!state.limits.allowCredit) Denial.CreditNotAllowed else null
}

/** Payloads de configuración persistidos en la tabla settings (clave → JSON). */
data class BusinessProfile(
    val name: String = "",
    val address: String = "",
    val taxId: String = "",                 // NIT
    val phone: String = "",
    val folioPrefix: String = "A",
)
data class TaxConfig(
    val defaultRate: BigDecimal = BigDecimal("0.10"),   // informativo (ADR-001 §1.5)
    val exemptEnabled: Boolean = true,
)
data class AppSettings(
    val offlineWarningDays: Int = 7,
    val backupReminderDays: Int = 7,
    val cashierDiscountCapPct: BigDecimal = BigDecimal.ZERO,
    val allowNegativeStock: Boolean = false,
)
