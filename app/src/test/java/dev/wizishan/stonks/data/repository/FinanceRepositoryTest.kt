package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.dao.observeFiltered
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceRepositoryTest : DatabaseTest() {

    private val repository by lazy {
        repository()
    }

    @Test
    fun `adding an expense stores it with its trip and note`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val tripId = repository.addTrip("Japan 2026")

        repository.addExpense(
            amountMinor = 4250,
            date = LocalDate.parse("2026-09-01"),
            categoryId = foodId,
            tripId = tripId,
            note = "Ramen",
        )

        val row = db.expenseDao().observeFiltered().first().single()
        assertEquals(4250, row.amountMinor)
        assertEquals("Japan 2026", row.tripName)
        assertEquals("Ramen", row.note)
    }

    @Test
    fun `a blank note is stored as null rather than an empty string`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        repository.addExpense(1000, LocalDate.parse("2026-09-01"), foodId, note = "   ")

        assertNull(db.expenseDao().observeFiltered().first().single().note)
    }

    @Test
    fun `income source is trimmed`() = runTest {
        repository.addIncome(250_000, LocalDate.parse("2026-09-01"), source = "  Salary  ")

        assertEquals(listOf("Salary"), db.incomeDao().observeSources().first())
    }

    @Test
    fun `a new category takes the next free slot`() = runTest {
        // The eight defaults occupy every slot, so free one first.
        val groceries = requireNotNull(db.categoryDao().getByName("Groceries"))
        db.categoryDao().delete(groceries)

        val result = repository.addCategory("Coffee")

        assertTrue(result is AddCategoryResult.Created)
        val created = requireNotNull(db.categoryDao().getByName("Coffee"))
        assertEquals("#008300", created.colorHex)
    }

    @Test
    fun `a ninth category is refused rather than given an invented hue`() = runTest {
        val result = repository.addCategory("Coffee")

        assertEquals(AddCategoryResult.NoFreeSlot, result)
        assertNull(db.categoryDao().getByName("Coffee"))
    }

    @Test
    fun `once slots run out the user can pick an existing one`() = runTest {
        val result = repository.addCategoryOnSlot("Coffee", CategorySlots.all.first().lightHex)

        assertTrue(result is AddCategoryResult.Created)
        assertEquals("#2A78D6", db.categoryDao().getByName("Coffee")?.colorHex)
    }

    @Test
    fun `a slot that is not in the palette is refused`() = runTest {
        assertEquals(AddCategoryResult.InvalidName, repository.addCategoryOnSlot("Coffee", "#123456"))
    }

    @Test
    fun `duplicate and blank category names are refused`() = runTest {
        assertEquals(AddCategoryResult.NameTaken, repository.addCategory("Transport"))
        assertEquals(AddCategoryResult.NameTaken, repository.addCategory("  transport  "))
        assertEquals(AddCategoryResult.InvalidName, repository.addCategory("   "))
    }

    @Test
    fun `reassigning and deleting a category moves its history and then removes it`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val groceriesId = seededCategoryId("Groceries")
        repository.addExpense(1000, LocalDate.parse("2026-09-01"), foodId)
        repository.addExpense(2000, LocalDate.parse("2026-09-02"), foodId)

        repository.reassignAndDeleteCategory(foodId, groceriesId)

        assertNull(db.categoryDao().getById(foodId))
        assertEquals(2, db.expenseDao().countInCategory(groceriesId))
    }
}
