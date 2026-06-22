package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.vocabregistry.data.model.*
import com.tahsin.vocabregistry.domain.*
import com.tahsin.vocabregistry.ui.AppViewModel
import com.tahsin.vocabregistry.ui.theme.Ledger
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/* ---------- CALIBRATION ---------- */
@Composable
fun CalibrationScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val words = remember(ui.words) {
        if (ui.words.isEmpty()) emptyList() else {
            fun pick(t: Int, n: Int) = ui.words
                .filter { it.tier == t && it.pos !in setOf("idiom", "phrasal verb", "collocation", "phrase") }
                .shuffled().take(n)
            pick(1, 10) + pick(2, 8) + pick(3, 4) + pick(4, 2)
        }
    }
    var idx by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<Pair<Int, Boolean>>() }
    if (words.isEmpty()) return
    val word = words.getOrNull(idx)
    val cloze = remember(idx) { word?.let { buildCloze(it, ui.words, ProficiencyTracker.DistractorMode.RANDOM_POS) } }

    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(20.dp).verticalScroll(rememberScrollState())) {
        Eyebrow("File opening · Calibration")
        Text("24 items. Choose the word that fits.", color = Ledger.Cream,
            fontSize = 22.sp, fontFamily = FontFamily.Serif)
        Text("Known words skip the queue — recognition seeded, production still earned.",
            color = Ledger.GreenSoft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        Text("Item ${idx + 1} / ${words.size}", color = Ledger.GreenSoft,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (word != null && cloze != null) {
            PaperCard {
                Chip(Ledger.tierName(word.tier), Ledger.tierColor(word.tier))
                Spacer(Modifier.height(8.dp))
                Text(cloze.first, fontSize = 17.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp, color = Color(0xFF111726))
                Spacer(Modifier.height(12.dp))
                fun answer(ok: Boolean) {
                    results += word.id to ok
                    if (idx + 1 >= words.size) vm.seedCalibration(results) else idx++
                }
                cloze.second.forEach { opt ->
                    OptionButton(opt) { answer(opt == word.word) }
                }
                TextButton(onClick = { answer(false) }, Modifier.fillMaxWidth()) {
                    Text("Not sure / don't know", color = Color(0xFF566077), fontFamily = FontFamily.Monospace)
                }
            }
        }
        TextButton(onClick = { vm.seedCalibration(results) }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip — start everything from zero", color = Ledger.GreenSoft,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

/* ---------- DASHBOARD ---------- */
@Composable
fun DashboardScreen(vm: AppViewModel, startSession: (SessionMode) -> Unit) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val speak = rememberSpeaker()
    var celebrateBand by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(ui.bandUp, ui.band) { if (ui.bandUp) celebrateBand = ui.band }
    celebrateBand?.let { b -> BandUpOverlay(b) { celebrateBand = null } }
    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Eyebrow("Daily docket")
                Text("Tahsin\u2019s Lexicon", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${ui.streak}d", color = Ledger.Brass, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("streak · ${ui.freezes} freeze", color = Ledger.GreenSoft,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(14.dp))
        ExamHeroCard(ui)
        Spacer(Modifier.height(10.dp))
        StreakGoalCard(ui)
        Spacer(Modifier.height(10.dp))
        RankBadgesCard(ui)
        Spacer(Modifier.height(10.dp))
        HeroReadinessRing(ui)
        Spacer(Modifier.height(10.dp))
        HeroXpCard(ui)
        Spacer(Modifier.height(10.dp))
        DailyQuestsCard(ui)
        Spacer(Modifier.height(10.dp))
        PaperCard {
            // THE ADAPTIVE DIAL — visible so the learner knows the system is following them
            Chip("Level: ${ui.proficiency.level.title} \u00B7 graded at band ${ui.proficiency.level.graderBand}" +
                if (ui.proficiency.demoted) " (tightened)" else "",
                if (ui.proficiency.demoted) Ledger.Stamp else Ledger.Green)
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Due for review", fontSize = 16.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                Text("${ui.dueCount}", fontSize = 22.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (ui.dueCount > 0) Ledger.Stamp else Ledger.Green)
            }
        }
        ui.wordOfDay?.let { w ->
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Eyebrow("Word of the day")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    SpeakerButton(w.word, speak)
                }
                Text("${w.pos} · ${Ledger.tierName(w.tier)}", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = Color(0xFF566077))
                Spacer(Modifier.height(4.dp))
                Text(w.definition, fontSize = 14.sp, color = Color(0xFF111726))
                Text("\u201C${w.example}\u201D", fontSize = 13.sp, fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Serif, color = Color(0xFF33507F),
                    modifier = Modifier.padding(top = 4.dp))
                w.confusable?.let {
                    Text("\u26A0 $it", color = Ledger.Stamp, fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple(SessionMode.DEEP, "Deep session", "30 exposures · new words + grading"),
            Triple(SessionMode.SPRINT, "Sprint", "12 due items only · ~5 minutes"),
            Triple(SessionMode.COMMUTE, "Commute", "20 recognition cards · no typing"),
        ).forEach { (mode, title, sub) ->
            Surface(onClick = { startSession(mode) }, color = if (mode == SessionMode.DEEP) Color(0xFFFFFFFF) else Color.Transparent,
                shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp,
                    if (mode == SessionMode.DEEP) Color(0xFFD7DEEC) else Ledger.GreenSoft.copy(alpha = .35f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, fontSize = 16.sp, fontFamily = FontFamily.Serif,
                        color = if (mode == SessionMode.DEEP) Color(0xFF111726) else Ledger.Cream)
                    Text(sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = if (mode == SessionMode.DEEP) Color(0xFF566077) else Ledger.GreenSoft)
                }
            }
        }
    }
}

/* ---------- PROGRESS ---------- */
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Eyebrow("Audit trail")
        Text("Progress", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(12.dp))

        var stats by remember { mutableStateOf(StatsData()) }
        LaunchedEffect(Unit) { stats = vm.loadStats() }

        // headline tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Reviews", stats.totalReviews.toString(), Modifier.weight(1f))
            StatTile("Days", stats.daysStudied.toString(), Modifier.weight(1f))
            StatTile("Accuracy", "${(stats.overallAccuracy * 100).toInt()}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Streak", "${ui.streak}d", Modifier.weight(1f))
            StatTile("Best", "${ui.longest}d", Modifier.weight(1f))
            StatTile("Mastered", ui.mastered.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // study heatmap
        PaperCard {
            Text("Study activity \u00B7 last 16 weeks", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                stats.heatmap.chunked(7).forEach { wk ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        wk.forEach { dc ->
                            val intensity = if (stats.busiestDay == 0) 0f
                                else (dc.count.toFloat() / stats.busiestDay).coerceIn(0f, 1f)
                            val c = if (dc.count == 0) Color(0xFFE6EBF3)
                                else lerp(Color(0xFFCDE5CB), Ledger.Green, intensity)
                            Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(c))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("less", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
                listOf(0f, 0.34f, 0.67f, 1f).forEach { t ->
                    val c = if (t == 0f) Color(0xFFE6EBF3) else lerp(Color(0xFFCDE5CB), Ledger.Green, t)
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c))
                }
                Text("more", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
            }
        }
        Spacer(Modifier.height(10.dp))

        // weekly reviews + accuracy
        PaperCard {
            Text("Weekly reviews", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            val maxRv = (stats.weeks.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.weeks.forEach { w ->
                    Box(Modifier.weight(1f).fillMaxHeight((w.reviews.toFloat() / maxRv).coerceIn(0.03f, 1f))
                        .background(Ledger.Stamp, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.weeks.forEach { w ->
                    Text(w.label, Modifier.weight(1f), fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        color = Color(0xFF566077), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Weekly accuracy", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Row(Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.weeks.forEach { w ->
                    val h = if (w.reviews == 0) 0.03f else w.accuracy.coerceIn(0.03f, 1f)
                    Box(Modifier.weight(1f).fillMaxHeight(h)
                        .background(Ledger.Green, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Readiness trend", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            val pts = ui.history.takeLast(30)
            Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pts.forEach { (_, b) ->
                    Box(Modifier.weight(1f).fillMaxHeight(((b - 4.0) / 5.0).toFloat().coerceIn(0.05f, 1f))
                        .background(Ledger.Stamp, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                }
                if (pts.isEmpty()) Text("No sessions yet.", fontSize = 12.sp, color = Color(0xFF566077))
            }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Proficiency engine", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            val p = ui.proficiency
            Text("Production EMA ${(p.emaP * 100).toInt()}% · Collocation ${(p.emaC * 100).toInt()}% · Recognition ${(p.emaR * 100).toInt()}%",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077),
                modifier = Modifier.padding(top = 6.dp))
            Text("→ grader band ${p.level.graderBand} · intervals ×${p.level.intervalModifier} · intake ${if (p.level.newCapDelta >= 0) "+" else ""}${p.level.newCapDelta}/day",
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Ledger.Green,
                modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Per-tier consolidation (R · P · C · G)", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            (1..8).forEach { t ->
                val tw = ui.words.filter { it.tier == t }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Ledger.tierName(t), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = Color(0xFF566077), modifier = Modifier.width(92.dp))
                    Axis.entries.forEach { k ->
                        val ok = tw.count { w ->
                            ui.axes[w.id]?.get(k)?.status in setOf(AxisStatus.REVIEW, AxisStatus.MASTERED)
                        }
                        val pct = if (tw.isEmpty()) 0f else ok.toFloat() / tw.size
                        Box(Modifier.weight(1f).height(7.dp).padding(horizontal = 2.dp)
                            .background(Color(0xFFD8E0F0), RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(pct).fillMaxHeight()
                                .background(Ledger.tierColor(t), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
    }
}

/* ---------- BROWSE ---------- */
@Composable
fun BrowseScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var q by remember { mutableStateOf("") }
    var tier by remember { mutableIntStateOf(0) }
    val list = remember(q, tier, ui.words) {
        ui.words.asSequence()
            .filter { tier == 0 || it.tier == tier }
            .filter { q.isBlank() || it.word.contains(q, true) || it.theme.contains(q, true) || it.definition.contains(q, true) }
            .take(100).toList()
    }
    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp)) {
        Eyebrow("The registry")
        Text("${ui.words.size} entries", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
        OutlinedTextField(value = q, onValueChange = { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Search word, theme, meaning…", color = Ledger.GreenSoft) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ledger.Cream, unfocusedTextColor = Ledger.Cream,
                focusedBorderColor = Ledger.Brass, unfocusedBorderColor = Ledger.GreenSoft,
                cursorColor = Ledger.Brass))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..8).forEach { t ->
                FilterChip(selected = tier == t, onClick = { tier = t },
                    label = { Text(if (t == 0) "All" else "T$t", fontFamily = FontFamily.Monospace, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(list, key = { it.id }) { w ->
                RegistryRow(w, ui.axes[w.id], ui.rich[w.id])
            }
        }
    }
}

/* ---------- WRITING ---------- */
private val IELTS_CRITERIA = listOf(
    "Task Response" to "Does it fully answer the question with a clear position and developed ideas?",
    "Coherence & Cohesion" to "Is it well organised, with logical paragraphs and smooth linking?",
    "Lexical Resource" to "Is the vocabulary wide, precise and natural?",
    "Grammatical Range & Accuracy" to "Are sentence structures varied and largely error-free?",
)

@Composable
fun WritingScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WritingJson>>(emptyList()) }
    LaunchedEffect(Unit) { items = vm.repo.writingItems() }

    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp)) {
            Eyebrow("Writing desk")
            Text("Loading the Task 2 library\u2026", color = Ledger.GreenSoft,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }

    var idx by remember(items) { mutableIntStateOf(items.indices.random()) }
    val item = items[idx]
    var text by remember(idx) { mutableStateOf("") }
    var showSamples by remember(idx) { mutableStateOf(false) }
    var showRubric by remember(idx) { mutableStateOf(false) }
    val scores = remember(idx) { mutableStateListOf(6.0, 6.0, 6.0, 6.0) }
    var saved by remember(idx) { mutableStateOf<Double?>(null) }
    val wc = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val estBand = Math.round((scores.sum() / scores.size) * 2.0) / 2.0
    var busy by remember { mutableStateOf(false) }
    var aiResult by remember(idx) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Eyebrow("Writing desk")
        Text("Task 2 practice", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
        Text("${items.size} prompts \u00B7 ${items.count { it.hasSamples }} with band 6 vs 8 models",
            color = Ledger.GreenSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(10.dp))

        PaperCard {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(item.type, Ledger.Brass)
                Chip(item.topic, Ledger.Green)
                if (item.hasSamples) Chip("models", Ledger.Stamp)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.prompt, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                lineHeight = 22.sp, color = Color(0xFF111726))
            TextButton(onClick = { idx = items.indices.random() }) {
                Text("different prompt", fontSize = 11.sp, color = Color(0xFF566077), fontFamily = FontFamily.Monospace)
            }
        }

        if (item.phrases.isNotEmpty() || item.targets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PaperCard {
                if (item.phrases.isNotEmpty()) {
                    Text("Useful phrases", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    Spacer(Modifier.height(4.dp))
                    item.phrases.forEach { ph ->
                        Text("\u2022 $ph", fontSize = 12.sp, color = Color(0xFF33507F),
                            fontFamily = FontFamily.Serif, lineHeight = 18.sp)
                    }
                }
                if (item.targets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Target vocabulary", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    Text(item.targets.joinToString("  \u00B7  "), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = Color(0xFF566077), lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Your response", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Write here or on paper, aiming for 250+ words. Then compare with the models and grade yourself.",
                fontSize = 11.sp, color = Color(0xFF566077), lineHeight = 16.sp)
            OutlinedTextField(value = text, onValueChange = { text = it },
                Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 6,
                placeholder = { Text("Deploy your target vocabulary\u2026", color = Color(0xFF566077)) },
                colors = inkFieldColors())
            Text("$wc words", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = if (wc >= 250) Ledger.Green else Color(0xFF566077), modifier = Modifier.padding(top = 4.dp))
        }

        if (item.hasSamples) {
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Row(Modifier.fillMaxWidth().clickable { showSamples = !showSamples },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Model answers \u2014 band 6 vs band 8", fontSize = 14.sp, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                    Text(if (showSamples) "\u25B2" else "\u25BC", fontSize = 12.sp, color = Color(0xFF566077))
                }
                if (showSamples) {
                    Spacer(Modifier.height(8.dp))
                    SampleBlock("BAND 6", item.band6, Ledger.Brass)
                    Spacer(Modifier.height(10.dp))
                    SampleBlock("BAND 8", item.band8, Ledger.Green)
                    if (item.gap.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("What lifts a 6 to an 8", fontSize = 13.sp, fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                        Spacer(Modifier.height(4.dp))
                        item.gap.forEach { g ->
                            Text("\u2192 $g", fontSize = 12.sp, color = Color(0xFF33507F), fontFamily = FontFamily.Serif,
                                lineHeight = 18.sp, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Text("Practice prompt", fontSize = 13.sp, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                Text("This prompt is for free practice. Use the phrase bank above, then grade your essay with the rubric below.",
                    fontSize = 12.sp, color = Color(0xFF566077), lineHeight = 17.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        PaperCard {
            Row(Modifier.fillMaxWidth().clickable { showRubric = !showRubric },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Grade yourself (no AI needed)", fontSize = 14.sp, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                Text(if (showRubric) "\u25B2" else "\u25BC", fontSize = 12.sp, color = Color(0xFF566077))
            }
            if (showRubric) {
                Spacer(Modifier.height(6.dp))
                IELTS_CRITERIA.forEachIndexed { i, pair ->
                    Spacer(Modifier.height(6.dp))
                    Text(pair.first, fontSize = 13.sp, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                    Text(pair.second, fontSize = 11.sp, color = Color(0xFF566077), lineHeight = 15.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        BandStepperButton("\u2212") { if (scores[i] > 4.0) scores[i] = scores[i] - 0.5 }
                        Text("%.1f".format(scores[i]), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = Ledger.Stamp)
                        BandStepperButton("+") { if (scores[i] < 9.0) scores[i] = scores[i] + 0.5 }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().background(Color(0xFFEDF1FA), RoundedCornerShape(10.dp)).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Estimated band", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
                            Text("%.1f".format(estBand), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                        }
                        Button(onClick = { vm.creditWriting(estBand); saved = estBand },
                            colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                            Text("Save")
                        }
                    }
                }
                saved?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Saved band ${"%.1f".format(it)} \u2014 this feeds your readiness ring and earns hero XP.",
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Ledger.Green, lineHeight = 17.sp)
                }
            }
        }

        if (ui.apiKeySet) {
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Text("Optional: AI examiner", fontSize = 13.sp, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                Text("You have an API key saved, so you can also get an automated second opinion.",
                    fontSize = 11.sp, color = Color(0xFF566077), lineHeight = 16.sp)
                Button(enabled = !busy && wc >= 80, onClick = {
                    busy = true; aiResult = null
                    scope.launch {
                        aiResult = vm.grader.gradeEssay(item.prompt, text, item.targets, ui.proficiency.level)
                            ?: "Examiner unreachable \u2014 check the API key in Settings."
                        busy = false
                    }
                }, Modifier.padding(top = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                    Text(if (busy) "Examining\u2026" else "Ask the AI examiner")
                }
                aiResult?.let { raw ->
                    Spacer(Modifier.height(8.dp))
                    val pretty = runCatching {
                        val o = Json.parseToJsonElement(raw).jsonObject
                        buildString {
                            appendLine("Band ${o["band"]?.jsonPrimitive?.content}")
                            o["topFix"]?.jsonPrimitive?.content?.let { append("\u270E $it") }
                        }
                    }.getOrElse { raw }
                    Text(pretty, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF111726))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SampleBlock(label: String, body: String, accent: Color) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F8FC), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 13.sp, fontFamily = FontFamily.Serif, lineHeight = 20.sp, color = Color(0xFF111726))
    }
}

@Composable
private fun BandStepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEDF1FA))
            .border(1.dp, Color(0xFFD7DEEC), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, color = Color(0xFF111726), fontWeight = FontWeight.Bold) }
}
/* ---------- SETTINGS ---------- */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var exam by remember(ui.examDate) { mutableStateOf(ui.examDate) }
    var key by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val data = vm.repo.exportJson()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                backupMsg = "Progress saved. Keep this file to restore on another phone."
            } catch (e: Exception) { backupMsg = "Export failed: " + (e.message ?: "unknown error") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) { vm.repo.importJson(text); vm.refresh(); backupMsg = "Progress restored." }
                else backupMsg = "Could not read that file."
            } catch (e: Exception) { backupMsg = "Import failed: " + (e.message ?: "unknown error") }
        }
    }
    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Eyebrow("Office")
        Text("Settings", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("IELTS exam date", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Drives the phase plan, intake, and tier weighting.", fontSize = 12.sp, color = Color(0xFF566077))
            OutlinedTextField(value = exam, onValueChange = { exam = it },
                placeholder = { Text("YYYY-MM-DD", color = Color(0xFF566077)) },
                colors = inkFieldColors(), modifier = Modifier.padding(top = 6.dp))
            Button(onClick = { vm.setExamDate(exam) }, Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) { Text("Save date") }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Anthropic API key", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text(if (ui.apiKeySet) "Key saved. LLM grading is live." else
                "Optional. Without a key the app uses self-grading: you compare your sentence " +
                "against the model example and rate yourself — free forever.",
                fontSize = 12.sp, color = if (ui.apiKeySet) Ledger.Green else Color(0xFF566077))
            OutlinedTextField(value = key, onValueChange = { key = it },
                placeholder = { Text("sk-ant-…", color = Color(0xFF566077)) },
                colors = inkFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Button(onClick = { vm.setApiKey(key); key = "" }, Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) { Text("Save key") }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Academic mode (Tier 4)", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    Text("C2 research vocabulary in sessions and the Writing examiner.",
                        fontSize = 12.sp, color = Color(0xFF566077))
                }
                Switch(checked = ui.academicMode, onCheckedChange = { vm.setAcademic(it) })
            }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Extra word sets", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Add these to your daily sessions. Off by default to keep IELTS focus; " +
                "all of them stay searchable in the Registry regardless.",
                fontSize = 12.sp, color = Color(0xFF566077), lineHeight = 17.sp)
            ToggleRow("BCS vocabulary (Tier 6)", ui.tier6) { vm.setTier6(it) }
            ToggleRow("Bank-job prep (Tier 7)", ui.tier7) { vm.setTier7(it) }
            ToggleRow("Academic / research (Tier 8)", ui.tier8) { vm.setTier8(it) }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Sound", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Soft chimes during sessions \u2014 choose what you hear.",
                fontSize = 12.sp, color = Color(0xFF566077), lineHeight = 17.sp)
            ToggleRow("Chime on every correct answer", ui.soundEveryCorrect) { vm.setSoundCorrect(it) }
            ToggleRow("Special chime on Precise answers", ui.soundPrecise) { vm.setSoundPrecise(it) }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily reminder", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    Text("A once-a-day nudge when cards are due, plus the word of the day.",
                        fontSize = 12.sp, color = Color(0xFF566077), lineHeight = 17.sp)
                }
                Switch(checked = ui.reminderOn, onCheckedChange = { vm.setReminder(it, ui.reminderHour) })
            }
            if (ui.reminderOn) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me at", fontSize = 13.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BandStepperButton("\u2212") { vm.setReminder(true, (ui.reminderHour + 23) % 24) }
                        Text(hourLabel(ui.reminderHour), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = Ledger.Stamp)
                        BandStepperButton("+") { vm.setReminder(true, (ui.reminderHour + 1) % 24) }
                    }
                }
                Text("Exact timing is approximate \u2014 Android batches reminders to save battery.",
                    fontSize = 11.sp, color = Color(0xFF566077), modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Backup & restore", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Save all your progress \u2014 every word's memory, streaks, and level \u2014 to a file. " +
                "Move it to a new phone and import to continue exactly where you left off.",
                fontSize = 12.5.sp, color = Color(0xFF566077), lineHeight = 18.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportLauncher.launch("tahsincabs_backup.json") },
                    colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) { Text("Export") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Ledger.Green)) { Text("Import") }
            }
            backupMsg?.let {
                Text(it, fontSize = 12.sp, color = Ledger.Green, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("Adaptive engine", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text("Level ${ui.proficiency.level.title}. The grader band, review intervals, daily intake, " +
                "and distractor difficulty retune automatically from your rolling performance — " +
                "stricter as you improve, gentler the moment you slump.",
                fontSize = 12.5.sp, color = Color(0xFF566077), lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF6F8FC))
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color(0xFF111726))
            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF566077))
        }
    }
}

private fun hourLabel(h: Int): String {
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12 $ampm"
}
