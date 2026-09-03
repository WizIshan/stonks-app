package dev.wizishan.stonks.core

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Just enough colour science to keep a freely chosen colour legible.
 *
 * Works in OKLCH rather than HSL. HSL's "lightness" is not perceptual — pure yellow and
 * pure blue both sit at L=50% while looking nothing alike in brightness — so clamping HSL
 * lightness would leave yellow glaring and blue invisible. OKLCH's L tracks what the eye
 * actually reports, which is what makes one band work across every hue.
 *
 * Plain Kotlin with no Android dependency, so all of it is unit-testable.
 */
object ColorMath {

    /**
     * The lightness band a mark has to sit in to read against each surface.
     *
     * These are the same bands the palette validator holds the eight built-in slots to, so
     * a custom colour is judged by the same standard rather than a looser one.
     */
    private const val LIGHT_SURFACE_MIN = 0.43
    private const val LIGHT_SURFACE_MAX = 0.77
    private const val DARK_SURFACE_MIN = 0.48
    private const val DARK_SURFACE_MAX = 0.67

    data class Oklch(val lightness: Double, val chroma: Double, val hueDegrees: Double)

    /** Null for anything that is not a `#RRGGBB` string. */
    fun parseHex(hex: String?): Triple<Int, Int, Int>? {
        val cleaned = hex?.trim()?.removePrefix("#") ?: return null
        if (cleaned.length != 6 || cleaned.any { it.digitToIntOrNull(16) == null }) return null
        return Triple(
            cleaned.substring(0, 2).toInt(16),
            cleaned.substring(2, 4).toInt(16),
            cleaned.substring(4, 6).toInt(16),
        )
    }

    fun isValidHex(hex: String?): Boolean = parseHex(hex) != null

    fun toHex(r: Int, g: Int, b: Int): String =
        "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    // ---- conversions -------------------------------------------------------------

    fun hexToOklch(hex: String): Oklch? {
        val (r, g, b) = parseHex(hex) ?: return null
        val lr = toLinear(r / 255.0)
        val lg = toLinear(g / 255.0)
        val lb = toLinear(b / 255.0)

        val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)

        val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s

        val chroma = kotlin.math.hypot(okA, okB)
        val hue = (Math.toDegrees(kotlin.math.atan2(okB, okA)) + 360.0) % 360.0
        return Oklch(okL, chroma, hue)
    }

    fun oklchToHex(color: Oklch): String {
        val hueRadians = Math.toRadians(color.hueDegrees)
        val okA = color.chroma * kotlin.math.cos(hueRadians)
        val okB = color.chroma * kotlin.math.sin(hueRadians)

        val l = (color.lightness + 0.3963377774 * okA + 0.2158037573 * okB).pow(3)
        val m = (color.lightness - 0.1055613458 * okA - 0.0638541728 * okB).pow(3)
        val s = (color.lightness - 0.0894841775 * okA - 1.2914855480 * okB).pow(3)

        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

        return toHex(toByte(r), toByte(g), toByte(b))
    }

    /**
     * Nudge a colour's lightness into the readable band for the surface it will sit on.
     *
     * Hue and chroma are left exactly as chosen — the colour stays recognisably the one
     * that was picked. Only its lightness moves, and only when it is outside the band, so
     * a colour already in range comes back untouched.
     */
    fun adaptForSurface(hex: String, dark: Boolean): String {
        val color = hexToOklch(hex) ?: return hex
        val min = if (dark) DARK_SURFACE_MIN else LIGHT_SURFACE_MIN
        val max = if (dark) DARK_SURFACE_MAX else LIGHT_SURFACE_MAX

        if (color.lightness in min..max) return hex
        return oklchToHex(color.copy(lightness = color.lightness.coerceIn(min, max)))
    }

    // ---- HSV, for the picker's sliders --------------------------------------------

    /**
     * The picker's sliders are HSV because that is what people expect to drag — a hue
     * ring, then how strong and how bright. The result is converted to a hex and only
     * *then* adapted per surface, so the slider positions stay stable while the rendered
     * colour is the safe one.
     */
    fun hsvToHex(hueDegrees: Float, saturation: Float, value: Float): String {
        val h = ((hueDegrees % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c

        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return toHex(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** Returns hue in degrees, saturation and value in 0..1. */
    fun hexToHsv(hex: String): Triple<Float, Float, Float>? {
        val (ri, gi, bi) = parseHex(hex) ?: return null
        val r = ri / 255f
        val g = gi / 255f
        val b = bi / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (max == 0f) 0f else delta / max
        return Triple((hue + 360f) % 360f, saturation, max)
    }

    private fun toLinear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun toSrgb(channel: Double): Double {
        val c = channel.coerceIn(0.0, 1.0)
        return if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
    }

    private fun toByte(linear: Double): Int = (toSrgb(linear) * 255).roundToInt().coerceIn(0, 255)
}
