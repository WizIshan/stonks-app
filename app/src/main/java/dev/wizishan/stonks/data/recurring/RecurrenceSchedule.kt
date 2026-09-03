package dev.wizishan.stonks.data.recurring

import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a rule owes as of a given day, and where it should pick up next. */
data class DueOccurrences(
    val dates: List<LocalDate>,
    val nextDueDate: LocalDate,
)

/**
 * Schedule arithmetic, kept pure so the awkward cases are testable without a database.
 *
 * The awkward case is monthly. Advancing `nextDueDate` by one month repeatedly drifts: a
 * rule starting on 31 January lands on 28 February, and stepping from *there* gives 28
 * March, so the rule silently moves off month-end forever. Occurrences are therefore
 * counted from [RecurringRule.startDate] — `startDate.plusMonths(n)` — which clamps to the
 * short month and returns to the 31st afterwards, the behaviour a person expects from
 * "the last day of the month".
 */
object RecurrenceSchedule {

    /**
     * Catch-up is capped. A rule left paused for years, or a corrupted date, should not be
     * able to spend an unbounded amount of time generating rows on a background thread.
     */
    const val MAX_CATCH_UP = 400

    fun nextAfter(
        startDate: LocalDate,
        frequency: RecurringFrequency,
        current: LocalDate,
    ): LocalDate = when (frequency) {
        RecurringFrequency.DAILY -> current.plusDays(1)
        RecurringFrequency.WEEKLY -> current.plusWeeks(1)
        RecurringFrequency.MONTHLY -> {
            val elapsed = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), current.withDayOfMonth(1))
            startDate.plusMonths(elapsed + 1)
        }
    }

    /**
     * Every occurrence from [RecurringRule.nextDueDate] up to and including [today].
     *
     * The catch-up matters: if the app is not opened for a week, a daily rule owes seven
     * entries, not one. Generating only the latest would quietly lose six days of rent or
     * subscriptions.
     */
    fun occurrencesDue(rule: RecurringRule, today: LocalDate): DueOccurrences {
        val dates = mutableListOf<LocalDate>()
        var cursor = rule.nextDueDate

        while (!cursor.isAfter(today) && dates.size < MAX_CATCH_UP) {
            dates += cursor
            cursor = nextAfter(rule.startDate, rule.frequency, cursor)
        }

        return DueOccurrences(dates = dates, nextDueDate = cursor)
    }
}
