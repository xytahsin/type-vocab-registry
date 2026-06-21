package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
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
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Readiness ${ui.band}", color = Ledger.Stamp, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text("IELTS in ${ui.daysToExam} days · ${ui.mastered} mastered · ${ui.axes.size} in circulation",
                        fontSize = 12.sp, color = Color(0xFF566077))
                    Spacer(Modifier.height(6.dp))
                    // THE ADAPTIVE DIAL — visible so the learner knows the system is following them
                    Chip("Level: ${ui.proficiency.level.title} · graded at band ${ui.proficiency.level.graderBand}" +
                        if (ui.proficiency.demoted) " (tightened)" else "",
                        if (ui.proficiency.demoted) Ledger.Stamp else Ledger.Green)
                }
            }
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
                PaperCard(Modifier.padding(vertical = 3.dp)) {
                    Text(w.word, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                    Text("${w.pos} · ${w.theme} — ${w.definition}", fontSize = 12.sp, color = Color(0xFF566077))
                    val st = ui.axes[w.id]
                    if (st != null) {
                        Text(Axis.entries.joinToString(" · ") { k ->
                            "${k.name}:${st[k]?.status?.name?.take(3) ?: "—"}"
                        }, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Ledger.Green)
                    }
                }
            }
        }
    }
}

/* ---------- WRITING ---------- */
private val T2_PROMPTS = listOf(
    "Some people believe governments should prioritise spending on public transport over roads. To what extent do you agree or disagree?",
    "In many countries the gap between rich and poor is widening. What problems does this cause, and what measures could address them?",
    "Some argue technology makes public services more accessible; others say it excludes vulnerable groups. Discuss both views and give your opinion.",
    "Environmental problems are too big for individual countries to solve. International cooperation is the only answer. Do you agree or disagree?",
)
@Composable
fun WritingScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var prompt by remember { mutableStateOf(T2_PROMPTS.random()) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val wc = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Eyebrow("Writing desk")
        Text("Task 2 practice", color = Ledger.Cream, fontSize = 23.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text(prompt, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color(0xFF111726))
            TextButton(onClick = { prompt = T2_PROMPTS.random(); result = null }) {
                Text("different prompt", fontSize = 11.sp, color = Color(0xFF566077), fontFamily = FontFamily.Monospace)
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, Modifier.fillMaxWidth(),
                minLines = 8, placeholder = { Text("150–200 words. Deploy your targets — the examiner is watching.", color = Color(0xFF566077)) },
                colors = inkFieldColors())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$wc words", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = if (wc >= 150) Ledger.Green else Color(0xFF566077))
                Button(enabled = !busy && wc >= 80, onClick = {
                    busy = true; result = null
                    scope.launch {
                        val targets = ui.axes.keys.mapNotNull { id -> ui.words.find { it.id == id }?.word }.take(250) +
                            if (ui.academicMode) ui.words.filter { it.tier == 4 }.map { it.word } else emptyList()
                        result = vm.grader.gradeEssay(prompt, text, targets, ui.proficiency.level)
                            ?: "Examiner unreachable — check the API key in Settings."
                        busy = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                    Text(if (busy) "Examining…" else "Submit to examiner")
                }
            }
            result?.let { raw ->
                Spacer(Modifier.height(10.dp))
                val pretty = runCatching {
                    val o = Json.parseToJsonElement(raw).jsonObject
                    buildString {
                        appendLine("Band ${o["band"]?.jsonPrimitive?.content} (graded at level ${ui.proficiency.level.title})")
                        o["usedTargets"]?.jsonArray?.let { a -> if (a.isNotEmpty())
                            appendLine("Deployed: " + a.joinToString { it.jsonPrimitive.content }) }
                        o["misused"]?.jsonArray?.let { a -> if (a.isNotEmpty())
                            appendLine("Misused: " + a.joinToString { it.jsonPrimitive.content }) }
                        o["missedOpportunities"]?.jsonArray?.forEach { m ->
                            val mo = m.jsonObject
                            appendLine("Could have used ${mo["word"]?.jsonPrimitive?.content} — “${mo["where"]?.jsonPrimitive?.content}”")
                        }
                        o["topFix"]?.jsonPrimitive?.content?.let { appendLine("✎ $it") }
                    }
                }.getOrElse { raw }
                Text(pretty, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF111726))
            }
        }
    }
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
