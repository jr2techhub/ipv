package com.ipv.pos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InventoryCashFolioTest {

    // ---------- Costo promedio ponderado ----------

    @Test fun `cpp tras compra mezcla stock y costo nuevos`() {
        // 10 u a 5.00 + 10 u a 7.00 → CPP 6.00
        val cpp = WeightedAverageCost.afterPurchase(
            currentStock = BigDecimal("10"), currentCpp = BigDecimal("5.00"),
            purchaseQty = BigDecimal("10"), purchaseUnitCost = BigDecimal("7.00"),
        )
        assertEquals(BigDecimal("6.0000"), cpp)
    }

    @Test fun `cpp con stock cero o negativo usa costo de la compra`() {
        val fromZero = WeightedAverageCost.afterPurchase(BigDecimal.ZERO, BigDecimal("99"), BigDecimal("5"), BigDecimal("4"))
        assertEquals(BigDecimal("4.0000"), fromZero)
        // stock -3 no contamina: solo cuenta la compra
        val fromNeg = WeightedAverageCost.afterPurchase(BigDecimal("-3"), BigDecimal("2"), BigDecimal("5"), BigDecimal("4"))
        assertEquals(BigDecimal("4.0000"), fromNeg)
    }

    // ---------- Caja ----------

    @Test fun `esperado y diferencia de caja en centavos exactos`() {
        val expected = CashCountRules.expectedBalance(Cents(5000), Cents(123456), Cents(1000))
        assertEquals(123456L + 5000L - 1000L, expected.value)
        assertEquals(Cents(127456), expected)
        assertEquals(-50L, CashCountRules.difference(expected, Cents(127406)).value) // faltante 0.50
    }

    // ---------- Folio ----------

    @Test fun `folio formateado con prefijo y ceros`() {
        assertEquals("A-000123", FolioRules.format("a", 123))
        assertEquals("BC-000001", FolioRules.format(" bc ", 1))
        assertEquals("A-000001", FolioRules.format("", 1))
        assertEquals(2L, FolioRules.next(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `contador cero es error — folios empiezan en 1`() { FolioRules.format("A", 0) }

    // ---------- Receivables ----------

    @Test fun `saldo y antiguedad`() {
        assertEquals(Cents(4000), ReceivableRules.balance(Cents(10000), Cents(6000)))
        assertEquals(ReceivableRules.Aging.CURRENT, ReceivableRules.aging(10))
        assertEquals(ReceivableRules.Aging.OVER_30, ReceivableRules.aging(31))
        assertEquals(ReceivableRules.Aging.OVER_60, ReceivableRules.aging(75))
    }

    @Test fun `limite null nunca excede; limite numerico compara en decimal`() {
        assertFalse(ReceivableRules.exceedsLimit(null, Cents(1_000_000)))
        assertTrue(ReceivableRules.exceedsLimit(BigDecimal("100.00"), Cents(10001)))
        assertFalse(ReceivableRules.exceedsLimit(BigDecimal("100.00"), Cents(10000))) // exactamente el límite OK
    }

    // ---------- UUIDv7 ----------

    @Test fun `uuidv7 ordenable por tiempo y version variante correctas`() {
        val a = Uuidv7.new(1_700_000_000_000)
        val b = Uuidv7.new(1_700_000_000_500)
        assertTrue(a < b)
        assertEquals('7', a[14])                    // versión 7
        assertTrue("89ab".contains(a[19]))          // variante RFC 9562
        assertEquals(1_700_000_000_000L, Uuidv7.timestampOf(a))
        assertEquals(36, a.length)
    }

    // ---------- Licencia FREE límites ----------

    @Test fun `guard FREE limita productos usuarios y credito`() {
        val free = LicenseState.FREE_DEFAULT
        assertNull(LicenseGuard.checkCanAddProduct(free, 199))
        assertEquals(LicenseGuard.Denial.ProductLimitReached, LicenseGuard.checkCanAddProduct(free, 200))
        assertEquals(LicenseGuard.Denial.UserLimitReached, LicenseGuard.checkCanAddUser(free, 1))
        assertEquals(LicenseGuard.Denial.CreditNotAllowed, LicenseGuard.checkCanSellOnCredit(free))
        val premium = LicenseState(LicenseTier.PREMIUM, "Neg", "dev", null, setOf("sync"))
        assertNull(LicenseGuard.checkCanAddProduct(premium, 100_000))
        assertNull(LicenseGuard.checkCanSellOnCredit(premium))
        assertTrue(premium.isValidAt(System.currentTimeMillis()))
        val expired = premium.copy(expiresAt = 1L)
        assertFalse(expired.isValidAt(System.currentTimeMillis()))
    }

    // ---------- Lockout ----------

    @Test fun `quinto intento falla y bloquea 5 minutos`() {
        var state = LoginLockoutPolicy.State(0, null)
        val t0 = 100_000L
        repeat(4) { state = LoginLockoutPolicy.attemptAfterFailure(state, t0).first }
        assertFalse(LoginLockoutPolicy.isLocked(state, t0))
        val (locked, justLocked) = LoginLockoutPolicy.attemptAfterFailure(state, t0)
        assertTrue(justLocked)
        assertNotNull(locked.lockedUntil)
        assertTrue(LoginLockoutPolicy.isLocked(locked, t0 + 4 * 60_000))
        assertFalse(LoginLockoutPolicy.isLocked(locked, t0 + 5 * 60_000 + 1))
    }
}
