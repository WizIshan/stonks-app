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
import kotlinx.coroutines.flow.Flow
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
