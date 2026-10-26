package com.ipv.pos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test fun `hash y verificacion roundtrip`() {
        val salt = PinHasher.newSalt()
        val stored = PinHasher.hash("1234", salt)
        assertTrue(stored.startsWith("120000:"))           // iter:salt:hash
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("1235", stored))
    }

    @Test fun `salt distinto produce hash distinto para mismo PIN`() {
        val a = PinHasher.hash("9999", PinHasher.newSalt())
        val b = PinHasher.hash("9999", PinHasher.newSalt())
        assertFalse(a == b)
        assertTrue(PinHasher.verify("9999", a) && PinHasher.verify("9999", b))
    }

    @Test fun `verificacion tolera cadena corrupta sin explotar`() {
        assertFalse(PinHasher.verify("1234", "no-un-hash"))
        assertFalse(PinHasher.verify("1234", "1000:zz:yy"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza pin de 3 digitos`() { PinHasher.hash("123", PinHasher.newSalt()) }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza pin no numerico`() { PinHasher.hash("12ab", PinHasher.newSalt()) }

    @Test fun `pin de 6 digitos aceptado`() {
        val s = PinHasher.hash("123456", PinHasher.newSalt())
        assertEquals(true, PinHasher.verify("123456", s))
    }
}
