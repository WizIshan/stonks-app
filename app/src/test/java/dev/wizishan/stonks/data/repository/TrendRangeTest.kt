package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.data.local.query.MonthTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class TrendRangeTest {

    private val september = YearMonth.of(2026, 9)

    private fun months(vararg keys: String) = keys.map { MonthTotal(it, 1000) }

    @Test
    fun `fixed ranges are exactly what they say`() {
        assertEquals(3, monthsToCover(emptyList(), emptyList(), september, TrendRange.THREE_MONTHS))
        assertEquals(6, monthsToCover(emptyList(), emptyList(), september, TrendRange.SIX_MONTHS))
        assertEquals(12, monthsToCover(emptyList(), emptyList(), september, TrendRange.ONE_YEAR))
    }

    @Test
    fun `three months is the default`() {
        assertEquals(TrendRange.THREE_MONTHS, DashboardData().trendRange)
    }

    @Test
    fun `a fixed range ignores how far back the data goes`() {
        val old = months("2019-01", "2026-09")

        assertEquals(3, monthsToCover(old, emptyList(), september, TrendRange.THREE_MONTHS))
    }

    @Test
    fun `all time reaches back to the oldest entry, inclusive`() {
        // July, August, September.
        assertEquals(3, monthsToCover(months("2026-07"), emptyList(), september, TrendRange.ALL_TIME))
    }

    @Test
    fun `all time considers income as well as spend`() {
        val span = monthsToCover(months("2026-08"), months("2026-01"), september, TrendRange.ALL_TIME)

        assertEquals(9, span)
    }

    @Test
    fun `all time on an empty database still draws an axis`() {
        // A single point is not a line; falling back to the default gives it something to
        // render rather than a degenerate chart.
        assertEquals(3, monthsToCover(emptyList(), emptyList(), september, TrendRange.ALL_TIME))
    }

    @Test
    fun `all time with only this month still spans two points`() {
        assertEquals(2, monthsToCover(months("2026-09"), emptyList(), september, TrendRange.ALL_TIME))
    }

    @Test
    fun `all time is capped so a very old entry cannot flood the chart`() {
        val ancient = months("1990-01")

        val span = monthsToCover(ancient, emptyList(), september, TrendRange.ALL_TIME)

        assertEquals(120, span)
    }

    @Test
    fun `an unparseable month key is ignored rather than throwing`() {
        val messy = listOf(MonthTotal("not-a-month", 1000), MonthTotal("2026-08", 1000))

        assertEquals(2, monthsToCover(messy, emptyList(), september, TrendRange.ALL_TIME))
    }

    @Test
    fun `the resolved span feeds a trend of that exact length`() {
        val expenses = months("2026-05")
        val span = monthsToCover(expenses, emptyList(), september, TrendRange.ALL_TIME)

        val points = buildTrend(expenses, emptyList(), september, span)

        assertEquals(span, points.size)
        assertEquals(YearMonth.of(2026, 5), points.first().month)
        assertEquals(september, points.last().month)
        assertTrue(points.all { it.month <= september })
    }
}
