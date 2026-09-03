package dev.wizishan.stonks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    private fun lightness(hex: String) = requireNotNull(ColorMath.hexToOklch(hex)).lightness

    private fun hue(hex: String) = requireNotNull(ColorMath.hexToOklch(hex)).hueDegrees

    @Test
    fun `parses a hex with or without the hash`() {
        assertEquals(Triple(42, 120, 214), ColorMath.parseHex("#2A78D6"))
        assertEquals(Triple(42, 120, 214), ColorMath.parseHex("2a78d6"))
        assertEquals(Triple(42, 120, 214), ColorMath.parseHex("  #2A78D6  "))
    }

    @Test
    fun `rejects anything that is not a six-digit hex`() {
        assertNull(ColorMath.parseHex(null))
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#FFF"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex("#2A78D6FF"))
        assertFalse(ColorMath.isValidHex("nonsense"))
        assertTrue(ColorMath.isValidHex("#2A78D6"))
    }

    @Test
    fun `oklch survives a round trip`() {
        listOf("#2A78D6", "#EB6834", "#008300", "#E87BA4", "#FFFFFF", "#000000").forEach { hex ->
            val back = ColorMath.oklchToHex(requireNotNull(ColorMath.hexToOklch(hex)))
            // Allow a unit of rounding per channel; the conversion is lossy at 8 bits.
            val (r1, g1, b1) = requireNotNull(ColorMath.parseHex(hex))
            val (r2, g2, b2) = requireNotNull(ColorMath.parseHex(back))
            assertTrue("$hex -> $back", kotlin.math.abs(r1 - r2) <= 1)
            assertTrue("$hex -> $back", kotlin.math.abs(g1 - g2) <= 1)
            assertTrue("$hex -> $back", kotlin.math.abs(b1 - b2) <= 1)
        }
    }

    @Test
    fun `lightness is perceptual, not HSL`() {
        // The whole reason for using OKLCH: HSL calls both of these 50% lightness, which
        // is why clamping HSL would leave yellow glaring and blue nearly invisible.
        assertTrue("yellow should read far lighter than blue", lightness("#FFFF00") > lightness("#0000FF") + 0.3)
    }

    // ---- surface adaptation ------------------------------------------------------

    @Test
    fun `a colour already in the band is returned untouched`() {
        val hex = "#2A78D6"
        assertEquals(hex, ColorMath.adaptForSurface(hex, dark = false))
    }

    @Test
    fun `near-black is lifted enough to be seen on a dark surface`() {
        val adapted = ColorMath.adaptForSurface("#050505", dark = true)

        assertTrue(lightness(adapted) >= 0.47)
    }

    @Test
    fun `near-white is brought down enough to be seen on a light surface`() {
        val adapted = ColorMath.adaptForSurface("#FEFEFE", dark = false)

        assertTrue(lightness(adapted) <= 0.78)
    }

    @Test
    fun `adapting keeps the hue that was chosen`() {
        // The point is that the colour stays recognisably itself; only its lightness moves.
        val chosen = "#001A66"
        val adapted = ColorMath.adaptForSurface(chosen, dark = true)

        assertEquals(hue(chosen), hue(adapted), 3.0)
    }

    @Test
    fun `both themes end up inside their own band`() {
        listOf("#000000", "#FFFFFF", "#2A78D6", "#7A0000").forEach { hex ->
            val light = lightness(ColorMath.adaptForSurface(hex, dark = false))
            val dark = lightness(ColorMath.adaptForSurface(hex, dark = true))
            assertTrue("$hex light=$light", light in 0.42..0.78)
            assertTrue("$hex dark=$dark", dark in 0.47..0.68)
        }
    }

    @Test
    fun `an unparseable value is passed through rather than throwing`() {
        assertEquals("not-a-colour", ColorMath.adaptForSurface("not-a-colour", dark = false))
    }

    // ---- HSV, for the sliders ----------------------------------------------------

    @Test
    fun `hsv round-trips through hex`() {
        listOf("#2A78D6", "#EB6834", "#008300").forEach { hex ->
            val (h, s, v) = requireNotNull(ColorMath.hexToHsv(hex))
            assertEquals(hex, ColorMath.hsvToHex(h, s, v))
        }
    }

    @Test
    fun `hsv covers the primaries`() {
        assertEquals("#FF0000", ColorMath.hsvToHex(0f, 1f, 1f))
        assertEquals("#00FF00", ColorMath.hsvToHex(120f, 1f, 1f))
        assertEquals("#0000FF", ColorMath.hsvToHex(240f, 1f, 1f))
        assertEquals("#FFFFFF", ColorMath.hsvToHex(0f, 0f, 1f))
        assertEquals("#000000", ColorMath.hsvToHex(0f, 0f, 0f))
    }

    @Test
    fun `hue wraps rather than clipping`() {
        assertEquals(ColorMath.hsvToHex(0f, 1f, 1f), ColorMath.hsvToHex(360f, 1f, 1f))
        assertEquals(ColorMath.hsvToHex(350f, 1f, 1f), ColorMath.hsvToHex(-10f, 1f, 1f))
    }

    @Test
    fun `out-of-range slider values are clamped, not wrapped`() {
        assertEquals("#FFFFFF", ColorMath.hsvToHex(0f, -1f, 2f))
        assertNotNull(ColorMath.hexToHsv(ColorMath.hsvToHex(200f, 2f, 2f)))
    }

    @Test
    fun `the eight built-in colours all already sit in the light band`() {
        // They were validated against that band, so adaptation should be a no-op for them.
        CategorySlots.all.forEach { slot ->
            assertEquals(slot.lightHex, ColorMath.adaptForSurface(slot.lightHex, dark = false))
        }
    }
}
