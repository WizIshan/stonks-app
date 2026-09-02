package dev.wizishan.stonks.core

/**
 * One slot of the fixed categorical palette: a single hue, stepped for each surface.
 *
 * Plain Kotlin with no Compose dependency, so the data layer can seed and assign slots
 * without reaching into the UI. [dev.wizishan.stonks.ui.theme.CategoryPalette] is the
 * Compose adapter over this.
 */
data class CategorySlot(
    val hue: String,
    /** Canonical identifier, persisted in `Category.colorHex`. */
    val lightHex: String,
    val darkHex: String,
    val defaultCategoryName: String,
)

/**
 * The fixed 8-slot category identity palette. See DESIGN.md §3b.
 *
 * Slots are assigned **in order** and never cycled, randomised, or generated. Past slot 8
 * the user picks an existing slot — duplicates are acceptable because every category is
 * always name-labelled, so colour is never the sole identifier.
 *
 * Validated with `tools/validate_palette.py` against the Material 3 baseline surfaces
 * (light `#FEF7FF`, dark `#141218`); passes every check in both modes on the adjacent
 * pairlist. Do not edit a hex without re-running it.
 */
object CategorySlots {

    val all: List<CategorySlot> = listOf(
        CategorySlot("blue", "#2A78D6", "#3987E5", "Food & Drink"),
        CategorySlot("orange", "#EB6834", "#D95926", "Transport"),
        CategorySlot("aqua", "#1BAF7A", "#199E70", "Lodging"),
        CategorySlot("yellow", "#EDA100", "#C98500", "Shopping"),
        CategorySlot("magenta", "#E87BA4", "#D55181", "Entertainment"),
        CategorySlot("green", "#008300", "#008300", "Groceries"),
        CategorySlot("violet", "#4A3AA7", "#9085E9", "Health"),
        CategorySlot("red", "#E34948", "#E66767", "Bills & Utilities"),
    )

    /**
     * Reserved grey for the "Other" bucket that charts fold small categories into.
     * Never assign this to a real category.
     */
    val other = CategorySlot("other", "#898781", "#898781", "Other")

    private val byHex: Map<String, CategorySlot> =
        (all + other).associateBy { it.lightHex.uppercase() }

    /** The slot a stored hex belongs to, or null if the hex is not from this palette. */
    fun forHex(hex: String?): CategorySlot? = hex?.let { byHex[it.trim().uppercase()] }

    /**
     * The next slot to hand a newly created category, given the ones already in use.
     * Returns null once all eight are taken — the caller must then let the user pick.
     */
    fun nextFree(usedHexes: Collection<String>): CategorySlot? {
        val used = usedHexes.mapTo(HashSet()) { it.trim().uppercase() }
        return all.firstOrNull { it.lightHex.uppercase() !in used }
    }
}
