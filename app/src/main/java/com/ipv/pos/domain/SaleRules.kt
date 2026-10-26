package com.ipv.pos.domain

import java.math.BigDecimal

/** Tipos de cambio manuales (ADR-001 §1.4): sin feed offline fiable, el cajero los captura al cobrar. */
enum class Currency(val code: String) { CUP("CUP"), MLC("MLC"), USD("USD") }

enum class PaymentMethod(val currencyDefault: Currency, val isCash: Boolean) {
    CASH_CUP(Currency.CUP, true),
    CASH_MLC(Currency.MLC, true),
    CASH_USD(Currency.USD, true),
    CARD_MLC(Currency.MLC, false),
    TRANSFER(Currency.CUP, false),
    CREDIT(Currency.CUP, false);   // Sprint 3; reservado en Sprint 2
}

enum class SaleStatus { OPEN, COMPLETED, VOID }
enum class InvoiceStatus { ISSUED, VOID, SENT_TO_AUTHORITY, REJECTED }
enum class StockMovementType { SALE, PURCHASE, ADJUSTMENT, RETURN, CREDIT_NOTE, TRANSFER }
enum class UserRole { ADMIN, MANAGER, CASHIER }
enum class SessionStatus { OPEN, CLOSED }

/** Línea del carrito antes de persistir. Precios como BigDecimal exactos. */
data class CartLine(
    val productId: String,
    val productName: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,          // fracción 0..1
    val isService: Boolean,
    val lineDiscountCents: Cents = Cents.ZERO,
)

sealed interface CheckoutError {
    data object NoSession : CheckoutError
    data object EmptyCart : CheckoutError
    data class InsufficientStock(val productId: String, val available: BigDecimal, val requested: BigDecimal) : CheckoutError
    data class DiscountExceedsRoleCap(val maxPct: BigDecimal) : CheckoutError
    data class PaymentsDoNotCoverTotal(val missing: Cents) : CheckoutError
    data class CreditRequiresCustomer : CheckoutError
    data class CreditLimitExceeded(val customerId: String, val limit: BigDecimal, val newBalance: BigDecimal) : CheckoutError
    data object FreeTierNoCredit : CheckoutError
}

/** Resultado puro del cálculo de checkout — sin I/O, 100% testeable. */
data class CheckoutTotals(
    val subtotal: Cents,
    val discountTotal: Cents,
    val taxTotal: Cents,
    val total: Cents,
)

/**
 * Reglas de negocio del carrito y totales (arquitectura §7, spec §4.1).
 * Impuestos informativos por línea sobre base descontada; global % solo ADMIN/MANAGER
 * con tope de rol para CAJERO (cashierDiscountCapPct, default 0).
 */
object CartRules {

    /** Descuento máximo porcentual permitido según rol (spec §2/§4.1.4). */
    fun discountCapPct(role: UserRole, cashierCapPct: BigDecimal): BigDecimal = when (role) {
        UserRole.ADMIN, UserRole.MANAGER -> BigDecimal("100")
        UserRole.CASHIER -> cashierCapPct.max(BigDecimal.ZERO)
    }

    fun validateDiscountPct(pct: BigDecimal, role: UserRole, cashierCapPct: BigDecimal): Boolean =
        pct >= BigDecimal.ZERO && pct <= discountCapPct(role, cashierCapPct)

    /**
     * Totales deterministas en céntimos. Orden de operaciones fijo:
     * línea = precio*cantidad → menos descuento de línea → base imponible → impuesto por línea.
     * El descuento GLOBAL (%) se aplica proporcionalmente a la base ANTES de impuestos
     * y se redistribuye a céntimo exacto (última línea absorbe residuo → cuadre garantizado).
     */
    fun computeTotals(lines: List<CartLine>, globalDiscountPct: BigDecimal): CheckoutTotals {
        require(lines.isNotEmpty()) { "cart empty" }
        val subtotals = lines.map { lineTotal(it.unitPrice, it.quantity) }
        val subtotal = subtotals.fold(Cents.ZERO) { a, b -> a + b }
        val lineDiscounts = lines.map { it.lineDiscountCents }
        val perLineDiscount = lineDiscounts.fold(Cents.ZERO) { a, b -> a + b }

        // Base después de descuentos por línea
        val basesAfterLineDisc = subtotals.zip(lineDiscounts) { s, d -> s - d }
        val baseForGlobal = basesAfterLineDisc.fold(Cents.ZERO) { a, b -> a + b }
        val globalDisc = discountPct(baseForGlobal, globalDiscountPct)

        // Redistribución proporcional del descuento global (residuo a la última línea)
        val globalPerLine = distribute(globalDisc, basesAfterLineDisc)
        val discountTotal = perLineDiscount + globalDisc

        var taxTotal = Cents.ZERO
        for ((i, line) in lines.withIndex()) {
            val taxableBase = basesAfterLineDisc[i] - globalPerLine[i]
            taxTotal += taxOf(taxableBase, line.taxRate)
        }
        return CheckoutTotals(
            subtotal = subtotal,
            discountTotal = discountTotal,
            taxTotal = taxTotal,
            total = baseForGlobal - globalDisc + taxTotal,
        )
    }

    /** Reparte un monto entre pesos proporcionales; la suma de partes == monto exacto. */
    internal fun distribute(amount: Cents, weights: List<Cents>): List<Cents> {
        if (weights.isEmpty()) return emptyList()
        val totalWeight = weights.fold(0L) { a, w -> a + w.value }
        if (totalWeight <= 0 || amount.isZero) return weights.map { Cents(0) }
        val parts = mutableListOf<Long>()
        var assigned = 0L
        for (i in weights.indices) {
            if (i == weights.lastIndex) {
                parts.add(amount.value - assigned)   // residuo → cuadre exacto
            } else {
                val p = (amount.value.toBigDecimal() * weights[i].value.toBigDecimal() /
                    totalWeight.toBigDecimal()).toLong() // truncamiento controlado
                parts.add(p); assigned += p
            }
        }
        return parts.map(::Cents)
    }

    /** Vuelto: solo aplica a pagos en efectivo; excedente de tarjeta/transferencia NO genera vuelto. */
    fun changeDue(cashPaid: Cents, total: Cents): Cents = (cashPaid - total).coerceAtLeast(Cents.ZERO)

    /** Regla de stock (spec §4.1.3): servicios nunca bloquean; productos respetan allowNegative. */
    fun stockViolation(
        line: CartLine,
        currentStock: BigDecimal,
        allowNegativeStock: Boolean,
    ): CheckoutError? {
        if (line.isService || allowNegativeStock) return null
        val after = currentStock.subtract(line.quantity)
        return if (after < BigDecimal.ZERO)
            CheckoutError.InsufficientStock(line.productId, currentStock, line.quantity)
        else null
    }
}
