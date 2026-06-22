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
import com.tahsin.vocabregistry.domain.Heroics
import com.tahsin.vocabregistry.domain.HeroXp
import com.tahsin.vocabregistry.domain.HeroRank
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
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
    fun tierFrac(t: Int): Float {
        val tot = ui.tierTotals[t] ?: 0
        val mas = ui.tierMastered[t] ?: 0
        return if (tot == 0) 0f else (mas.toFloat() / tot).coerceIn(0f, 1f)
    }
    fun tierDone(t: Int): Boolean {
        val tot = ui.tierTotals[t] ?: 0
        return tot > 0 && (ui.tierMastered[t] ?: 0) >= tot
    }
    return listOf(
        // —— vocabulary mastery ——
        Badge("\uD83C\uDF31", "First Word", "Master your very first word", ui.mastered >= 1, frac(ui.mastered, 1)),
        Badge("\uD83D\uDCDA", "Twenty-Five", "Master twenty-five words", ui.mastered >= 25, frac(ui.mastered, 25)),
        Badge("\uD83D\uDCAF", "Centurion", "A hundred words conquered", ui.mastered >= 100, frac(ui.mastered, 100)),
        Badge("\uD83D\uDDFF", "Word-Pillar", "Two hundred and fifty mastered", ui.mastered >= 250, frac(ui.mastered, 250)),
        Badge("\uD83C\uDFDB\uFE0F", "Lexicon Hall", "Five hundred words mastered", ui.mastered >= 500, frac(ui.mastered, 500)),
        Badge("\uD83D\uDC51", "Thousand Words", "A thousand words mastered", ui.mastered >= 1000, frac(ui.mastered, 1000)),
        // —— streaks / consistency ——
        Badge("\uD83D\uDD25", "On Fire", "Study three days running", ui.longest >= 3, frac(ui.longest, 3)),
        Badge("\uD83D\uDCC5", "Week Strong", "Hold a seven-day streak", ui.longest >= 7, frac(ui.longest, 7)),
        Badge("\uD83D\uDDD3\uFE0F", "Fortnight", "Fourteen days unbroken", ui.longest >= 14, frac(ui.longest, 14)),
        Badge("\uD83C\uDFC6", "Iron Month", "Thirty days without missing", ui.longest >= 30, frac(ui.longest, 30)),
        Badge("\uD83D\uDC8E", "Hundred Days", "A hundred-day streak", ui.longest >= 100, frac(ui.longest, 100)),
        // —— readiness bands ——
        Badge("\uD83D\uDCC8", "Band 6", "Readiness reaches 6.0", ui.band >= 6.0, frac(ui.band, 6.0)),
        Badge("\uD83D\uDCC9", "Band 6.5", "Readiness reaches 6.5", ui.band >= 6.5, frac(ui.band, 6.5)),
        Badge("\uD83C\uDF93", "Band 7", "Readiness reaches 7.0", ui.band >= 7.0, frac(ui.band, 7.0)),
        Badge("\u2B50", "Band 7.5", "Readiness reaches 7.5", ui.band >= 7.5, frac(ui.band, 7.5)),
        Badge("\uD83C\uDF1F", "Band 8", "Readiness reaches 8.0", ui.band >= 8.0, frac(ui.band, 8.0)),
        // —— writing ——
        Badge("\u270D\uFE0F", "First Essay", "Self-rate one piece of writing", ui.essays >= 1, frac(ui.essays, 1)),
        Badge("\uD83D\uDD8B\uFE0F", "Diligent Pen", "Self-rate ten essays", ui.essays >= 10, frac(ui.essays, 10)),
        Badge("\uD83C\uDFC5", "Band-8 Pen", "Rate an essay at band 8 or above", ui.bestWriting >= 8.0, frac(ui.bestWriting, 8.0)),
        // —— tier trophies ——
        Badge("\uD83E\uDD49", "Tier I Cleared", "Master every Tier-1 word", tierDone(1), tierFrac(1)),
        Badge("\uD83E\uDD48", "Tier II Cleared", "Master every Tier-2 word", tierDone(2), tierFrac(2)),
        Badge("\uD83E\uDD47", "Tier III Cleared", "Master every Tier-3 word", tierDone(3), tierFrac(3)),
        // —— hero levels (XP) ——
        Badge("\u2694\uFE0F", "Lexical Knight", "Reach 1,600 hero XP", ui.xp >= HeroRank.KNIGHT.minXp, frac(ui.xp.toDouble(), HeroRank.KNIGHT.minXp.toDouble())),
        Badge("\uD83C\uDF20", "Luminary", "Reach 10,000 hero XP", ui.xp >= HeroRank.LUMINARY.minXp, frac(ui.xp.toDouble(), HeroRank.LUMINARY.minXp.toDouble())),
        // —— quests ——
        Badge("\uD83D\uDDFA\uFE0F", "Quester", "Complete all daily quests once", ui.questsDoneCount >= 1, frac(ui.questsDoneCount, 1)),
        Badge("\uD83E\uDDED", "Quest Streak", "Full quests on seven days", ui.questsDoneCount >= 7, frac(ui.questsDoneCount, 7)),
        // —— comeback ——
        Badge("\uD83D\uDD4A\uFE0F", "Phoenix", "Recover after a slump", ui.comeback, if (ui.comeback) 1f else 0f),
        // —— housekeeping ——
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

/* ============================================================
 *  HERO READINESS RING  — precise band, fills toward the next
 *  half-band, escalating colour + glow as the learner ascends.
 * ============================================================ */
@Composable
fun HeroReadinessRing(ui: UiSnapshot) {
    val hero = Heroics.hero(ui.band)
    val next = Heroics.nextNumber(ui.band)
    val animFrac by animateFloatAsState(
        ui.ringFrac.coerceIn(0f, 1f), tween(750, easing = LinearEasing), label = "ring",
    )
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    val sw = size.minDimension * 0.11f
                    val inset = sw * 1.7f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    val sweep = 360f * animFrac
                    if (hero.glow > 0f) {
                        drawArc(
                            hero.ring.copy(alpha = 0.16f * hero.glow + 0.05f), -90f, sweep, false,
                            topLeft = topLeft, size = arcSize, style = Stroke(sw * 2.4f, cap = StrokeCap.Round),
                        )
                    }
                    drawArc(
                        Color(0xFFDDE4F0), -90f, 360f, false,
                        topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    drawArc(
                        hero.ring, -90f, sweep, false,
                        topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f".format(ui.band), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, color = hero.core,
                    )
                    Text(
                        hero.title.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Readiness")
                Text(
                    hero.blurb, fontSize = 13.sp, fontFamily = FontFamily.Serif,
                    color = Color(0xFF111726), lineHeight = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(animFrac * 100).toInt()}% to band ${"%.1f".format(next)}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                )
                Text(
                    "${ui.mastered} mastered \u00B7 ${ui.daysToExam} days to exam",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                )
            }
        }
    }
}

