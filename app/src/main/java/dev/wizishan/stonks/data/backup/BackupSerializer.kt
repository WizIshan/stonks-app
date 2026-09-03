package dev.wizishan.stonks.data.backup

import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.Budget
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.RecurringType
import dev.wizishan.stonks.data.local.entity.Trip
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Turns the database into a backup file and back, with no Android dependencies — so the
 * format, its validation, and every awkward file a user could pick are testable on the JVM.
 */
object BackupSerializer {

    private val json = Json {
        prettyPrint = true
        // A file written by a later version will carry fields this one does not know. The
        // version check refuses those outright, so this only matters for fields added
        // within a version, where ignoring them is the right call rather than crashing.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: BackupFile): String = json.encodeToString(BackupFile.serializer(), snapshot)

    /**
     * Parse and validate. Everything that can be wrong with a file is decided here, before
     * a single row is written.
     */
    fun decode(text: String): Result<BackupFile> {
        val parsed = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (_: Exception) {
            // Any parse failure is the same thing to a user: this is not a Stonks backup.
            return Result.failure(ImportException(ImportFailure.NotJson))
        }

        if (parsed.version < BackupFile.MINIMUM_SUPPORTED_VERSION ||
            parsed.version > BackupFile.CURRENT_VERSION
        ) {
            return Result.failure(ImportException(ImportFailure.UnsupportedVersion(parsed.version)))
        }

        validate(parsed)?.let { return Result.failure(ImportException(ImportFailure.Invalid(it))) }
        return Result.success(parsed)
    }

    /**
     * Returns a reason if the file could not be written to the database as it stands.
     *
     * Checked up front rather than relying on the database's own constraints, because a
     * foreign key failure part-way through says nothing useful to a person holding a file
     * they thought was their records.
     */
    private fun validate(file: BackupFile): String? {
        val categoryIds = file.categories.map { it.id }.toSet()
        val tripIds = file.trips.map { it.id }.toSet()

        if (categoryIds.size != file.categories.size) return "duplicate category ids"
        if (tripIds.size != file.trips.size) return "duplicate trip ids"

        file.expenses.forEach { expense ->
            if (expense.categoryId !in categoryIds) return "expense ${expense.id} names a missing category"
            if (expense.tripId != null && expense.tripId !in tripIds) return "expense ${expense.id} names a missing trip"
            parseDate(expense.date) ?: return "expense ${expense.id} has an unreadable date"
        }
        file.income.forEach { income ->
            parseDate(income.date) ?: return "income ${income.id} has an unreadable date"
        }
        file.budgets.forEach { budget ->
            if (budget.categoryId != null && budget.categoryId !in categoryIds) {
                return "budget ${budget.id} names a missing category"
            }
        }
        file.recurringRules.forEach { rule ->
            if (rule.categoryId != null && rule.categoryId !in categoryIds) {
                return "recurring rule ${rule.id} names a missing category"
            }
            if (rule.tripId != null && rule.tripId !in tripIds) {
                return "recurring rule ${rule.id} names a missing trip"
            }
            runCatching { RecurringType.valueOf(rule.type) }.getOrNull()
                ?: return "recurring rule ${rule.id} has an unknown type"
            runCatching { RecurringFrequency.valueOf(rule.frequency) }.getOrNull()
                ?: return "recurring rule ${rule.id} has an unknown frequency"
            parseDate(rule.startDate) ?: return "recurring rule ${rule.id} has an unreadable start date"
            parseDate(rule.nextDueDate) ?: return "recurring rule ${rule.id} has an unreadable next date"
        }
        file.trips.forEach { trip ->
            trip.startDate?.let { parseDate(it) ?: return "trip ${trip.id} has an unreadable start date" }
            trip.endDate?.let { parseDate(it) ?: return "trip ${trip.id} has an unreadable end date" }
        }
        return null
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    // ---- entity conversion -------------------------------------------------------

    fun snapshot(
        exportedAt: String,
        categories: List<Category>,
        trips: List<Trip>,
        expenses: List<Expense>,
        income: List<Income>,
        budgets: List<Budget>,
        rules: List<RecurringRule>,
    ) = BackupFile(
        exportedAt = exportedAt,
        currency = Money.currency.currencyCode,
        categories = categories.map { BackupCategory(it.id, it.name, it.colorHex) },
        trips = trips.map { BackupTrip(it.id, it.name, it.startDate?.toString(), it.endDate?.toString()) },
        expenses = expenses.map {
            BackupExpense(it.id, it.amountMinor, it.date.toString(), it.categoryId, it.tripId, it.note, it.recurringRuleId)
        },
        income = income.map {
            BackupIncome(it.id, it.amountMinor, it.date.toString(), it.source, it.note, it.recurringRuleId)
        },
        budgets = budgets.map {
            BackupBudget(it.id, it.categoryId, it.monthlyLimitMinor, it.alertThresholdPercent, it.notifiedThresholdMonth, it.notifiedOverMonth)
        },
        recurringRules = rules.map {
            BackupRecurringRule(
                id = it.id,
                type = it.type.name,
                amountMinor = it.amountMinor,
                categoryId = it.categoryId,
                source = it.source,
                tripId = it.tripId,
                frequency = it.frequency.name,
                startDate = it.startDate.toString(),
                nextDueDate = it.nextDueDate.toString(),
                note = it.note,
                isActive = it.isActive,
            )
        },
    )

    /**
     * Ids are carried across verbatim, not regenerated. Every relation in the file is
     * expressed by id, so renumbering on import would mean rewriting all of them — and one
     * missed reference silently reattaches an expense to the wrong category.
     */
    fun toCategories(file: BackupFile) = file.categories.map { Category(it.id, it.name, it.colorHex) }

    fun toTrips(file: BackupFile) = file.trips.map {
        Trip(it.id, it.name, it.startDate?.let(LocalDate::parse), it.endDate?.let(LocalDate::parse))
    }

    fun toExpenses(file: BackupFile) = file.expenses.map {
        Expense(it.id, it.amountMinor, LocalDate.parse(it.date), it.categoryId, it.tripId, it.note, it.recurringRuleId)
    }

    fun toIncome(file: BackupFile) = file.income.map {
        Income(it.id, it.amountMinor, LocalDate.parse(it.date), it.source, it.note, it.recurringRuleId)
    }

    fun toBudgets(file: BackupFile) = file.budgets.map {
        Budget(it.id, it.categoryId, it.monthlyLimitMinor, it.alertThresholdPercent, it.notifiedThresholdMonth, it.notifiedOverMonth)
    }

    fun toRecurringRules(file: BackupFile) = file.recurringRules.map {
        RecurringRule(
            id = it.id,
            type = RecurringType.valueOf(it.type),
            amountMinor = it.amountMinor,
            categoryId = it.categoryId,
            source = it.source,
            tripId = it.tripId,
            frequency = RecurringFrequency.valueOf(it.frequency),
            startDate = LocalDate.parse(it.startDate),
            nextDueDate = LocalDate.parse(it.nextDueDate),
            note = it.note,
            isActive = it.isActive,
        )
    }
}

class ImportException(val failure: ImportFailure) : Exception(failure.toString())
