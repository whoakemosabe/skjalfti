package app.skjalfti.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.skjalfti.R
import app.skjalfti.Skin

object Fonts {
    /** Pixel UI text. */
    val departure = FontFamily(Font(R.font.departure_mono, FontWeight.Normal))
    /** Dot-matrix readouts. */
    val doto = FontFamily(Font(R.font.doto_bold, FontWeight.Bold), Font(R.font.doto_black, FontWeight.Black))
    /** The wordmark. */
    val martian = FontFamily(Font(R.font.martian_mono_regular, FontWeight.Normal), Font(R.font.martian_mono_extrabold, FontWeight.ExtraBold))
    /** Glass UI text. */
    val geist = FontFamily(
        Font(R.font.geist_light, FontWeight.Light),
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
    )
    val geistMono = FontFamily(Font(R.font.geist_mono_regular, FontWeight.Normal), Font(R.font.geist_mono_medium, FontWeight.Medium))
}

/** Every colour a screen uses, so both skins draw the same layout in their own look. */
@Immutable
data class Palette(
    val bg: Color,
    val panel: Color,
    val bevelHi: Color,
    val bevelLo: Color,
    val screen: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val off: Color,
    /** The trace and "good" things. */
    val phosphor: Color,
    /** Readouts and the selected tab. */
    val amber: Color,
    /** Alerts and REC. */
    val hot: Color,
    /** Night report accent. */
    val magenta: Color,
    /** Text drawn on top of an accent fill. */
    val onAccent: Color,
)

val PixelPalette = Palette(
    bg = Color(0xFF0B0B14),
    panel = Color(0xFF1C1C33),
    bevelHi = Color(0xFF4A4A78),
    bevelLo = Color(0xFF05050A),
    screen = Color(0xFF07140C),
    ink = Color(0xFFFFFFFF),
    muted = Color(0xFFA9A9D0),
    faint = Color(0xFF6C6C99),
    off = Color(0xFF2A2A44),
    phosphor = Color(0xFF39FF7A),
    amber = Color(0xFFFFB000),
    hot = Color(0xFFFF4F6D),
    magenta = Color(0xFFFF4FD8),
    onAccent = Color(0xFF05050A),
)

val GlassPalette = Palette(
    bg = Color(0xFF06080B),
    panel = Color(0x14FFFFFF),
    bevelHi = Color(0x38FFFFFF),
    bevelLo = Color(0x14FFFFFF),
    screen = Color(0x00000000),
    ink = Color(0xFFF2F4F7),
    muted = Color(0xFFA7ADB8),
    faint = Color(0xFF8A909C),
    off = Color(0x1FFFFFFF),
    phosphor = Color(0xFFFF6A2B),
    amber = Color(0xFFFFB38A),
    hot = Color(0xFFFF3D71),
    magenta = Color(0xFF9B7BFF),
    onAccent = Color(0xFF0B0D10),
)

/** Type roles. Each skin maps them to its own fonts. */
@Immutable
data class Type(
    val label: TextStyle,
    val body: TextStyle,
    val title: TextStyle,
    val mark: TextStyle,
    val number: TextStyle,
    val small: TextStyle,
)

private fun ts(f: FontFamily, size: TextUnit, w: FontWeight = FontWeight.Normal, ls: TextUnit = 0.sp) =
    TextStyle(fontFamily = f, fontSize = size, fontWeight = w, letterSpacing = ls)

val PixelType = Type(
    label = ts(Fonts.departure, 11.sp, ls = 0.5.sp),
    body = ts(Fonts.departure, 14.sp),
    title = ts(Fonts.departure, 16.sp),
    mark = ts(Fonts.martian, 22.sp, FontWeight.ExtraBold, 1.sp),
    number = ts(Fonts.doto, 34.sp, FontWeight.Black),
    small = ts(Fonts.departure, 12.sp),
)

val GlassType = Type(
    label = ts(Fonts.geist, 12.sp),
    body = ts(Fonts.geist, 15.sp),
    title = ts(Fonts.geist, 17.sp, FontWeight.Medium),
    mark = ts(Fonts.geist, 28.sp, FontWeight.Medium, (-0.5).sp),
    number = ts(Fonts.geistMono, 22.sp),
    small = ts(Fonts.geist, 13.sp),
)

/** What the current skin is, with its palette and type. */
@Immutable
data class Look(val skin: Skin, val c: Palette, val t: Type) {
    val pixel: Boolean get() = skin == Skin.PIXEL

    /** Pixel speaks in capitals; glass in sentence case. */
    fun say(s: String): String = if (pixel) s.uppercase() else s
}

fun lookFor(skin: Skin) = when (skin) {
    Skin.PIXEL -> Look(skin, PixelPalette, PixelType)
    Skin.GLASS -> Look(skin, GlassPalette, GlassType)
}

val LocalLook = staticCompositionLocalOf { lookFor(Skin.PIXEL) }