/* ============================================================
 *  HERO XP / LEVEL  — always-forward progression
 * ============================================================ */
@Composable
fun HeroXpCard(ui: UiSnapshot) {
    val prog = HeroXp.progress(ui.xp)
    val animFill by animateFloatAsState(prog.frac.coerceIn(0f, 1f), tween(850), label = "xpfill")
    val animXp by animateIntAsState(ui.xp.toInt(), tween(850), label = "xpval")
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u2728", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Hero level")
                Text(
                    prog.rank.title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, color = prog.rank.color,
                )
            }
            Text("$animXp XP", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFDDE4F0)),
        ) {
            if (animFill > 0f) Box(Modifier.weight(animFill).fillMaxHeight().background(prog.rank.color))
            if (animFill < 1f) Box(Modifier.weight(1f - animFill))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (prog.rankSpan > 0L) "${prog.intoRank} / ${prog.rankSpan} XP to ${prog.nextTitle}"
            else "Maximum rank reached \u2014 legendary.",
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
        )
    }
}

/* ============================================================
 *  DAILY QUESTS
 * ============================================================ */
@Composable
fun DailyQuestsCard(ui: UiSnapshot) {
    val quests = listOf(
        Triple("Review 15 due items", ui.reviewsToday, 15),
        Triple("Learn 3 new words", ui.newToday, 3),
        Triple("Self-rate 1 piece of writing", ui.writingToday, 1),
    )
    val doneCount = quests.count { it.second >= it.third }
    PaperCard {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("Daily quests")
            Text(
                "$doneCount / ${quests.size}", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = if (doneCount == quests.size) Ledger.Green else Color(0xFF566077),
            )
        }
        Spacer(Modifier.height(8.dp))
        quests.forEach { (label, cur, goal) ->
            val done = cur >= goal
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "\u2611\uFE0F" else "\u2B1C", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    label, fontSize = 13.sp, color = Color(0xFF111726),
                    modifier = Modifier.weight(1f), fontFamily = FontFamily.Serif,
                )
                Text(
                    "${cur.coerceAtMost(goal)}/$goal", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = if (done) Ledger.Green else Color(0xFF566077),
                )
            }
        }
        if (doneCount == quests.size) {
            Spacer(Modifier.height(6.dp))
            Text(
                "All quests cleared \u2014 +${HeroXp.QUEST_ALL_BONUS} XP today.", fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, color = Ledger.Green,
            )
        }
    }
}

/* ============================================================
 *  BAND-UP CELEBRATION
 * ============================================================ */
@Composable
fun BandUpOverlay(band: Double, onDismiss: () -> Unit) {
    val hero = Heroics.hero(band)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF182A50))
                .border(1.dp, hero.ring.copy(alpha = .6f), RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            ConfettiBurst(Modifier.matchParentSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "BAND UP", color = Ledger.Brass, fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp, fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "%.1f".format(band), color = hero.core, fontSize = 60.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                )
                Text(hero.title, color = Ledger.Cream, fontSize = 20.sp, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(8.dp))
                Text(
                    hero.blurb, color = Color(0xFF9FB2D8), fontSize = 13.sp,
                    fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(hero.ring)
                        .clickable { onDismiss() }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text("Onward", color = Color(0xFF111726), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/* ============================================================
 *  CONFETTI BURST — one-shot particle spray for celebrations
 * ============================================================ */
@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val palette = listOf(
        Color(0xFFE8C547), Color(0xFF6FA86A), Color(0xFF6FA8FF),
        Color(0xFFE0556E), Color(0xFFF0F4FF),
    )
    val seeds = remember {
        val r = java.util.Random(11)
        List(42) {
            Triple(
                (r.nextDouble() * 2.0 * Math.PI).toFloat(),   // angle
                0.45f + r.nextFloat() * 0.85f,                // speed factor
                palette[r.nextInt(palette.size)],            // colour
            )
        }
    }
    val radii = remember { val r = java.util.Random(5); List(42) { 3f + r.nextFloat() * 4f } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1500, easing = LinearEasing)) }
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height * 0.40f
        val reach = size.minDimension * 0.95f
        val alpha = (1f - p).coerceIn(0f, 1f)
        seeds.forEachIndexed { i, (ang, spd, col) ->
            val dx = kotlin.math.cos(ang) * spd * reach * p
            val dy = kotlin.math.sin(ang) * spd * reach * p + reach * 0.55f * p * p
            drawCircle(
                col.copy(alpha = alpha),
                radius = radii[i] * (1f - 0.3f * p),
                center = Offset(cx + dx, cy + dy),
            )
        }
    }
}
