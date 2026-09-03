package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.dao.CategoryDao
import dev.wizishan.stonks.data.local.dao.ExpenseDao
import dev.wizishan.stonks.data.local.dao.IncomeDao
import dev.wizishan.stonks.data.local.dao.TripDao
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.data.local.query.ExpenseListItem
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.local.query.IncomeListItem
import dev.wizishan.stonks.data.local.dao.observeFiltered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
) {

    // ---- reads -------------------------------------------------------------------

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeTrips(): Flow<List<Trip>> = tripDao.observeAll()

    fun observeIncomeSources(): Flow<List<String>> = incomeDao.observeSources()

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

    /** Create a category on a slot the user chose, once the eight are used up. */
    suspend fun addCategoryOnSlot(name: String, colorHex: String): AddCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddCategoryResult.InvalidName
        if (categoryDao.getByName(trimmed) != null) return AddCategoryResult.NameTaken
        if (CategorySlots.forHex(colorHex) == null) return AddCategoryResult.InvalidName
        return AddCategoryResult.Created(
            categoryDao.insert(Category(name = trimmed, colorHex = colorHex))
        )
    }

    suspend fun addTrip(name: String, startDate: LocalDate? = null, endDate: LocalDate? = null): Long =
        tripDao.insert(Trip(name = name.trim(), startDate = startDate, endDate = endDate))

    /**
     * Move every expense off [fromCategoryId] and then delete it.
     *
     * The foreign key is RESTRICT, so a category with history cannot be deleted directly;
     * this is the supported way through, and it keeps the two steps in one place so a
     * caller can't do half of it.
     */
    suspend fun reassignAndDeleteCategory(fromCategoryId: Long, toCategoryId: Long) {
        expenseDao.reassignCategory(fromCategoryId, toCategoryId)
        categoryDao.getById(fromCategoryId)?.let { categoryDao.delete(it) }
    }
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
