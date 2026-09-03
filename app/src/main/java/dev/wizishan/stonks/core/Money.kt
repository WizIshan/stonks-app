package dev.wizishan.stonks.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money is stored everywhere as a **Long count of minor units** — 8250 means €82.50.
 *
 * Never store or sum an amount as a Double: binary floating point cannot represent 0.10
 * exactly, so totals drift and a budget can trip its alert threshold a cent early. An
 * integer count of cents is exact under addition, which is all this app ever does with
 * money. Doubles appear only inside chart geometry, where the value is a pixel position
 * rather than an amount.
 *
 * v1 is single-currency (EUR). Per-row currency and FX are a documented later feature;
 * keeping the currency in one place here is what makes that change small.
 */
object Money {

    val currency: Currency = Currency.getInstance("EUR")

    /** Minor units per major unit — 100 for EUR. */
    val minorUnitScale: Int = currency.defaultFractionDigits

    /** `8250` -> `"€82.50"`, localised for the given locale. */
    fun format(minor: Long, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Money.currency
            minimumFractionDigits = minorUnitScale
            maximumFractionDigits = minorUnitScale
        }
        return format.format(toMajor(minor))
    }

    /**
     * `8250` -> `"+€82.50"` / `-4000` -> `"−€40.00"`.
     *
     * The sign is always rendered so polarity never depends on colour alone
     * (DESIGN.md §3d). Uses a true minus sign, not a hyphen.
     */
    fun formatSigned(minor: Long, locale: Locale = Locale.getDefault()): String {
        val magnitude = format(kotlin.math.abs(minor), locale)
        return if (minor < 0) "−$magnitude" else "+$magnitude"
    }

    /** `8250` -> `82.50`. For display and chart geometry only — never for summing. */
    fun toMajor(minor: Long): BigDecimal =
        BigDecimal.valueOf(minor).movePointLeft(minorUnitScale)

    /** A sign, digits, and separators that always have digits after them. */
    private val SHAPE = Regex("""^[+-]?\d+(?:[.,]\d+)*$""")

    /**
     * Parse user input into minor units, or null if it isn't a usable amount.
     *
     * Accepts a currency symbol, spaces, either separator convention (`1,234.56` or
     * `1.234,56`), and a leading sign. Rejects anything carrying more precision than the
     * currency has: silently rounding someone's amount would be worse than making them
     * retype it.
     */
    fun parseOrNull(input: String): Long? {
        // The symbol may lead or trail, and may sit inside a sign ("-€5"), so strip it
        // wherever it appears rather than only at the ends. NBSP is not matched by
        // Char.isWhitespace() and currency formatters emit it, so remove it explicitly.
        val cleaned = input
            .replace(currency.symbol, "")
            .replace(currency.currencyCode, "")
            .filterNot { it.isWhitespace() || it == '\u00A0' }
            .ifEmpty { return null }
            .also { if (!SHAPE.matches(it)) return null }
            .let(::normaliseSeparators)

        val decimal = try {
            BigDecimal(cleaned)
        } catch (_: NumberFormatException) {
            return null
        }
        if (decimal.scale() > minorUnitScale) return null

        return try {
            decimal.movePointRight(minorUnitScale).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Reduce both separator conventions to a plain `1234.56`.
     *
     * A **lone** separator is always read as the decimal point, whichever character it is.
     * `1,234` therefore reads as 1.234 and gets rejected for excess precision rather than
     * being silently taken as one thousand two hundred and thirty four — the two readings
     * differ by a factor of a thousand, and guessing wrong on someone's money is far worse
     * than asking them to retype it without the separator.
     *
     * With two or more separators the layout is unambiguous: the last one is the decimal
     * point and the rest are grouping.
     */
    private fun normaliseSeparators(value: String): String {
        val separatorCount = value.count { it == ',' || it == '.' }
        if (separatorCount == 0) return value
        if (separatorCount == 1) return value.replace(',', '.')

        val decimalPos = maxOf(value.lastIndexOf(','), value.lastIndexOf('.'))
        val whole = value.substring(0, decimalPos).filterNot { it == ',' || it == '.' }
        val fraction = value.substring(decimalPos + 1)
        return "$whole.$fraction"
    }
}
