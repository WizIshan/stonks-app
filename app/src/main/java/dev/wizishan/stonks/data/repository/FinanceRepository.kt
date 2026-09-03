package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.core.ColorMath
import dev.wizishan.stonks.data.budget.BudgetChecker
import dev.wizishan.stonks.data.budget.BudgetProgress
import dev.wizishan.stonks.data.local.dao.BudgetDao
import dev.wizishan.stonks.data.local.dao.CategoryDao
import dev.wizishan.stonks.data.local.dao.ExpenseDao
import dev.wizishan.stonks.data.local.dao.IncomeDao
import dev.wizishan.stonks.data.local.dao.RecurringRuleDao
import dev.wizishan.stonks.data.local.dao.TripDao
import dev.wizishan.stonks.data.local.entity.Budget
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.RecurringType
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.data.local.query.CategoryUsage
import dev.wizishan.stonks.data.local.query.TripUsage
import dev.wizishan.stonks.data.local.query.ExpenseListItem
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.local.query.IncomeListItem
import dev.wizishan.stonks.data.local.dao.observeFiltered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/**
 * The app's single entry point to stored data.
 *
 * Thin on purpose — it exists so screens depend on one seam rather than four DAOs, and so
 * rules that span tables (slot assignment for a new category, reassign-then-delete) live
 * in one place instead of being re-implemented per ViewModel.
 */
