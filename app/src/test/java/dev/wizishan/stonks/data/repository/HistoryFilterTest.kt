package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.data.local.query.HistorySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryFilterTest {

    private val today = LocalDate.parse("2026-09-15")

    @Test
    fun `all time has no range`() {
        assertNull(HistoryPeriod.ALL_TIME.rangeOrNull(today))
    }

    @Test
    fun `this month runs from the first of the month to today`() {
        val range = requireNotNull(HistoryPeriod.THIS_MONTH.rangeOrNull(today))
        assertEquals(LocalDate.parse("2026-09-01"), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun `rolling windows are inclusive of today, so 30 days means 30 days`() {
        val range = requireNotNull(HistoryPeriod.LAST_30_DAYS.rangeOrNull(today))
        assertEquals(LocalDate.parse("2026-08-17"), range.start)
        assertEquals(today, range.endInclusive)
        assertEquals(30, range.start.datesUntil(range.endInclusive.plusDays(1)).count())
    }

    @Test
    fun `ninety day window is also inclusive`() {
        val range = requireNotNull(HistoryPeriod.LAST_90_DAYS.rangeOrNull(today))
        assertEquals(90, range.start.datesUntil(range.endInclusive.plusDays(1)).count())
    }

    @Test
    fun `this month on the first of the month is a single day`() {
        val range = requireNotNull(HistoryPeriod.THIS_MONTH.rangeOrNull(LocalDate.parse("2026-09-01")))
        assertEquals(range.start, range.endInclusive)
    }

    @Test
    fun `a default filter is not filtered`() {
        assertFalse(HistoryFilter().isFiltered)
        // Sort is not a filter — reordering the list does not hide anything.
        assertFalse(HistoryFilter(sort = HistorySort.AMOUNT_DESC).isFiltered)
    }

    @Test
    fun `any narrowing choice counts as filtered`() {
        assertTrue(HistoryFilter(type = HistoryType.INCOME).isFiltered)
        assertTrue(HistoryFilter(categoryId = 1).isFiltered)
        assertTrue(HistoryFilter(tripId = 1).isFiltered)
        assertTrue(HistoryFilter(period = HistoryPeriod.THIS_MONTH).isFiltered)
    }

    @Test
    fun `a category or trip filter excludes income, which has neither`() {
        assertTrue(HistoryFilter().includesIncome)
        assertFalse(HistoryFilter(categoryId = 1).includesIncome)
        assertFalse(HistoryFilter(tripId = 1).includesIncome)
        assertFalse(HistoryFilter(type = HistoryType.EXPENSES).includesIncome)
    }

    @Test
    fun `the income view drops the sorts that have no meaning for it`() {
        val sorts = HistoryFilter(type = HistoryType.INCOME).availableSorts
        assertFalse(HistorySort.CATEGORY_ASC in sorts)
        assertFalse(HistorySort.TRIP_ASC in sorts)
        assertTrue(HistorySort.AMOUNT_DESC in sorts)
    }

    @Test
    fun `every sort is offered when expenses are in view`() {
        assertEquals(HistorySort.entries, HistoryFilter().availableSorts)
        assertEquals(HistorySort.entries, HistoryFilter(type = HistoryType.EXPENSES).availableSorts)
    }
}
