package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.data.local.query.CategoryTotal
import dev.wizishan.stonks.data.local.query.MonthTotal
import dev.wizishan.stonks.data.local.query.TripTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The pure shaping functions behind the Dashboard — no database needed. */
class DashboardTest {

    private fun category(name: String, total: Long, hex: String = "#2A78D6") =
        CategoryTotal(categoryId = name.hashCode().toLong(), categoryName = name, colorHex = hex, totalMinor = total)

    @Test
    fun `slices come back ranked biggest first`() {
        val slices = listOf(category("A", 100), category("C", 300), category("B", 200)).toRankedSlices()

        assertEquals(listOf("C", "B", "A"), slices.map { it.label })
        assertEquals(listOf(300L, 200L, 100L), slices.map { it.amountMinor })
    }

    @Test
    fun `a tail of two or more folds into Other`() {
        val totals = (1..9).map { category("cat$it", it * 100L) }

        val slices = totals.toRankedSlices(limit = 6)

        assertEquals(7, slices.size)
        assertEquals("Other", slices.last().label)
        // The three smallest: 100 + 200 + 300.
        assertEquals(600, slices.last().amountMinor)
    }

    @Test
    fun `the folded bucket uses the reserved grey`() {
        val slices = (1..9).map { category("cat$it", it * 100L) }.toRankedSlices(limit = 6)

        assertEquals("#898781", slices.last().colorHex)
    }

    @Test
    fun `a tail of exactly one is shown by name instead of being folded`() {
        val totals = (1..7).map { category("cat$it", it * 100L) }

        val slices = totals.toRankedSlices(limit = 6)

        assertEquals(7, slices.size)
        assertTrue("relabelling a single category as Other hides its name for nothing",
            slices.none { it.label == "Other" })
    }

    @Test
    fun `fewer categories than the cap are all shown`() {
        val slices = listOf(category("A", 100), category("B", 200)).toRankedSlices(limit = 6)

        assertEquals(2, slices.size)
    }

    @Test
    fun `no categories gives no slices`() {
        assertEquals(emptyList<RankedSlice>(), emptyList<CategoryTotal>().toRankedSlices())
    }

    @Test
    fun `trips rank and fold the same way`() {
        val trips = (1..9).map { TripTotal(it.toLong(), "trip$it", it * 100L) }

        val slices = trips.toTripSlices(limit = 6)

        assertEquals(7, slices.size)
        assertEquals("Other", slices.last().label)
        assertEquals("trip9", slices.first().label)
    }

    // ---- trend -------------------------------------------------------------------

    private val september = YearMonth.of(2026, 9)

    @Test
    fun `the trend spans exactly the requested number of months, ending at the given one`() {
        val points = buildTrend(emptyList(), emptyList(), september, months = 12)

        assertEquals(12, points.size)
        assertEquals(YearMonth.of(2025, 10), points.first().month)
        assertEquals(september, points.last().month)
    }

    @Test
    fun `months with no activity are filled with zero, not skipped`() {
        val expenses = listOf(MonthTotal("2026-09", 5000), MonthTotal("2026-07", 3000))

        val points = buildTrend(expenses, emptyList(), september, months = 3)

        // A line drawn only through July and September would slope smoothly across an
        // August that actually had nothing in it.
        assertEquals(listOf("2026-07", "2026-08", "2026-09"), points.map { it.month.toString() })
        assertEquals(listOf(3000L, 0L, 5000L), points.map { it.spendMinor })
    }

    @Test
    fun `spend and income are matched up by month`() {
        val expenses = listOf(MonthTotal("2026-09", 5000))
        val income = listOf(MonthTotal("2026-08", 250_000), MonthTotal("2026-09", 250_000))

        val points = buildTrend(expenses, income, september, months = 2)

        assertEquals(listOf(0L, 5000L), points.map { it.spendMinor })
        assertEquals(listOf(250_000L, 250_000L), points.map { it.incomeMinor })
    }

    @Test
    fun `net is income minus spend per point`() {
        val points = buildTrend(
            listOf(MonthTotal("2026-09", 5000)),
            listOf(MonthTotal("2026-09", 3000)),
            september,
            months = 1,
        )

        assertEquals(-2000, points.single().netMinor)
    }

    @Test
    fun `data outside the window is ignored rather than folded into an edge month`() {
        val expenses = listOf(MonthTotal("2020-01", 999_999), MonthTotal("2026-09", 5000))

        val points = buildTrend(expenses, emptyList(), september, months = 2)

        assertEquals(listOf(0L, 5000L), points.map { it.spendMinor })
    }

    @Test
    fun `the storage key matches SQLite's month grouping`() {
        assertEquals("2026-09", september.storageKey())
        assertEquals("2026-01", YearMonth.of(2026, 1).storageKey())
    }
}