class FinanceRepository(
    private val categoryDao: CategoryDao,
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val incomeDao: IncomeDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val budgetDao: BudgetDao,
) {

    // ---- reads -------------------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeTrips(): Flow<List<Trip>> = tripDao.observeAll()

    fun observeIncomeSources(): Flow<List<String>> = incomeDao.observeSources()

    fun observeRecurringRules(): Flow<List<RecurringRule>> = recurringRuleDao.observeAll()

    fun observeBudgets(): Flow<List<Budget>> = budgetDao.observeAll()

    fun observeCategoryUsage(): Flow<List<CategoryUsage>> = categoryDao.observeUsage()

    fun observeTripUsage(): Flow<List<TripUsage>> = tripDao.observeUsage()

    suspend fun findCategoryByName(name: String): Category? = categoryDao.getByName(name.trim())

    suspend fun findTripByName(name: String): Trip? = tripDao.getByName(name.trim())

    /**
     * Every budget with this month's spend against it.
     *
     * The overall budget is fed the month total rather than the sum of the per-category
     * budgets: it is a limit on everything, including categories that have no budget of
     * their own.
     */
    fun observeBudgetProgress(month: YearMonth): Flow<List<BudgetProgress>> {
        val key = month.storageKey()
        return combine(
            budgetDao.observeAll(),
            categoryDao.observeAll(),
            expenseDao.observeTotalsByCategory(key),
            expenseDao.observeMonthTotal(key),
        ) { budgets, categories, categoryTotals, monthTotal ->
            val categoriesById = categories.associateBy { it.id }
            val spendByCategory = categoryTotals.associate { it.categoryId to it.totalMinor }

            budgets.mapNotNull { budget ->
                val category = budget.categoryId?.let(categoriesById::get)
                if (budget.categoryId != null && category == null) return@mapNotNull null

                BudgetProgress(
                    budgetId = budget.id,
                    categoryId = budget.categoryId,
                    label = category?.name ?: BudgetChecker.OVERALL_LABEL,
                    colorHex = category?.colorHex,
                    spentMinor = if (budget.categoryId == null) {
                        monthTotal
                    } else {
                        spendByCategory[budget.categoryId] ?: 0
                    },
                    limitMinor = budget.monthlyLimitMinor,
                    thresholdPercent = budget.alertThresholdPercent,
                )
            }.sortedWith(
                // Overall first — it frames everything below it — then biggest limit down.
                compareByDescending<BudgetProgress> { it.isOverall }
                    .thenByDescending { it.limitMinor },
            )
        }
    }

    /**
     * The History list: expenses and income as one stream.
     *
     * They live in separate tables, so each side is filtered and sorted in SQL and the
     * two are merged here. The merge re-sorts rather than interleaving, because a
     * comparator that spans both types is the only thing that can order a mixed list
     * consistently — and because CATEGORY_ASC and TRIP_ASC have no meaning for income,
     * which is why those push income to the end instead of inventing a key for it.
     *
     * [today] is passed in rather than read from the clock so period filters are testable.
     */
    fun observeHistory(
        filter: HistoryFilter,
        today: LocalDate = LocalDate.now(),
    ): Flow<List<HistoryItem>> {
        val range = filter.period.rangeOrNull(today)

        val expenses: Flow<List<HistoryItem>> =
            if (!filter.includesExpenses) {
                flowOf(emptyList())
            } else {
                expenseDao.observeFiltered(
                    categoryId = filter.categoryId,
                    tripId = filter.tripId,
                    from = range?.start,
                    to = range?.endInclusive,
                    sort = filter.sort,
                ).map { rows -> rows.map(ExpenseListItem::toHistoryItem) }
            }

        val income: Flow<List<HistoryItem>> =
            if (!filter.includesIncome) {
                flowOf(emptyList())
            } else {
                incomeDao.observeFiltered(
                    from = range?.start,
                    to = range?.endInclusive,
                    sort = filter.sort,
                ).map { rows -> rows.map(IncomeListItem::toHistoryItem) }
            }

        return combine(expenses, income) { spend, earned ->
            (spend + earned).sortedFor(filter.sort)
        }
    }

    /**
     * Everything the Dashboard shows for one month.
     *
     * Trip totals are deliberately not month-scoped: a trip spans whatever dates it spans,
     * and clipping it to a calendar month would report a fraction of the trip as the trip.
     */
    fun observeDashboard(
        month: YearMonth,
        trendRange: TrendRange = TrendRange.THREE_MONTHS,
    ): Flow<DashboardData> {
        val key = month.storageKey()

        val totals = combine(
            expenseDao.observeMonthTotal(key),
            incomeDao.observeMonthTotal(key),
        ) { spend, income -> spend to income }

        val breakdowns = combine(
            expenseDao.observeTotalsByCategory(key),
            expenseDao.observeTotalsByTrip(),
        ) { categories, trips -> categories.toRankedSlices() to trips.toTripSlices() }

        val trend = combine(
            expenseDao.observeMonthlyTotals(),
            incomeDao.observeMonthlyTotals(),
        ) { spend, income ->
            buildTrend(spend, income, month, monthsToCover(spend, income, month, trendRange))
        }

        return combine(totals, breakdowns, trend) { (spend, income), (byCategory, byTrip), points ->
            DashboardData(
                month = month,
                spendMinor = spend,
                incomeMinor = income,
                byCategory = byCategory,
                byTrip = byTrip,
                trend = points,
                trendRange = trendRange,
            )
        }
    }

    // ---- writes ------------------------------------------------------------------

    suspend fun addExpense(
        amountMinor: Long,
        date: LocalDate,
        categoryId: Long,
        tripId: Long? = null,
        note: String? = null,
    ): Long = expenseDao.insert(
        Expense(
            amountMinor = amountMinor,
            date = date,
            categoryId = categoryId,
            tripId = tripId,
            note = note?.takeIf { it.isNotBlank() },
        )
    )

    suspend fun addIncome(
        amountMinor: Long,
        date: LocalDate,
        source: String,
        note: String? = null,
    ): Long = incomeDao.insert(
        Income(
            amountMinor = amountMinor,
            date = date,
            source = source.trim(),
            note = note?.takeIf { it.isNotBlank() },
        )
    )

    /**
     * Create a category, taking the next free palette slot.
     *
     * Returns [NoFreeSlot] once all eight are used — the caller must then let the user
     * pick an existing slot, because the palette is never extended with a generated hue
     * (DESIGN.md §3b).
     */
    suspend fun addCategory(name: String): AddCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddCategoryResult.InvalidName
        if (categoryDao.getByName(trimmed) != null) return AddCategoryResult.NameTaken

        val slot = CategorySlots.nextFree(categoryDao.usedColorHexes())
            ?: return AddCategoryResult.NoFreeSlot
        return AddCategoryResult.Created(
            categoryDao.insert(Category(name = trimmed, colorHex = slot.lightHex))
        )
    }

    /**
      * Create a category in a colour the user chose.
      *
      * Any well-formed hex is accepted, not only the eight built-in slots. What keeps that
      * safe is that a category is always name-labelled, so colour reinforces identity
      * rather than carrying it, and that rendering adapts the lightness per surface —
      * see [dev.wizishan.stonks.ui.theme.CategoryPalette].
      */
    suspend fun addCategoryWithColor(name: String, colorHex: String): AddCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddCategoryResult.InvalidName
        if (!ColorMath.isValidHex(colorHex)) return AddCategoryResult.InvalidName
        if (categoryDao.getByName(trimmed) != null) return AddCategoryResult.NameTaken
        return AddCategoryResult.Created(
            categoryDao.insert(Category(name = trimmed, colorHex = colorHex.uppercase()))
        )
    }

    /**
     * Create a repeating expense.
     *
     * [startDate] doubles as the first due date, so the entry for today is generated by
     * the same path as every later one rather than being written here as a special case.
     * That keeps one code path responsible for what a rule produces.
     */
    suspend fun addRecurringExpense(
        amountMinor: Long,
        startDate: LocalDate,
        categoryId: Long,
        frequency: RecurringFrequency,
        tripId: Long? = null,
        note: String? = null,
    ): Long = recurringRuleDao.insert(
        RecurringRule(
            type = RecurringType.EXPENSE,
            amountMinor = amountMinor,
            categoryId = categoryId,
            tripId = tripId,
            frequency = frequency,
            startDate = startDate,
            nextDueDate = startDate,
            note = note?.takeIf { it.isNotBlank() },
        )
    )

    suspend fun addRecurringIncome(
        amountMinor: Long,
        startDate: LocalDate,
        source: String,
        frequency: RecurringFrequency,
        note: String? = null,
    ): Long = recurringRuleDao.insert(
        RecurringRule(
            type = RecurringType.INCOME,
            amountMinor = amountMinor,
            source = source.trim(),
            frequency = frequency,
            startDate = startDate,
            nextDueDate = startDate,
            note = note?.takeIf { it.isNotBlank() },
        )
    )

    /**
     * Pause or resume a rule.
     *
     * Resuming moves the cursor to today rather than replaying everything missed while it
     * was paused. Someone who paused a subscription for three months does not want three
     * months of charges appearing when they turn it back on.
     */
    suspend fun setRuleActive(ruleId: Long, active: Boolean, today: LocalDate = LocalDate.now()) {
        val rule = recurringRuleDao.getById(ruleId) ?: return
        if (active && rule.nextDueDate.isBefore(today)) {
            recurringRuleDao.setNextDueDate(ruleId, today)
        }
        recurringRuleDao.setActive(ruleId, active)
    }

    /** Entries the rule already generated are kept — they are spend that really happened. */
    suspend fun deleteRule(ruleId: Long) {
        recurringRuleDao.getById(ruleId)?.let { recurringRuleDao.delete(it) }
    }

    /**
     * Set or replace the limit for a category, or for everything when [categoryId] is null.
     *
     * Reuses the existing row's id so a changed limit keeps the month's alert history —
     * raising a limit you have already been warned about should not immediately warn you
     * again.
     */
    suspend fun setBudget(
        categoryId: Long?,
        monthlyLimitMinor: Long,
        alertThresholdPercent: Int = Budget.DEFAULT_THRESHOLD_PERCENT,
    ): Long {
        val existing = budgetDao.getForCategory(categoryId)
        return budgetDao.upsert(
            Budget(
                id = existing?.id ?: 0,
                categoryId = categoryId,
                monthlyLimitMinor = monthlyLimitMinor,
                alertThresholdPercent = alertThresholdPercent,
                notifiedThresholdMonth = existing?.notifiedThresholdMonth,
                notifiedOverMonth = existing?.notifiedOverMonth,
            )
        )
    }

    suspend fun deleteBudget(budgetId: Long) {
        budgetDao.getById(budgetId)?.let { budgetDao.delete(it) }
    }

    /** Rename a trip or change its dates. */
    suspend fun updateTrip(
        id: Long,
        name: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): TripResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return TripResult.InvalidName
        val clash = tripDao.getByName(trimmed)
        if (clash != null && clash.id != id) return TripResult.NameTaken
        val existing = tripDao.getById(id) ?: return TripResult.InvalidName
        tripDao.update(existing.copy(name = trimmed, startDate = startDate, endDate = endDate))
        return TripResult.Saved(id)
    }

    /**
     * Remove a trip.
     *
     * No reassignment step, unlike a category: the foreign key is SET_NULL, so the trip's
     * expenses simply stop being tagged and stay as ordinary spend. A trip is a grouping,
     * not something an expense needs one of.
     */
    suspend fun deleteTrip(id: Long) = tripDao.deleteById(id)

    suspend fun addTripChecked(
        name: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): TripResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return TripResult.InvalidName
        if (tripDao.getByName(trimmed) != null) return TripResult.NameTaken
        return TripResult.Saved(addTrip(trimmed, startDate, endDate))
    }

    suspend fun addTrip(name: String, startDate: LocalDate? = null, endDate: LocalDate? = null): Long =
        tripDao.insert(Trip(name = name.trim(), startDate = startDate, endDate = endDate))

    suspend fun updateExpense(
        id: Long,
        amountMinor: Long,
        date: LocalDate,
        categoryId: Long,
        tripId: Long? = null,
        note: String? = null,
    ) {
        val existing = expenseDao.getById(id) ?: return
        expenseDao.update(
            existing.copy(
                amountMinor = amountMinor,
                date = date,
                categoryId = categoryId,
                tripId = tripId,
                note = note?.takeIf { it.isNotBlank() },
            )
        )
    }

    /**
     * Editing keeps `recurringRuleId` — [existing.copy] carries it over rather than
     * clearing it. A generated entry someone corrected is still the entry that rule
     * generated, and losing that would let the rule create a duplicate for the same date.
     */
    suspend fun updateIncome(
        id: Long,
        amountMinor: Long,
        date: LocalDate,
        source: String,
        note: String? = null,
    ) {
        val existing = incomeDao.getById(id) ?: return
        incomeDao.update(
            existing.copy(
                amountMinor = amountMinor,
                date = date,
                source = source.trim(),
                note = note?.takeIf { it.isNotBlank() },
            )
        )
    }

    suspend fun getExpense(id: Long): Expense? = expenseDao.getById(id)

    suspend fun getIncome(id: Long): Income? = incomeDao.getById(id)

    suspend fun deleteExpense(id: Long) {
        expenseDao.getById(id)?.let { expenseDao.delete(it) }
    }

    suspend fun deleteIncome(id: Long) {
        incomeDao.getById(id)?.let { incomeDao.delete(it) }
    }

    /**
     * Remove one History row, whichever table it came from.
     *
     * Reads the row first rather than deleting by id so a row already gone (deleted on
     * another screen, or a stale list) is a no-op instead of an error.
     */
    suspend fun delete(item: HistoryItem) {
        when (item) {
            is HistoryItem.ExpenseItem -> expenseDao.getById(item.id)?.let { expenseDao.delete(it) }
            is HistoryItem.IncomeItem -> incomeDao.getById(item.id)?.let { incomeDao.delete(it) }
        }
    }

    /**
     * Rename a category, or move it to a different palette slot.
     *
     * The colour is stored on the row, so changing it here changes it everywhere at once —
     * the chart, the history chips and the budget meter all read the same value.
     */
    suspend fun updateCategory(
        id: Long,
        name: String,
        colorHex: String,
    ): AddCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddCategoryResult.InvalidName
        if (!ColorMath.isValidHex(colorHex)) return AddCategoryResult.InvalidName

        val clash = categoryDao.getByName(trimmed)
        if (clash != null && clash.id != id) return AddCategoryResult.NameTaken

        val existing = categoryDao.getById(id) ?: return AddCategoryResult.InvalidName
        categoryDao.update(existing.copy(name = trimmed, colorHex = colorHex.uppercase()))
        return AddCategoryResult.Created(id)
    }

    /**
     * Move everything off [fromCategoryId] and then delete it.
     *
     * Expenses and recurring rules both RESTRICT on category, so both have to move first;
     * the DAO does it in one transaction so a caller cannot do half of it.
     */
    suspend fun reassignAndDeleteCategory(fromCategoryId: Long, toCategoryId: Long) {
        if (fromCategoryId == toCategoryId) return
        categoryDao.reassignAndDelete(fromCategoryId, toCategoryId)
    }
}

