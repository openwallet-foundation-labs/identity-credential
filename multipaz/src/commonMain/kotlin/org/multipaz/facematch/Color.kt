package org.multipaz.facematch

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * RGBA color representation used by face matching and liveness sessions to control
 * UI background illumination.
 *
 * @property red red channel component in the range `[0.0f, 1.0f]`.
 * @property green green channel component in the range `[0.0f, 1.0f]`.
 * @property blue blue channel component in the range `[0.0f, 1.0f]`.
 * @property alpha alpha channel component in the range `[0.0f, 1.0f]`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("FaceMatcherColor")
data class Color(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1.0f
) {
    init {
        require(red in 0.0f..1.0f) { "red must be in 0.0..1.0, was $red" }
        require(green in 0.0f..1.0f) { "green must be in 0.0..1.0, was $green" }
        require(blue in 0.0f..1.0f) { "blue must be in 0.0..1.0, was $blue" }
        require(alpha in 0.0f..1.0f) { "alpha must be in 0.0..1.0, was $alpha" }
    }

    /**
     * Converts this color to a packed 32-bit ARGB integer (`0xAARRGGBB`).
     */
    fun toArgb(): Int {
        val a = (alpha * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val r = (red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val g = (green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val b = (blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Relative luminance in `[0.0f, 1.0f]` for determining contrasting foreground text color.
     */
    fun luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    companion object {
        /** Opaque black (`#FF000000`). */
        val BLACK = Color(0.0f, 0.0f, 0.0f, 1.0f)

        /** Opaque white (`#FFFFFFFF`). */
        val WHITE = Color(1.0f, 1.0f, 1.0f, 1.0f)

        /** Opaque red (`#FFFF0000`). */
        val RED = Color(1.0f, 0.0f, 0.0f, 1.0f)

        /** Opaque green (`#FF00FF00`). */
        val GREEN = Color(0.0f, 1.0f, 0.0f, 1.0f)

        /** Opaque blue (`#FF0000FF`). */
        val BLUE = Color(0.0f, 0.0f, 1.0f, 1.0f)

        /** Opaque yellow (`#FFFFFF00`). */
        val YELLOW = Color(1.0f, 1.0f, 0.0f, 1.0f)

        /** Opaque cyan (`#FF00FFFF`). */
        val CYAN = Color(0.0f, 1.0f, 1.0f, 1.0f)

        /** Opaque magenta (`#FFFF00FF`). */
        val MAGENTA = Color(1.0f, 0.0f, 1.0f, 1.0f)

        /**
         * Creates a [Color] from a packed 32-bit ARGB integer (`0xAARRGGBB`).
         */
        fun fromArgb(argb: Int): Color {
            val a = ((argb ushr 24) and 0xFF) / 255.0f
            val r = ((argb ushr 16) and 0xFF) / 255.0f
            val g = ((argb ushr 8) and 0xFF) / 255.0f
            val b = (argb and 0xFF) / 255.0f
            return Color(red = r, green = g, blue = b, alpha = a)
        }
    }
}
