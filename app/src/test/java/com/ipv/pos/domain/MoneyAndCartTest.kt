package com.ipv.pos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Reglas de dinero/stock: objetivo del plan = 100% cobertura (plan §4). */
class MoneyAndCartTest {

    // ---------- Cents / BigDecimal ----------

    @Test fun `fromDecimal redondea HALF_UP a centavos`() {
        assertEquals(1235L, Cents.fromDecimal(BigDecimal("12.345")).value)   // .345 → .35 (HALF_UP)
        assertEquals(1234L, Cents.fromDecimal(BigDecimal("12.344")).value)
        assertEquals(-1235L, Cents.fromDecimal(BigDecimal("-12.345")).value)
    }

    @Test fun `lineTotal calcula exacto y redondea una sola vez`() {
        // 3.33 * 3 = 9.99 exacto
        assertEquals(999L, lineTotal(BigDecimal("3.33"), BigDecimal("3")).value)
        // 0.005 * 1 = 0.005 → HALF_UP → 0.01
        assertEquals(1L, lineTotal(BigDecimal("0.005"), BigDecimal("1")).value)
    }

    @Test fun `taxOf aplica fraccion data-driven`() {
        assertEquals(100L, taxOf(Cents(1000), BigDecimal("0.10")).value)     // 10.00 + 1.00
        assertEquals(0L, taxOf(Cents(1000), BigDecimal.ZERO).value)          // exento
        assertEquals(33L, taxOf(Cents(300), BigDecimal("0.11")).value)       // 3.00*0.11 = 0.33
    }

    @Test fun `formatCup agrupa con punto y decimal coma`() {
        assertEquals("$ 1.234,56", formatCup(Cents(123456)))
        assertEquals("-$ 0,05", formatCup(Cents(-5)))
        assertEquals("$ 0,00", formatCup(Cents.ZERO))
    }

    // ---------- CartRules ----------

    private fun line(price: String, qty: String, tax: String = "0.10", disc: Cents = Cents.ZERO, service: Boolean = false) =
        CartLine("p", "prod", BigDecimal(qty), BigDecimal(price), BigDecimal(tax), service, disc)

    @Test fun `totales simples sin descuento`() {
        val t = CartRules.computeTotals(listOf(line("10.00", "2")), BigDecimal.ZERO)
        assertEquals(2000L, t.subtotal.value)
        assertEquals(0L, t.discountTotal.value)
        assertEquals(200L, t.taxTotal.value)
        assertEquals(2200L, t.total.value)
    }

    @Test fun `descuento por linea reduce base imponible`() {
        // 20.00 - 5.00 desc línea = 15.00; impuesto 10% = 1.50; total 16.50
        val t = CartRules.computeTotals(listOf(line("20.00", "1", disc = Cents(500))), BigDecimal.ZERO)
        assertEquals(2000L, t.subtotal.value)
        assertEquals(500L, t.discountTotal.value)
        assertEquals(150L, t.taxTotal.value)
        assertEquals(1650L, t.total.value)
    }

    @Test fun `descuento global se redistribuye y el total cuadra exacto`() {
        // líneas 7.00 y 3.00 → base 10.00; global 15% = 1.50 → bases 8.50/... repartidas
        val lines = listOf(line("7.00", "1", tax = "0.10"), line("3.00", "1", tax = "0.10"))
        val t = CartRules.computeTotals(lines, BigDecimal("15"))
        assertEquals(1000L, t.subtotal.value)
        assertEquals(150L, t.discountTotal.value)
        // impuestos sobre base descontada proporcionalmente: 8.50*.10 + 1.50*.10 = 0.85+0.15 = 1.00
        assertEquals(100L, t.taxTotal.value)
        assertEquals(950L, t.total.value)
        // invariantes críticos: subtotal - discounts + taxes == total SIEMPRE
        assertEquals(t.total.value, t.subtotal.value - t.discountTotal.value + t.taxTotal.value)
    }

    @Test fun `distribute residuo va a la ultima linea y suma exacta`() {
        val parts = CartRules.distribute(Cents(100), listOf(Cents(1), Cents(1), Cents(1)))
        assertEquals(listOf(33L, 33L, 34L), parts.map { it.value })
        assertEquals(100L, parts.sumOf { it.value })
    }

    @Test fun `vuelto solo sobre efectivo y nunca negativo`() {
        assertEquals(Cents(250), CartRules.changeDue(Cents(1250), Cents(1000)))
        assertEquals(Cents.ZERO, CartRules.changeDue(Cents(800), Cents(1000)))
    }

    @Test fun `tope de descuento por rol`() {
        assertTrue(CartRules.validateDiscountPct(BigDecimal("50"), UserRole.ADMIN, BigDecimal.ZERO))
        assertFalse(CartRules.validateDiscountPct(BigDecimal("1"), UserRole.CASHIER, BigDecimal.ZERO))
        assertTrue(CartRules.validateDiscountPct(BigDecimal("10"), UserRole.CASHIER, BigDecimal("10")))
        assertFalse(CartRules.validateDiscountPct(BigDecimal("11"), UserRole.MANAGER, BigDecimal.ZERO))
    }

    @Test fun `stock insuficiente bloquea, servicio y negativo permitido no`() {
        val l = line("1.00", "5")
        val v = CartRules.stockViolation(l, BigDecimal("2"), allowNegativeStock = false)
        assertTrue(v is CheckoutError.InsufficientStock)
        assertNull(CartRules.stockViolation(l, BigDecimal("2"), allowNegativeStock = true))
        assertNull(CartRules.stockViolation(line("1.00", "5", service = true), BigDecimal.ZERO, false))
    }
}