sealed interface TripResult {
    data class Saved(val id: Long) : TripResult
    data object NameTaken : TripResult
    data object InvalidName : TripResult
}

sealed interface AddCategoryResult {
    data class Created(val id: Long) : AddCategoryResult
    data object NameTaken : AddCategoryResult
    data object InvalidName : AddCategoryResult
    data object NoFreeSlot : AddCategoryResult
}

private fun ExpenseListItem.toHistoryItem() = HistoryItem.ExpenseItem(
    id = id,
    date = date,
    amountMinor = amountMinor,
    note = note,
    categoryId = categoryId,
    categoryName = categoryName,
    categoryColorHex = categoryColorHex,
    tripId = tripId,
    tripName = tripName,
)

private fun IncomeListItem.toHistoryItem() = HistoryItem.IncomeItem(
    id = id,
    date = date,
    amountMinor = amountMinor,
    note = note,
    source = source,
)

/**
 * Orders a mixed list. Every comparator ends with the same tiebreakers so the order is
 * total: two entries on the same day for the same amount never swap places between reads.
 */
private fun List<HistoryItem>.sortedFor(sort: HistorySort): List<HistoryItem> {
    val incomeLast = compareBy<HistoryItem> { it is HistoryItem.IncomeItem }
    val categoryName = { item: HistoryItem -> (item as? HistoryItem.ExpenseItem)?.categoryName.orEmpty() }
    val tripName = { item: HistoryItem -> (item as? HistoryItem.ExpenseItem)?.tripName }

    val comparator = when (sort) {
        HistorySort.DATE_DESC -> compareByDescending<HistoryItem> { it.date }.then(incomeLast)
        HistorySort.DATE_ASC -> compareBy<HistoryItem> { it.date }.then(incomeLast)
        HistorySort.AMOUNT_DESC -> compareByDescending<HistoryItem> { it.amountMinor }.then(incomeLast)
        HistorySort.AMOUNT_ASC -> compareBy<HistoryItem> { it.amountMinor }.then(incomeLast)
        HistorySort.CATEGORY_ASC -> incomeLast.thenBy(String.CASE_INSENSITIVE_ORDER, categoryName)
        HistorySort.TRIP_ASC -> incomeLast
            .thenBy { tripName(it) == null }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { tripName(it).orEmpty() }
    }

    return sortedWith(comparator.thenByDescending { it.date }.thenByDescending { it.id })
}
