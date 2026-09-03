package dev.wizishan.stonks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun `formats minor units as euros`() {
        assertTrue(Money.format(8250, Locale.GERMANY).contains("82,50"))
        assertTrue(Money.format(8250, Locale.US).contains("82.50"))
        assertTrue(Money.format(0, Locale.US).contains("0.00"))
    }

    @Test
    fun `signed formatting always shows polarity`() {
        assertTrue(Money.formatSigned(8250, Locale.US).startsWith("+"))
        assertTrue(Money.formatSigned(-8250, Locale.US).startsWith("−"))
        assertTrue("zero reads as a positive balance", Money.formatSigned(0, Locale.US).startsWith("+"))
    }

    @Test
    fun `signed formatting shows the magnitude once, not a double negative`() {
        val formatted = Money.formatSigned(-4000, Locale.US)
        assertEquals(1, formatted.count { it == '−' || it == '-' })
    }

    @Test
    fun `parses plain amounts`() {
        assertEquals(8250L, Money.parseOrNull("82.50"))
        assertEquals(8250L, Money.parseOrNull("82,50"))
        assertEquals(8200L, Money.parseOrNull("82"))
        assertEquals(5L, Money.parseOrNull("0.05"))
        assertEquals(-4000L, Money.parseOrNull("-40"))
    }

    @Test
    fun `parses amounts with a currency symbol or spaces`() {
        assertEquals(8250L, Money.parseOrNull("€82.50"))
        assertEquals(8250L, Money.parseOrNull("82.50 €"))
        assertEquals(8250L, Money.parseOrNull(" 82.50 "))
        assertEquals(8250L, Money.parseOrNull("EUR 82.50"))
        assertEquals(-500L, Money.parseOrNull("-€5"))
    }

    @Test
    fun `parses both grouping conventions`() {
        assertEquals(123_456L, Money.parseOrNull("1,234.56"))
        assertEquals(123_456L, Money.parseOrNull("1.234,56"))
        assertEquals(123_456_789L, Money.parseOrNull("1,234,567.89"))
        assertEquals(123_400L, Money.parseOrNull("1234"))
    }

    @Test
    fun `refuses to guess when a lone separator is ambiguous`() {
        // "1,234" is one thousand two hundred and thirty four under one convention and
        // 1.234 under the other — a factor of a thousand apart. Rejecting it costs the
        // user a retype; guessing wrong costs them their records.
        assertNull(Money.parseOrNull("1,234"))
        assertNull(Money.parseOrNull("1.234"))
    }

    @Test
    fun `rejects more precision than the currency has`() {
        assertNull("rounding a user's input silently would be worse than refusing it", Money.parseOrNull("82.505"))
        assertNull(Money.parseOrNull("0.001"))
    }

    @Test
    fun `rejects junk`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("   "))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Money.parseOrNull("8..50"))
        assertNull(Money.parseOrNull("€"))
    }

    @Test
    fun `parse and format round-trip`() {
        listOf(0L, 5L, 100L, 8250L, -4000L, 123_456_789L).forEach { minor ->
            val reparsed = Money.parseOrNull(Money.format(minor, Locale.US))
            assertEquals("round trip failed for $minor", minor, reparsed)
        }
    }

    @Test
    fun `minor units are exact where doubles are not`() {
        // 0.1 + 0.2 != 0.3 in binary floating point; the whole reason amounts are Longs.
        val summed = (1..3).sumOf { Money.parseOrNull("0.1")!! }
        assertEquals(30L, summed)
        assertEquals(Money.parseOrNull("0.30"), summed)
    }
}
