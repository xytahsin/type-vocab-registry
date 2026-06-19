package com.tahsin.vocabregistry.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Ledger {
    val Bg = Color(0xFF13241F)
    val BgSoft = Color(0xFF1B312A)
    val Paper = Color(0xFFF1E8D2)
    val PaperEdge = Color(0xFFD8CBA8)
    val Ink = Color(0xFF23291F)
    val InkSoft = Color(0xFF5A5644)
    val Cream = Color(0xFFEFE9DA)
    val Stamp = Color(0xFFC03B2B)
    val Brass = Color(0xFFB98A2F)
    val Green = Color(0xFF2E6B4F)
    val GreenSoft = Color(0xFF9FBCA9)
    fun tierColor(t: Int) = when (t) {
        1 -> Color(0xFF5B8266); 2 -> Color(0xFFB98A2F); 3 -> Color(0xFFA4574A); else -> Color(0xFF54688B)
    }
    fun tierName(t: Int) = when (t) {
        1 -> "T1 · B2 Core"; 2 -> "T2 · C1 IELTS"; 3 -> "T3 · GRE"; else -> "T4 · C2 Research"
    }
}

@Composable
fun VocabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ledger.Brass, secondary = Ledger.Green, background = Ledger.Bg,
            surface = Ledger.Paper, onSurface = Ledger.Ink, onBackground = Ledger.Cream,
            error = Ledger.Stamp,
        ),
        content = content,
    )
}
