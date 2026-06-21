package com.tahsin.vocabregistry.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.vocabregistry.ui.UiSnapshot
import com.tahsin.vocabregistry.ui.theme.Ledger
import java.util.Locale

/* ============================================================
 *  AUDIO — offline Text-to-Speech (no key, no network)
 * ============================================================ */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    val engine = remember { mutableStateOf<TextToSpeech?>(null) }
    val ready = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val e = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) ready.value = true
        }
        engine.value = e
        onDispose { e.stop(); e.shutdown() }
    }
    return { text ->
        val e = engine.value
        if (e != null && ready.value) {
            e.language = Locale.UK
            e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab")
        }
    }
}

/** Small tap-to-hear control. Cream-on-dark and gold are used so it never depends
 *  on the card-text colour path. */
@Composable
fun SpeakerButton(text: String, speak: (String) -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFEDF1FB))
            .clickable { speak(text) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("\uD83D\uDD0A", fontSize = 16.sp)   // 🔊
    }
}

/* ============================================================
 *  EXAM COUNTDOWN HERO
 * ============================================================ */
@Composable
fun ExamHeroCard(ui: UiSnapshot) {
    val d = ui.daysToExam
    val line = when {
        d < 0L  -> "Exam date has passed — set a new one in Settings."
        d == 0L -> "Today is the day. Walk in and own it."
        d <= 7  -> "Final week. Sharpen, don't cram — you've built this."
        d <= 30 -> "The last stretch. Consistency now compounds fastest."
        d <= 90 -> "Momentum window. Every session moves the band."
        else    -> "Long game. Small daily reps become a large vocabulary."
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF182A50))
            .border(1.dp, Color(0xFFE8C547).copy(alpha = .40f), RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Column {
            Text(
                "COUNTDOWN TO IELTS", color = Ledger.Brass, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (d < 0L) "—" else "$d", color = Ledger.Cream, fontSize = 50.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "days", color = Ledger.GreenSoft, fontSize = 16.sp,
                    fontFamily = FontFamily.Serif, modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            Text(line, color = Ledger.GreenSoft, fontSize = 13.sp, fontFamily = FontFamily.Serif)
            Text(
                "Target  ${ui.examDate}", color = Color(0xFF8FA2C8), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/* ============================================================
 *  STREAK + DAILY-GOAL RING
 * ============================================================ */
@Composable
fun StreakGoalCard(ui: UiSnapshot) {
    val goal = 20
    val frac = if (goal == 0) 0f else (ui.reviewsToday.toFloat() / goal).coerceIn(0f, 1f)
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                RingProgress(frac, Modifier.size(62.dp))
                Text(
                    "${(frac * 100).toInt()}%", fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    color = Color(0xFF111726),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Today's goal", fontSize = 15.sp, fontFamily = FontFamily.Serif,
                    color = Color(0xFF111726),
                )
                Text(
                    "${ui.reviewsToday} / $goal reviews", fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\uD83D\uDD25 ${ui.streak}-day streak   ·   best ${ui.longest}   ·   ${ui.freezes} freeze",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                )
            }
        }
    }
}

@Composable
fun RingProgress(frac: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = size.minDimension * 0.13f
        val inset = sw / 2f
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(
            color = Color(0xFFDDE4F0), startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arc, style = Stroke(sw, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xFFE8C547), startAngle = -90f,
            sweepAngle = 360f * frac.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(inset, inset), size = arc, style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}

/* ============================================================
 *  RANK + BADGES
 * ============================================================ */
private fun rankFor(mastered: Int): Pair<String, String> = when {
    mastered >= 500 -> "Wordsmith" to "Master of the lexicon."
    mastered >= 250 -> "Lexicographer" to "Words are your instrument."
    mastered >= 120 -> "Adept" to "Fluency is taking real shape."
    mastered >= 50  -> "Scholar" to "A serious vocabulary is forming."
    mastered >= 15  -> "Journeyman" to "The foundation is set."
    else            -> "Apprentice" to "Every expert started exactly here."
}

private data class Badge(val icon: String, val name: String, val earned: Boolean)

private fun badgesFor(ui: UiSnapshot): List<Badge> = listOf(
    Badge("\uD83C\uDF31", "First Word", ui.mastered >= 1),
    Badge("\uD83D\uDCDA", "Twenty-Five", ui.mastered >= 25),
    Badge("\uD83D\uDCAF", "Centurion", ui.mastered >= 100),
    Badge("\uD83D\uDD25", "On Fire", ui.streak >= 3),
    Badge("\uD83D\uDCC5", "Week Strong", ui.longest >= 7),
    Badge("\uD83C\uDFC6", "Iron Month", ui.longest >= 30),
    Badge("\uD83D\uDCC8", "Band 6", ui.band >= 6.0),
    Badge("\uD83C\uDF93", "Band 7", ui.band >= 7.0),
    Badge("\u2705", "Inbox Zero", ui.calibrated && ui.dueCount == 0),
)

@Composable
fun RankBadgesCard(ui: UiSnapshot) {
    val (rank, blurb) = rankFor(ui.mastered)
    val badges = badgesFor(ui)
    PaperCard {
        Eyebrow("Standing")
        Text(
            rank, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif, color = Color(0xFF111726),
        )
        Text(blurb, fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Color(0xFF566077))
        Spacer(Modifier.height(10.dp))
        badges.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { b ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(b.icon, fontSize = 22.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.22f))
                        Text(
                            b.name, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = if (b.earned) Color(0xFF111726) else Color(0xFF9AA6BF),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ============================================================
 *  SESSION-COMPLETE QUOTE  (cream-on-dark — robust)
 * ============================================================ */
private val QUOTES = listOf(
    "The limits of my language mean the limits of my world." to "Ludwig Wittgenstein",
    "Words are the most powerful drug used by mankind." to "Rudyard Kipling",
    "Energy and persistence conquer all things." to "Benjamin Franklin",
    "It always seems impossible until it is done." to "Nelson Mandela",
    "Well begun is half done." to "Aristotle",
    "Patience is bitter, but its fruit is sweet." to "Aristotle",
    "Knowledge is power." to "Francis Bacon",
    "Reading maketh a full man." to "Francis Bacon",
    "Practice is the best of all instructors." to "Publilius Syrus",
    "Little by little, one travels far." to "Spanish proverb",
    "Learning never exhausts the mind." to "Leonardo da Vinci",
    "We are what we repeatedly do." to "Will Durant",
    "A little learning is a dangerous thing." to "Alexander Pope",
    "The beginning is the most important part of the work." to "Plato",
)

@Composable
fun SessionQuote() {
    val item = remember { QUOTES.random() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE8C547).copy(alpha = .35f), RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Column {
            Text(
                "\u201C${item.first}\u201D", color = Color(0xFFEDF1FB), fontSize = 16.sp,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, lineHeight = 23.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "\u2014 ${item.second}", color = Color(0xFFE8C547), fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
