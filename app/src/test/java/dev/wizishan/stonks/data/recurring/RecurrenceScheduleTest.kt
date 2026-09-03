package dev.wizishan.stonks.data.recurring

import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.RecurringType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceScheduleTest {

    private fun rule(
        start: String,
        next: String = start,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    ) = RecurringRule(
        id = 1,
        type = RecurringType.EXPENSE,
        amountMinor = 1000,
        categoryId = 1,
        frequency = frequency,
        startDate = LocalDate.parse(start),
        nextDueDate = LocalDate.parse(next),
    )

    private fun next(start: String, frequency: RecurringFrequency, current: String) =
        RecurrenceSchedule.nextAfter(LocalDate.parse(start), frequency, LocalDate.parse(current))

    // ---- step arithmetic ---------------------------------------------------------

    @Test
    fun `daily and weekly steps are plain offsets`() {
        assertEquals(LocalDate.parse("2026-09-02"), next("2026-09-01", RecurringFrequency.DAILY, "2026-09-01"))
        assertEquals(LocalDate.parse("2026-09-08"), next("2026-09-01", RecurringFrequency.WEEKLY, "2026-09-01"))
    }

    @Test
    fun `daily stepping crosses a month boundary`() {
        assertEquals(LocalDate.parse("2026-10-01"), next("2026-09-01", RecurringFrequency.DAILY, "2026-09-30"))
    }

    @Test
    fun `monthly keeps the day of month`() {
        assertEquals(LocalDate.parse("2026-10-15"), next("2026-09-15", RecurringFrequency.MONTHLY, "2026-09-15"))
    }

    @Test
    fun `a month-end rule clamps into February and then comes back`() {
        // The whole reason occurrences are counted from startDate: stepping 31 Jan by one
        // month gives 28 Feb, and stepping from 28 Feb would give 28 Mar — the rule would
        // silently slide off month-end and never return.
        val feb = next("2026-01-31", RecurringFrequency.MONTHLY, "2026-01-31")
        assertEquals(LocalDate.parse("2026-02-28"), feb)

        val mar = next("2026-01-31", RecurringFrequency.MONTHLY, feb.toString())
        assertEquals(LocalDate.parse("2026-03-31"), mar)

        val apr = next("2026-01-31", RecurringFrequency.MONTHLY, mar.toString())
        assertEquals(LocalDate.parse("2026-04-30"), apr)
    }

    @Test
    fun `a leap year February is respected`() {
        assertEquals(
            LocalDate.parse("2028-02-29"),
            next("2028-01-31", RecurringFrequency.MONTHLY, "2028-01-31"),
        )
    }

    // ---- catch-up ----------------------------------------------------------------

    @Test
    fun `nothing is due before the start date`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2026-10-01", frequency = RecurringFrequency.DAILY),
            LocalDate.parse("2026-09-20"),
        )
        assertTrue(due.dates.isEmpty())
        assertEquals(LocalDate.parse("2026-10-01"), due.nextDueDate)
    }

    @Test
    fun `the day it starts counts as due`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2026-09-01", frequency = RecurringFrequency.MONTHLY),
            LocalDate.parse("2026-09-01"),
        )
        assertEquals(listOf(LocalDate.parse("2026-09-01")), due.dates)
        assertEquals(LocalDate.parse("2026-10-01"), due.nextDueDate)
    }

    @Test
    fun `a week away from the app owes seven daily entries, not one`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2026-09-01", frequency = RecurringFrequency.DAILY),
            LocalDate.parse("2026-09-07"),
        )
        assertEquals(7, due.dates.size)
        assertEquals(LocalDate.parse("2026-09-01"), due.dates.first())
        assertEquals(LocalDate.parse("2026-09-07"), due.dates.last())
        assertEquals(LocalDate.parse("2026-09-08"), due.nextDueDate)
    }

    @Test
    fun `catching up on months preserves month-end`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2026-01-31", frequency = RecurringFrequency.MONTHLY),
            LocalDate.parse("2026-04-15"),
        )
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31").map(LocalDate::parse),
            due.dates,
        )
        assertEquals(LocalDate.parse("2026-04-30"), due.nextDueDate)
    }

    @Test
    fun `a rule resumed mid-stream picks up from its cursor, not its start`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule(start = "2026-01-01", next = "2026-09-01", frequency = RecurringFrequency.MONTHLY),
            LocalDate.parse("2026-09-15"),
        )
        assertEquals(listOf(LocalDate.parse("2026-09-01")), due.dates)
    }

    @Test
    fun `catch-up is capped so a stale rule cannot run away`() {
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2000-01-01", frequency = RecurringFrequency.DAILY),
            LocalDate.parse("2026-09-01"),
        )
        assertEquals(RecurrenceSchedule.MAX_CATCH_UP, due.dates.size)
    }

    @Test
    fun `the returned cursor always sits after today`() {
        val today = LocalDate.parse("2026-09-07")
        val due = RecurrenceSchedule.occurrencesDue(
            rule("2026-09-01", frequency = RecurringFrequency.WEEKLY),
            today,
        )
        assertTrue(due.nextDueDate.isAfter(today))
    }
}
