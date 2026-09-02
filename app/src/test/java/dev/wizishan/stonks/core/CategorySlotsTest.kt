package dev.wizishan.stonks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the palette against drift. The hexes are the output of
 * `tools/validate_palette.py`; changing one without re-running it would quietly break the
 * colourblind separation the chart design depends on (DESIGN.md §3b).
 */
class CategorySlotsTest {

    @Test
    fun `there are exactly eight slots`() {
        assertEquals(8, CategorySlots.all.size)
    }

    @Test
    fun `slot order matches the validated palette`() {
        assertEquals(
            listOf("#2A78D6", "#EB6834", "#1BAF7A", "#EDA100", "#E87BA4", "#008300", "#4A3AA7", "#E34948"),
            CategorySlots.all.map { it.lightHex },
        )
    }

    @Test
    fun `every hex is a well-formed six-digit colour`() {
        val hex = Regex("^#[0-9A-F]{6}$")
        (CategorySlots.all + CategorySlots.other).forEach { slot ->
            assertTrue("bad light hex ${slot.lightHex}", hex.matches(slot.lightHex))
            assertTrue("bad dark hex ${slot.darkHex}", hex.matches(slot.darkHex))
        }
    }

    @Test
    fun `light hexes are unique so a hex identifies one slot`() {
        val hexes = CategorySlots.all.map { it.lightHex }
        assertEquals(hexes.size, hexes.toSet().size)
    }

    @Test
    fun `the reserved grey is not one of the eight`() {
        assertTrue(CategorySlots.other.lightHex !in CategorySlots.all.map { it.lightHex })
    }

    @Test
    fun `lookup by hex is case and whitespace insensitive`() {
        assertEquals("blue", CategorySlots.forHex("#2a78d6")?.hue)
        assertEquals("blue", CategorySlots.forHex(" #2A78D6 ")?.hue)
        assertNull(CategorySlots.forHex("#123456"))
        assertNull(CategorySlots.forHex(null))
    }

    @Test
    fun `slots are handed out in order`() {
        assertEquals("#2A78D6", CategorySlots.nextFree(emptyList())?.lightHex)
        assertEquals("#EB6834", CategorySlots.nextFree(listOf("#2A78D6"))?.lightHex)
        // Skips the taken ones rather than cycling or generating a new hue.
        assertEquals("#1BAF7A", CategorySlots.nextFree(listOf("#eb6834", "#2A78D6"))?.lightHex)
    }

    @Test
    fun `there is no ninth slot`() {
        assertNull(CategorySlots.nextFree(CategorySlots.all.map { it.lightHex }))
    }

    @Test
    fun `every slot has a default category name`() {
        CategorySlots.all.forEach { assertTrue(it.defaultCategoryName.isNotBlank()) }
        assertEquals(8, CategorySlots.all.map { it.defaultCategoryName }.toSet().size)
        assertNotNull(CategorySlots.all.firstOrNull { it.defaultCategoryName == "Food & Drink" })
    }
}
