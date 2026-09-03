package dev.wizishan.stonks.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The format and its validation, with no database and no Android. */
class BackupSerializerTest {

    private fun file(
        version: Int = BackupFile.CURRENT_VERSION,
        categories: List<BackupCategory> = listOf(BackupCategory(1, "Food & Drink", "#2A78D6")),
        trips: List<BackupTrip> = emptyList(),
        expenses: List<BackupExpense> = emptyList(),
        income: List<BackupIncome> = emptyList(),
        budgets: List<BackupBudget> = emptyList(),
        rules: List<BackupRecurringRule> = emptyList(),
    ) = BackupFile(
        version = version,
        exportedAt = "2026-09-03T10:00:00Z",
        currency = "EUR",
        categories = categories,
        trips = trips,
        expenses = expenses,
        income = income,
        budgets = budgets,
        recurringRules = rules,
    )

    private fun decodeFailure(text: String): ImportFailure =
        (BackupSerializer.decode(text).exceptionOrNull() as ImportException).failure

    @Test
    fun `a file round-trips unchanged`() {
        val original = file(
            trips = listOf(BackupTrip(1, "Japan 2026", "2026-04-01", "2026-04-14")),
            expenses = listOf(BackupExpense(1, 4250, "2026-09-01", 1, 1, "Ramen")),
            income = listOf(BackupIncome(1, 250_000, "2026-09-01", "Salary")),
            budgets = listOf(BackupBudget(1, 1, 50_000, 80)),
            rules = listOf(
                BackupRecurringRule(1, "EXPENSE", 95_000, 1, null, null, "MONTHLY", "2026-09-01", "2026-10-01")
            ),
        )

        val decoded = BackupSerializer.decode(BackupSerializer.encode(original)).getOrThrow()

        assertEquals(original, decoded)
    }

    @Test
    fun `the encoded file carries a version so a future reader knows what it has`() {
        val text = BackupSerializer.encode(file())
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"currency\": \"EUR\""))
    }

    @Test
    fun `an empty database exports and imports cleanly`() {
        val empty = file(categories = emptyList())
        assertEquals(empty, BackupSerializer.decode(BackupSerializer.encode(empty)).getOrThrow())
    }

    @Test
    fun `anything that is not the format is refused as unreadable`() {
        assertEquals(ImportFailure.NotJson, decodeFailure(""))
        assertEquals(ImportFailure.NotJson, decodeFailure("not json at all"))
        assertEquals(ImportFailure.NotJson, decodeFailure("{}"))
        assertEquals(ImportFailure.NotJson, decodeFailure("""{"version":1}"""))
    }

    @Test
    fun `a file from a newer version is refused rather than partly read`() {
        // Reading it by ignoring unknown fields would silently drop data the user believes
        // they just restored.
        val failure = decodeFailure(BackupSerializer.encode(file(version = 99)))
        assertEquals(ImportFailure.UnsupportedVersion(99), failure)
    }

    @Test
    fun `unknown fields within a supported version are ignored, not fatal`() {
        val text = BackupSerializer.encode(file()).replace(
            "\"currency\": \"EUR\"",
            "\"currency\": \"EUR\",\n  \"somethingAddedLater\": true",
        )
        assertTrue(BackupSerializer.decode(text).isSuccess)
    }

    @Test
    fun `an expense pointing at a category the file does not contain is refused`() {
        val failure = decodeFailure(
            BackupSerializer.encode(
                file(expenses = listOf(BackupExpense(1, 100, "2026-09-01", categoryId = 99)))
            )
        )
        assertTrue(failure is ImportFailure.Invalid)
        assertTrue((failure as ImportFailure.Invalid).reason.contains("missing category"))
    }

    @Test
    fun `an expense pointing at a missing trip is refused`() {
        val failure = decodeFailure(
            BackupSerializer.encode(
                file(expenses = listOf(BackupExpense(1, 100, "2026-09-01", 1, tripId = 42)))
            )
        )
        assertTrue((failure as ImportFailure.Invalid).reason.contains("missing trip"))
    }

    @Test
    fun `duplicate category ids are refused`() {
        val failure = decodeFailure(
            BackupSerializer.encode(
                file(
                    categories = listOf(
                        BackupCategory(1, "Food & Drink", "#2A78D6"),
                        BackupCategory(1, "Transport", "#EB6834"),
                    )
                )
            )
        )
        assertTrue((failure as ImportFailure.Invalid).reason.contains("duplicate category ids"))
    }

    @Test
    fun `an unreadable date is caught before anything is written`() {
        val failure = decodeFailure(
            BackupSerializer.encode(
                file(expenses = listOf(BackupExpense(1, 100, "not-a-date", 1)))
            )
        )
        assertTrue((failure as ImportFailure.Invalid).reason.contains("unreadable date"))
    }

    @Test
    fun `an unknown recurring type or frequency is refused`() {
        val badType = decodeFailure(
            BackupSerializer.encode(
                file(rules = listOf(BackupRecurringRule(1, "SOMETHING", 100, 1, null, null, "MONTHLY", "2026-09-01", "2026-10-01")))
            )
        )
        assertTrue((badType as ImportFailure.Invalid).reason.contains("unknown type"))

        val badFrequency = decodeFailure(
            BackupSerializer.encode(
                file(rules = listOf(BackupRecurringRule(1, "EXPENSE", 100, 1, null, null, "HOURLY", "2026-09-01", "2026-10-01")))
            )
        )
        assertTrue((badFrequency as ImportFailure.Invalid).reason.contains("unknown frequency"))
    }

    @Test
    fun `a budget naming a missing category is refused`() {
        val failure = decodeFailure(
            BackupSerializer.encode(file(budgets = listOf(BackupBudget(1, 99, 50_000, 80))))
        )
        assertTrue((failure as ImportFailure.Invalid).reason.contains("missing category"))
    }

    @Test
    fun `the overall budget has no category and is fine`() {
        val text = BackupSerializer.encode(file(budgets = listOf(BackupBudget(1, null, 50_000, 80))))
        assertTrue(BackupSerializer.decode(text).isSuccess)
    }

    @Test
    fun `income needs no category, so it is not checked against one`() {
        val text = BackupSerializer.encode(
            file(categories = emptyList(), income = listOf(BackupIncome(1, 100, "2026-09-01", "Salary")))
        )
        assertTrue(BackupSerializer.decode(text).isSuccess)
    }
}
