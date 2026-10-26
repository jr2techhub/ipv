package com.ipv.pos.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Costo promedio ponderado (spec §4.4): recalculado en cada entrada de compra.
 * CPP_nuevo = (stock_actual * CPP_actual + qty_compra * costo_compra) / (stock_actual + qty_compra)
 * Casos borde: stock<=0 tras la compra → CPP = costo de la compra nueva (no arrastra basura);
 * división con HALF_UP a 4 decimales internos (precios unitarios admiten >2 dec).
 */
object WeightedAverageCost {

    const val INTERNAL_SCALE = 4

    fun afterPurchase(
        currentStock: BigDecimal,
        currentCpp: BigDecimal,
        purchaseQty: BigDecimal,
        purchaseUnitCost: BigDecimal,
    ): BigDecimal {
        require(purchaseQty > BigDecimal.ZERO) { "purchaseQty must be positive" }
        val effectiveStock = currentStock.max(BigDecimal.ZERO) // stock negativo no contamina el CPP
        val totalValue = effectiveStock.multiply(currentCpp.max(BigDecimal.ZERO))
            .add(purchaseQty.multiply(purchaseUnitCost))
        val totalQty = effectiveStock.add(purchaseQty)
        return totalValue.divide(totalQty, INTERNAL_SCALE, RoundingMode.HALF_UP)
    }

    /** Las salidas (venta/devolución parcial) NO alteran el CPP — solo el stock. */
    fun unchangedOnOutflow(cpp: BigDecimal): BigDecimal = cpp
}

/**
 * Cierre de caja (spec §4.3): esperado = apertura + efectivo ventas − efectivo devoluciones.
 * Todo en céntimos exactos; diferencia != 0 exige comentario (validado en use case, no aquí).
 */
object CashCountRules {
    fun expectedBalance(
        openingCents: Cents,
        cashSalesCents: Cents,
        cashRefundsCents: Cents, // positivo = monto devuelto en efectivo
    ): Cents = openingCents + cashSalesCents - cashRefundsCents

    fun difference(expected: Cents, counted: Cents): Cents = counted - expected
}

/**
 * Folio por dispositivo (spec §4.5, arquitectura §5.3): prefijo + contador, NUNCA se reutiliza
 * incluso tras anulaciones. Puro para testeabilidad; la persistencia atómica vive en el DAO.
 */
object FolioRules {
    fun format(prefix: String, counter: Long): String {
        require(counter >= 1) { "counter starts at 1" }
        val clean = prefix.trim().uppercase().ifEmpty { "A" }
        return "$clean-${counter.toString().padStart(6, '0')}"
    }

    /** El siguiente número dado el último usado (monótono, sin gaps reutilizables). */
    fun next(lastUsed: Long): Long = lastUsed + 1
}

/**
 * Cuentas por cobrar (spec §4.6): saldo = créditos pendientes − abonos.
 * Antigüedad simple sobre la fecha de la venta más antigua impaga: corriente/+30/+60.
 */
object ReceivableRules {
    enum class Aging { CURRENT, OVER_30, OVER_60 }

    fun balance(creditSalesCents: Cents, paymentsCents: Cents): Cents = creditSalesCents - paymentsCents

    fun aging(oldestOpenSaleDays: Long): Aging = when {
        oldestOpenSaleDays >= 60 -> Aging.OVER_60
        oldestOpenSaleDays >= 30 -> Aging.OVER_30
        else -> Aging.CURRENT
    }

    /** Excede el límite? NULL limit = sin límite (spec). Solo ADMIN puede autorizar exceso. */
    fun exceedsLimit(limit: BigDecimal?, newBalanceCents: Cents): Boolean {
        if (limit == null) return false
        return newBalanceCents.toDecimal() > limit
    }
}
