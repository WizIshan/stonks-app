package dev.wizishan.stonks.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `round-trips a date`() {
        val date = LocalDate.of(2026, 9, 2)
        assertEquals(date, converters.toLocalDate(converters.fromLocalDate(date)))
    }

    @Test
    fun `nulls pass through`() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun `stores zero-padded ISO text`() {
        assertEquals("2026-01-05", converters.fromLocalDate(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `text form sorts chronologically`() {
        // This is the property every date query relies on — ORDER BY date, BETWEEN, and
        // substr(date, 1, 7) for month grouping all assume lexicographic == chronological.
        val dates = listOf(
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 2, 28),
        )
        val asText = dates.map { converters.fromLocalDate(it)!! }

        assertEquals(dates.sorted().map { it.toString() }, asText.sorted())
    }

    @Test
    fun `first seven characters are the year-month used for grouping`() {
        assertEquals("2026-09", converters.fromLocalDate(LocalDate.of(2026, 9, 2))!!.take(7))
        assertTrue(converters.fromLocalDate(LocalDate.of(2026, 12, 31))!!.startsWith("2026-12"))
    }
}
