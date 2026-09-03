package dev.wizishan.stonks.data.budget

import dev.wizishan.stonks.data.local.entity.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetProgressTest {

    private fun progress(spent: Long, limit: Long = 100_00, threshold: Int = 80) =
        BudgetProgress(
            budgetId = 1,
            categoryId = 1,
            label = "Food & Drink",
            colorHex = "#2A78D6",
            spentMinor = spent,
            limitMinor = limit,
            thresholdPercent = threshold,
        )

    @Test
    fun `bands follow the threshold, the limit, and well past it`() {
        assertEquals(BudgetStatus.HEALTHY, progress(spent = 50_00).status)
        assertEquals(BudgetStatus.WARNING, progress(spent = 80_00).status)
        assertEquals(BudgetStatus.WARNING, progress(spent = 99_00).status)
        assertEquals(BudgetStatus.OVER, progress(spent = 100_00).status)
        assertEquals(BudgetStatus.OVER, progress(spent = 119_00).status)
        assertEquals(BudgetStatus.CRITICAL, progress(spent = 120_00).status)
    }

    @Test
    fun `the threshold is configurable`() {
        assertEquals(BudgetStatus.HEALTHY, progress(spent = 50_00, threshold = 90).status)
        assertEquals(BudgetStatus.WARNING, progress(spent = 90_00, threshold = 90).status)
    }

    @Test
    fun `percent is uncapped so the label can say how far over`() {
        assertEquals(150, progress(spent = 150_00).percent)
        assertEquals(0, progress(spent = 0).percent)
    }

    @Test
    fun `the bar stops at the end of its track even when spend does not`() {
        // DESIGN.md §5: overflow is reported by the label, not by a bar running past its
        // own track, which would have nothing to be measured against.
        assertEquals(1f, progress(spent = 150_00).fillFraction)
        assertEquals(0.5f, progress(spent = 50_00).fillFraction)
    }

    @Test
    fun `remaining goes negative once the limit is passed`() {
        assertEquals(20_00, progress(spent = 80_00).remainingMinor)
        assertEquals(-50_00, progress(spent = 150_00).remainingMinor)
    }

    @Test
    fun `a zero limit cannot divide by zero`() {
        val zero = progress(spent = 5_00, limit = 0)
        assertEquals(0, zero.percent)
        assertEquals(0f, zero.fillFraction)
    }

    // ---- alert eligibility -------------------------------------------------------

    private fun budget(thresholdMonth: String? = null, overMonth: String? = null) = Budget(
        id = 1,
        categoryId = 1,
        monthlyLimitMinor = 100_00,
        alertThresholdPercent = 80,
        notifiedThresholdMonth = thresholdMonth,
        notifiedOverMonth = overMonth,
    )

    @Test
    fun `a healthy budget raises nothing`() {
        assertNull(budget().alertFor(BudgetStatus.HEALTHY, "2026-09"))
    }

    @Test
    fun `crossing the threshold alerts once per month`() {
        assertEquals(BudgetAlert.THRESHOLD, budget().alertFor(BudgetStatus.WARNING, "2026-09"))
        assertNull(budget(thresholdMonth = "2026-09").alertFor(BudgetStatus.WARNING, "2026-09"))
    }

    @Test
    fun `a new month starts quiet again`() {
        assertEquals(
            BudgetAlert.THRESHOLD,
            budget(thresholdMonth = "2026-08").alertFor(BudgetStatus.WARNING, "2026-09"),
        )
    }

    @Test
    fun `going over still alerts after a threshold warning was already sent`() {
        // "You're close" and "you're over" are different things to be told.
        assertEquals(
            BudgetAlert.OVER,
            budget(thresholdMonth = "2026-09").alertFor(BudgetStatus.OVER, "2026-09"),
        )
    }

    @Test
    fun `the over alert is not repeated within a month`() {
        assertNull(
            budget(thresholdMonth = "2026-09", overMonth = "2026-09")
                .alertFor(BudgetStatus.OVER, "2026-09")
        )
    }

    @Test
    fun `critical is treated as over for alerting`() {
        assertEquals(BudgetAlert.OVER, budget().alertFor(BudgetStatus.CRITICAL, "2026-09"))
        assertNull(budget(overMonth = "2026-09").alertFor(BudgetStatus.CRITICAL, "2026-09"))
    }
}
