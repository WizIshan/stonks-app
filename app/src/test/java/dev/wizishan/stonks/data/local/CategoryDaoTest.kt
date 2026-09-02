package dev.wizishan.stonks.data.local

import android.database.sqlite.SQLiteConstraintException
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.entity.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDaoTest : DatabaseTest() {

    private val dao get() = db.categoryDao()

    @Test
    fun `seeds the eight default categories`() = runTest {
        assertEquals(8, dao.count())
    }

    @Test
    fun `seeded categories carry the palette hex for their slot`() = runTest {
        CategorySlots.all.forEach { slot ->
            val category = dao.getByName(slot.defaultCategoryName)
            assertNotNull("missing seeded category ${slot.defaultCategoryName}", category)
            assertEquals(slot.lightHex, category!!.colorHex)
        }
    }

    @Test
    fun `seeded categories use every slot exactly once`() = runTest {
        val used = dao.usedColorHexes()
        assertEquals(CategorySlots.all.map { it.lightHex }.toSet(), used.toSet())
        assertNull("all eight slots are taken", CategorySlots.nextFree(used))
    }

    @Test
    fun `a new category takes the next free slot`() = runTest {
        // Free up one slot, then check the assignment picks it rather than inventing a hue.
        val groceries = requireNotNull(dao.getByName("Groceries"))
        dao.delete(groceries)

        val next = CategorySlots.nextFree(dao.usedColorHexes())
        assertEquals("#008300", next?.lightHex)
    }

    @Test
    fun `observeAll is ordered by name case-insensitively`() = runTest {
        category(name = "apples", hex = "#2A78D6")
        val names = dao.observeAll().first().map { it.name }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `duplicate category names are rejected`() = runTest {
        dao.insert(Category(name = "Transport", colorHex = "#EB6834"))
    }

    @Test
    fun `deleting a category still in use is refused`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 1250, date = "2026-09-01", categoryId = foodId)

        val food = requireNotNull(dao.getById(foodId))
        val threw = try {
            dao.delete(food)
            false
        } catch (_: SQLiteConstraintException) {
            true
        }

        assertTrue("expected the RESTRICT foreign key to refuse the delete", threw)
        assertNotNull("the category must survive a refused delete", dao.getById(foodId))
        assertEquals(1, db.expenseDao().countInCategory(foodId))
    }

    @Test
    fun `reassigning expenses first makes the delete succeed`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val groceriesId = seededCategoryId("Groceries")
        expense(amountMinor = 1250, date = "2026-09-01", categoryId = foodId)

        val moved = db.expenseDao().reassignCategory(foodId, groceriesId)
        dao.delete(requireNotNull(dao.getById(foodId)))

        assertEquals(1, moved)
        assertNull(dao.getById(foodId))
        assertEquals(1, db.expenseDao().countInCategory(groceriesId))
    }
}
