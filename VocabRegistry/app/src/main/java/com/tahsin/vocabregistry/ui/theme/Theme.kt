package com.tahsin.vocabregistry.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class ThemeMode { DARK, LIGHT, HIGH_CONTRAST }

/** All theme colors live here; three instances are swapped at runtime. */
data class Palette(
    val bg: Color, val bgSoft: Color, val paper: Color, val paperEdge: Color,
    val ink: Color, val inkSoft: Color, val cream: Color, val stamp: Color,
    val brass: Color, val green: Color, val greenSoft: Color, val sky: Brush,
    val dark: Boolean,
)

// DARK — Van Gogh "Starry Night": cobalt sky, light cards, star-gold accents.
private val DarkPalette = Palette(
    bg = Color(0xFF0C1838), bgSoft = Color(0xFF16294F),
    paper = Color(0xFFE9EDF8), paperEdge = Color(0xFFC3CEE6),
    ink = Color(0xFF14213F), inkSoft = Color(0xFF4C5A7A), cream = Color(0xFFEDF1FB),
    stamp = Color(0xFFCB6A3C), brass = Color(0xFFE8C547), green = Color(0xFF3E6FB0),
    greenSoft = Color(0xFF93A8CF),
    sky = Brush.verticalGradient(listOf(Color(0xFF1E3461), Color(0xFF132450), Color(0xFF0A1430))),
    dark = true,
)

// LIGHT — soft daytime sky: light bg, white cards, dark text everywhere.
private val LightPalette = Palette(
    bg = Color(0xFFEEF2FA), bgSoft = Color(0xFFE2E9F5),
    paper = Color(0xFFFFFFFF), paperEdge = Color(0xFFD2DBEC),
    ink = Color(0xFF14213F), inkSoft = Color(0xFF55617E), cream = Color(0xFF1C2A4A),
    stamp = Color(0xFFB85A2E), brass = Color(0xFF9A7A1E), green = Color(0xFF2E5C9E),
    greenSoft = Color(0xFF5A6B8C),
    sky = Brush.verticalGradient(listOf(Color(0xFFF1F5FC), Color(0xFFE6EDF7), Color(0xFFDFE8F5))),
    dark = false,
)

// HIGH CONTRAST — white background, black text, strong borders (for direct sunlight).
private val HighContrastPalette = Palette(
    bg = Color(0xFFFFFFFF), bgSoft = Color(0xFFF2F2F2),
    paper = Color(0xFFFFFFFF), paperEdge = Color(0xFF000000),
    ink = Color(0xFF000000), inkSoft = Color(0xFF333333), cream = Color(0xFF000000),
    stamp = Color(0xFFB23000), brass = Color(0xFF6E5200), green = Color(0xFF003E8A),
    greenSoft = Color(0xFF1A1A1A),
    sky = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF))),
    dark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Call sites keep using Ledger.X; values resolve from the active palette. */
object Ledger {
    val Bg: Color        @Composable get() = LocalPalette.current.bg
    val BgSoft: Color    @Composable get() = LocalPalette.current.bgSoft
    val Paper: Color     @Composable get() = LocalPalette.current.paper
    val PaperEdge: Color @Composable get() = LocalPalette.current.paperEdge
    val Ink: Color       @Composable get() = LocalPalette.current.ink
    val InkSoft: Color   @Composable get() = LocalPalette.current.inkSoft
    val Cream: Color     @Composable get() = LocalPalette.current.cream
    val Stamp: Color     @Composable get() = LocalPalette.current.stamp
    val Brass: Color     @Composable get() = LocalPalette.current.brass
    val Green: Color     @Composable get() = LocalPalette.current.green
    val GreenSoft: Color @Composable get() = LocalPalette.current.greenSoft
    val nightSky: Brush  @Composable get() = LocalPalette.current.sky

    fun tierColor(t: Int) = when (t) {
        1 -> Color(0xFF5B86C4); 2 -> Color(0xFFD9A521); 3 -> Color(0xFFCB6A3C)
        4 -> Color(0xFF9B86D3); 5 -> Color(0xFF2E9E8F); 6 -> Color(0xFFB85C8A)
        7 -> Color(0xFF4F8C52); else -> Color(0xFF5A63C4)
    }
    fun tierName(t: Int) = when (t) {
        1 -> "T1 · B2 Core"; 2 -> "T2 · C1 IELTS"; 3 -> "T3 · GRE"; 4 -> "T4 · C2 Research"
        5 -> "T5 · IBA Exam"; 6 -> "T6 · BCS"; 7 -> "T7 · Bank"; else -> "T8 · Academic"
    }
}

@Composable
fun VocabTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = when (mode) {
        ThemeMode.DARK -> DarkPalette
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.HIGH_CONTRAST -> HighContrastPalette
    }
    val scheme = if (palette.dark)
        darkColorScheme(
            primary = palette.brass, secondary = palette.green, background = palette.bg,
            surface = palette.paper, onSurface = palette.ink, onBackground = palette.cream, error = palette.stamp,
        )
    else
        lightColorScheme(
            primary = palette.brass, secondary = palette.green, background = palette.bg,
            surface = palette.paper, onSurface = palette.ink, onBackground = palette.cream, error = palette.stamp,
        )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
