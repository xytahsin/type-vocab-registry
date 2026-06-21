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
import com.tahsin.vocabregistry.data.model.Word
import com.tahsin.vocabregistry.data.model.Axis
import com.tahsin.vocabregistry.data.model.AxisState
import com.tahsin.vocabregistry.data.model.AxisStatus
import com.tahsin.vocabregistry.data.model.RichExtras
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import com.tahsin.vocabregistry.R

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

private data class Badge(val icon: String, val name: String, val meaning: String, val earned: Boolean, val progress: Float)

private fun frac(cur: Number, target: Number): Float = (cur.toFloat() / target.toFloat()).coerceIn(0f, 1f)

private fun badgesFor(ui: UiSnapshot): List<Badge> {
    val inbox = ui.calibrated && ui.dueCount == 0
    return listOf(
        Badge("\uD83C\uDF31", "First Word", "Master your very first word", ui.mastered >= 1, frac(ui.mastered, 1)),
        Badge("\uD83D\uDCDA", "Twenty-Five", "Master twenty-five words", ui.mastered >= 25, frac(ui.mastered, 25)),
        Badge("\uD83D\uDCAF", "Centurion", "A hundred words conquered", ui.mastered >= 100, frac(ui.mastered, 100)),
        Badge("\uD83D\uDD25", "On Fire", "Study three days running", ui.streak >= 3, frac(ui.streak, 3)),
        Badge("\uD83D\uDCC5", "Week Strong", "Hold a seven-day streak", ui.longest >= 7, frac(ui.longest, 7)),
        Badge("\uD83C\uDFC6", "Iron Month", "Thirty days without missing", ui.longest >= 30, frac(ui.longest, 30)),
        Badge("\uD83D\uDCC8", "Band 6", "Readiness reaches 6.0", ui.band >= 6.0, frac(ui.band, 6.0)),
        Badge("\uD83C\uDF93", "Band 7", "Readiness reaches 7.0", ui.band >= 7.0, frac(ui.band, 7.0)),
        Badge("\u2705", "Inbox Zero", "Clear every due review", inbox, if (inbox) 1f else 0f),
    )
}

