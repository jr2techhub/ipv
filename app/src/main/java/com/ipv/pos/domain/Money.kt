package com.ipv.pos.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Dinero del MVP (ADR-001 §2): la contabilidad se consolida en CUP con céntimos enteros.
 * SQLite persiste REAL pero TODO cálculo intermedio pasa por Money (BigDecimal exacto);
 * redondeo HALF_UP a 2 decimales solo al materializar totales. Criterio de aceptación #3
 * ("cuadra al centavo") depende de esta disciplina.
 */
@JvmInline
value class Cents(val value: Long) : Comparable<Cents> {
    override fun compareTo(other: Cents): Int = value.compareTo(other.value)

    operator fun plus(o: Cents) = Cents(value + o.value)
    operator fun minus(o: Cents) = Cents(value - o.value)
    operator fun times(n: Int) = Cents(value * n)
    operator fun unaryMinus() = Cents(-value)

    val isZero get() = value == 0L
    val isNegative get() = value < 0

    companion object {
        val ZERO = Cents(0)

        /** BigDecimal (ej. 12.35) → céntimos exactos con HALF_UP. */
        fun fromDecimal(amount: BigDecimal): Cents =
            Cents(amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact())

        fun fromDoubleSafe(amount: Double): Cents = fromDecimal(BigDecimal.valueOf(amount))
    }
}

/** Cents → BigDecimal para mostrar/persistir. */
fun Cents.toDecimal(): BigDecimal = BigDecimal(value).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)

/**
 * Precio unitario x cantidad → línea en céntimos.
 * El precio unitario puede venir con >2 decimales (ej. CPP ponderado); se calcula
 * la línea exacta y se redondea UNA vez (HALF_UP), nunca antes.
 */
fun lineTotal(unitPrice: BigDecimal, quantity: BigDecimal): Cents =
    Cents.fromDecimal(unitPrice.multiply(quantity))

/**
 * Impuesto sobre una base ya descontada. taxRate es fracción (0.10 = 10%), data-driven
 * desde settings — nunca hardcodeado (requisito regulatorio ONAT, arquitectura §6).
 */
fun taxOf(baseCents: Cents, taxRate: BigDecimal): Cents =
    Cents.fromDecimal(baseCents.toDecimal().multiply(taxRate))

/** Descuento porcentual sobre subtotal, redondeado a céntimo. */
fun discountPct(totalCents: Cents, pct: BigDecimal): Cents =
    Cents.fromDecimal(totalCents.toDecimal().multiply(pct).movePointLeft(2))

/** Formatea para UI cubana: "$ 1.234,56" (agrupación punto, decimal coma). */
fun formatCup(cents: Cents): String {
    val neg = cents.isNegative
    val abs = kotlin.math.abs(cents.value)
    val intPart = abs / 100
    val frac = abs % 100
    val grouped = buildString {
        val s = intPart.toString()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) append('.')
            append(c)
        }
    }
    return "${if (neg) "-$" else "$"} $grouped,${frac.toString().padStart(2, '0')}"
}