@Composable
fun RankBadgesCard(ui: UiSnapshot) {
    val (rank, blurb) = rankFor(ui.mastered)
    val badges = badgesFor(ui)
    val earned = badges.filter { it.earned }
    var expanded by remember { mutableStateOf(false) }
    PaperCard(Modifier.clickable { expanded = !expanded }.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Standing")
                Text(
                    rank, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = Color(0xFF111726),
                )
            }
            Text(if (expanded) "\u25B2" else "\u25BC", fontSize = 12.sp, color = Color(0xFF566077))
        }
        Spacer(Modifier.height(8.dp))
        if (!expanded) {
            if (earned.isEmpty()) {
                Text(
                    "No badges yet \u2014 your first is one word away.",
                    fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Color(0xFF566077),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    earned.forEach { Text(it.icon, fontSize = 26.sp) }
                }
                Text(
                    "${earned.size} earned  \u00B7  tap to see them all", fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Text(blurb, fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Color(0xFF566077))
            Spacer(Modifier.height(10.dp))
            badges.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { b -> BadgeCell(b, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                "tap to close", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = Color(0xFF9AA6BF), modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BadgeCell(b: Badge, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            RingProgress(b.progress, Modifier.size(50.dp))
            Text(b.icon, fontSize = 22.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.30f))
        }
        Text(
            b.name, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            color = if (b.earned) Color(0xFF111726) else Color(0xFF9AA6BF),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            b.meaning, fontSize = 9.sp, fontFamily = FontFamily.Serif, color = Color(0xFF8A93A8),
            textAlign = TextAlign.Center, lineHeight = 11.sp, modifier = Modifier.padding(top = 1.dp),
        )
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


/* ============================================================
 *  SOUND — soft procedural chimes via SoundPool
 * ============================================================ */
class Sounds(private val pool: SoundPool, private val ids: Map<String, Int>) {
    fun play(name: String) { ids[name]?.let { pool.play(it, 0.7f, 0.7f, 1, 0, 1f) } }
    fun release() { pool.release() }
}

@Composable
fun rememberSounds(): Sounds {
    val ctx = LocalContext.current
    val sounds = remember {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        Sounds(pool, mapOf(
            "correct" to pool.load(ctx, R.raw.sfx_correct, 1),
            "precise" to pool.load(ctx, R.raw.sfx_precise, 1),
            "levelup" to pool.load(ctx, R.raw.sfx_levelup, 1),
            "filed" to pool.load(ctx, R.raw.sfx_filed, 1),
        ))
    }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    return sounds
}

/* ============================================================
 *  STARRY-NIGHT LOADER
 * ============================================================ */
@Composable
fun StarryLoader() {
    val t = rememberInfiniteTransition(label = "load")
    val spin by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin",
    )
    val twinkle by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "tw",
    )
    Box(Modifier.fillMaxSize().background(Ledger.nightSky), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stars = listOf(
                0.15f to 0.20f, 0.80f to 0.15f, 0.30f to 0.50f, 0.70f to 0.55f, 0.20f to 0.80f,
                0.85f to 0.78f, 0.50f to 0.28f, 0.60f to 0.85f, 0.10f to 0.62f, 0.90f to 0.40f,
            )
            stars.forEachIndexed { i, (fx, fy) ->
                val a = (if (i % 2 == 0) twinkle else 1.35f - twinkle).coerceIn(0f, 1f)
                drawCircle(
                    Color(0xFFE8C547).copy(alpha = a), radius = 3f + (i % 3),
                    center = Offset(size.width * fx, size.height * fy),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(86.dp).rotate(spin)) {
                drawArc(
                    Color(0xFFE8C547), 0f, 250f, false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    style = Stroke(7f, cap = StrokeCap.Round),
                )
                drawArc(
                    Color(0xFF6E8AC0), 120f, 250f, false,
                    topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                    size = Size(size.width * 0.90f, size.height * 0.90f),
                    style = Stroke(5f, cap = StrokeCap.Round),
                )
                drawCircle(Color(0xFFEDF1FB), radius = 5f, center = Offset(size.width / 2, size.height / 2))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Tahsin\u2019s Lexicon", color = Ledger.Cream, fontSize = 20.sp,
                fontFamily = FontFamily.Serif, modifier = Modifier.alpha(twinkle),
            )
            Text(
                "opening the ledger\u2026", color = Ledger.GreenSoft, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}


/* ============================================================
 *  REGISTRY — expandable word entry + learning-progress dots
 * ============================================================ */
@Composable
fun ProgressDots(st: Map<Axis, AxisState>?) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Axis.entries.forEach { axis ->
            val status = st?.get(axis)?.status ?: AxisStatus.NEW
            val c = when (status) {
                AxisStatus.NEW -> Color(0xFFC2CBDD)
                AxisStatus.LEARNING -> Color(0xFFE8A23C)
                AxisStatus.REVIEW -> Color(0xFF3E6FB0)
                AxisStatus.MASTERED -> Color(0xFF3E9B6F)
                AxisStatus.LAPSED -> Color(0xFFCB6A3C)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Text(axis.name, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8A93A8), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(s: String) {
    Text(
        s.uppercase(), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
        color = Color(0xFFB07D2E), modifier = Modifier.padding(top = 10.dp, bottom = 1.dp),
    )
}

@Composable
fun RegistryRow(w: Word, st: Map<Axis, AxisState>?, rich: RichExtras?) {
    var open by remember { mutableStateOf(false) }
    PaperCard(Modifier.padding(vertical = 3.dp).clickable { open = !open }.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.word, fontSize = 16.sp, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                Text("${w.pos}  \u00B7  ${w.theme}", fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
            }
            ProgressDots(st)
        }
        if (!open) {
            Text(
                w.definition, fontSize = 12.sp, color = Color(0xFF566077),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(w.definition, fontSize = 14.sp, color = Color(0xFF111726))
            val examples = rich?.examples?.takeIf { it.isNotEmpty() } ?: listOf(w.example)
            SectionLabel("Examples")
            examples.forEach { ex ->
                Text(
                    "\u201C$ex\u201D", fontSize = 13.sp, fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif, color = Color(0xFF33507F),
                    lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (w.collocationList.isNotEmpty()) {
                SectionLabel("Collocations")
                Text(w.collocationList.joinToString("  \u00B7  "), fontSize = 13.sp, color = Color(0xFF111726))
            }
            if (w.synonymList.isNotEmpty()) {
                SectionLabel("Synonyms")
                Text(w.synonymList.joinToString(", "), fontSize = 13.sp, color = Color(0xFF2E7D52))
            }
            rich?.antonyms?.takeIf { it.isNotEmpty() }?.let {
                SectionLabel("Antonyms")
                Text(it.joinToString(", "), fontSize = 13.sp, color = Color(0xFFB5572E))
            }
            rich?.idioms?.takeIf { it.isNotEmpty() }?.let {
                SectionLabel("Idioms & phrases")
                it.forEach { ph ->
                    Text("\u2022 $ph", fontSize = 13.sp, color = Color(0xFF111726),
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            w.confusable?.let {
                Spacer(Modifier.height(8.dp))
                Text("\u26A0 $it", color = Ledger.Stamp, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                "tap to close", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = Color(0xFF9AA6BF), modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
