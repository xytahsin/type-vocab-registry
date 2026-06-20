package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.vocabregistry.data.model.Axis
import com.tahsin.vocabregistry.domain.*
import com.tahsin.vocabregistry.grading.*
import com.tahsin.vocabregistry.ui.AppViewModel
import com.tahsin.vocabregistry.ui.theme.Ledger
import kotlinx.coroutines.launch

private const val IDK = "__IDK__"
private sealed class Phase {
    data object Meet : Phase()
    data object Ask : Phase()
    data object Grading : Phase()
    data class ClozeFeedback(val correct: Boolean, val idk: Boolean, val q: Int) : Phase()
    data class ProdFeedback(val q: Int, val g: ProductionGrade) : Phase()
    data class CollFeedback(val q: Int, val g: CollocationGrade) : Phase()
    data class SelfGrade(val axis: Axis) : Phase()
}

@Composable
fun SessionScreen(vm: AppViewModel, mode: SessionMode, onClose: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val cards = remember { vm.composeSession(mode) }
    var idx by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf<Phase>(Phase.Ask) }
    var input by remember { mutableStateOf("") }
    var startMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var done by remember { mutableIntStateOf(0) }
    var qSum by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val dMode = ProficiencyTracker.distractorMode(ui.proficiency.level)

    fun finish() { vm.finishSession(); onClose() }

    if (cards.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(24.dp)) {
            PaperCard {
                Text("Nothing due right now", fontSize = 20.sp, fontFamily = FontFamily.Serif)
                Text("The docket is clear. Run a Deep session to introduce new words.",
                    fontSize = 13.sp, color = Color(0xFF566077))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                    Text("Back to docket")
                }
            }
        }
        return
    }
    if (idx >= cards.size) {
        Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(24.dp)) {
            PaperCard {
                Eyebrow("Session filed")
                Text("$done items", fontSize = 32.sp, fontFamily = FontFamily.Serif)
                Text("avg quality ${if (done > 0) "%.1f".format(qSum.toDouble() / done) else "—"} / 5 · grading band ${ui.proficiency.level.graderBand}",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF566077))
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::finish, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                    Text("Back to docket")
                }
            }
        }
        return
    }

    val card = cards[idx]
    val word = ui.words.firstOrNull { it.id == card.wordId } ?: return
    val cloze = remember(idx) { buildCloze(word, ui.words, dMode) }
    LaunchedEffect(idx) {
        input = ""
        startMs = System.currentTimeMillis()
        phase = if (card.meetFirst) Phase.Meet else Phase.Ask
        if (card.kind == CardKind.NEW) vm.introduceWord(word.id)
    }
    fun record(axis: Axis, q: Int, resp: String, tags: List<String>, prov: Boolean) {
        vm.applyReview(word.id, axis, q, resp, tags, prov)
        done++; qSum += q
    }
    fun next() { if (idx + 1 >= cards.size) idx = cards.size else idx++ }

    Column(Modifier.fillMaxSize().background(Ledger.nightSky).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = ::finish) {
                Text("← End session", color = Ledger.GreenSoft, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Text("${idx + 1} / ${cards.size} · ${mode.name}", color = Ledger.GreenSoft,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        val animProgress by animateFloatAsState(
            targetValue = idx.toFloat() / cards.size, animationSpec = tween(450), label = "progress")
        LinearProgressIndicator(
            progress = { animProgress },
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            color = Ledger.Brass, trackColor = Color(0xFF0A1430),
        )
        PaperCard(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Chip(Ledger.tierName(word.tier), Ledger.tierColor(word.tier))
                Chip(if (phase is Phase.Meet) "NEW WORD" else card.axis.label.uppercase(), Color(0xFF566077))
            }
            Spacer(Modifier.height(12.dp))

            when (val ph = phase) {
                is Phase.Meet -> {
                    Text(word.word, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text("${word.pos} · ${word.theme}", fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, color = Color(0xFF566077))
                    Spacer(Modifier.height(8.dp))
                    Text(word.definition, fontSize = 15.sp)
                    Text("“${word.example}”", fontSize = 14.sp, fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif, color = Color(0xFF33507F),
                        modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        word.collocationList.take(2).forEach { Chip(it, Ledger.Green) }
                    }
                    word.confusable?.let {
                        Text("⚠ $it", color = Ledger.Stamp, fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { phase = Phase.Ask; startMs = System.currentTimeMillis() },
                        colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                        Text("Got it — test me")
                    }
                }
                is Phase.Ask -> when (card.axis) {
                    Axis.R -> {
                        Text(cloze.first, fontSize = 17.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp, color = Color(0xFF111726))
                        Spacer(Modifier.height(12.dp))
                        cloze.second.forEach { opt ->
                            OutlinedButton(
                                onClick = {
                                    val correct = opt == word.word
                                    val slow = System.currentTimeMillis() - startMs > 15_000
                                    val q = if (correct) (if (slow) 3 else 5) else 1
                                    record(Axis.R, q, opt, emptyList(), false)
                                    phase = Phase.ClozeFeedback(correct, false, q)
                                },
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                colors = inkOutlinedColors(),
                            ) { Text(opt, color = Color(0xFF111726)) }
                        }
                        TextButton(onClick = {
                            record(Axis.R, 1, "(not sure)", emptyList(), false)
                            phase = Phase.ClozeFeedback(correct = false, idk = true, q = 1)
                        }, Modifier.fillMaxWidth()) {
                            Text("Not sure / don't know", color = Color(0xFF566077), fontFamily = FontFamily.Monospace)
                        }
                    }
                    Axis.P, Axis.G -> {
                        Text(word.word, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Text("Write one academic sentence using this word." +
                            if (card.axis == Axis.G) " Register is being graded." else "",
                            fontSize = 13.sp, color = Color(0xFF566077), modifier = Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it },
                            Modifier.fillMaxWidth(), minLines = 3,
                            placeholder = { Text("Your sentence…", color = Color(0xFF566077)) },
                            colors = inkFieldColors())
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = input.trim().split(Regex("\\s+")).size >= 4,
                            onClick = {
                                if (!ui.apiKeySet) { phase = Phase.SelfGrade(card.axis); return@Button }
                                phase = Phase.Grading
                                scope.launch {
                                    val g = vm.grader.gradeProduction(word, input.trim(), ui.proficiency.level)
                                    val q = g.aggregateQ
                                    record(card.axis, q, input, g.errorTags, g.provisional)
                                    if (card.axis == Axis.P)
                                        vm.applyReview(word.id, Axis.G, g.registerQ, input, emptyList(), g.provisional)
                                    phase = Phase.ProdFeedback(q, g)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp),
                        ) { Text("Submit for grading") }
                    }
                    Axis.C -> {
                        Text(word.word, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Text("Type a natural collocation using this word. (${word.definition})",
                            fontSize = 13.sp, color = Color(0xFF566077), modifier = Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it },
                            Modifier.fillMaxWidth(), placeholder = { Text("partner word + the word", color = Color(0xFF566077)) },
                            colors = inkFieldColors())
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                val t = input.trim().lowercase()
                                val local = word.collocationList.any {
                                    val n = it.lowercase(); t.contains(n) || n.contains(t)
                                }
                                // adaptive: above PROFICIENT, local fuzzy matches still go to the LLM
                                if (local && ui.proficiency.level < ProficiencyLevel.PROFICIENT) {
                                    record(Axis.C, 5, input, emptyList(), false)
                                    phase = Phase.CollFeedback(5,
                                        CollocationGrade(1.0, "Exact match with a known strong collocation."))
                                } else if (!ui.apiKeySet) {
                                    if (local) {
                                        record(Axis.C, 5, input, emptyList(), false)
                                        phase = Phase.CollFeedback(5,
                                            CollocationGrade(1.0, "Exact match with a known strong collocation."))
                                    } else phase = Phase.SelfGrade(Axis.C)
                                } else {
                                    phase = Phase.Grading
                                    scope.launch {
                                        val g = vm.grader.gradeCollocation(word, input.trim(), ui.proficiency.level)
                                        record(Axis.C, g.q, input,
                                            if (g.q < 3) listOf("wrong_collocation") else emptyList(), g.provisional)
                                        phase = Phase.CollFeedback(g.q, g)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp),
                        ) { Text("Check pairing") }
                    }
                }
                is Phase.SelfGrade -> {
                    Text("Compare against the file, then grade yourself honestly:",
                        fontSize = 13.sp, color = Color(0xFF566077))
                    Text("“${word.example}”", fontSize = 14.sp, fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif, color = Color(0xFF33507F),
                        modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        word.collocationList.take(3).forEach { Chip(it, Ledger.Green) }
                    }
                    word.confusable?.let {
                        Text("⚠ $it", color = Ledger.Stamp, fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text("Your answer: ${input.trim()}", fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(10.dp))
                    val axis = (phase as Phase.SelfGrade).axis
                    listOf(1 to "Missed it", 3 to "Shaky", 4 to "Good", 5 to "Precise").forEach { (q, label) ->
                        OutlinedButton(onClick = {
                            record(axis, q, input, listOf("self_graded"), true)
                            if (axis == Axis.P)
                                vm.applyReview(word.id, Axis.G, q, input, listOf("self_graded"), true)
                            phase = Phase.ClozeFeedback(correct = q >= 4, idk = false, q = q)
                        }, Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = inkOutlinedColors()) {
                            Text("$label · $q/5", color = Color(0xFF111726))
                        }
                    }
                }
                is Phase.Grading -> Text("The examiner is reading…", fontFamily = FontFamily.Monospace,
                    color = Color(0xFF566077), modifier = Modifier.padding(vertical = 24.dp))
                is Phase.ClozeFeedback -> {
                    GradeStamp(ph.q, if (ph.idk) "Noted" else if (ph.correct && ph.q == 3) "Slow pass" else null)
                    if (!ph.correct) {
                        Text("Correct: ${word.word} — ${word.definition}",
                            fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                        word.confusable?.let {
                            Text("⚠ $it", color = Ledger.Stamp, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = ::next, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                        Text("Next →")
                    }
                }
                is Phase.ProdFeedback -> {
                    GradeStamp(ph.q)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("gram" to ph.g.grammar, "coll" to ph.g.collocation,
                               "sem" to ph.g.semantic, "reg" to ph.g.register).forEach { (k, v) ->
                            Chip("$k ${(v * 100).toInt()}%",
                                if (v >= 0.8) Ledger.Green else if (v >= 0.5) Ledger.Brass else Ledger.Stamp)
                        }
                    }
                    if (ph.g.explanation.isNotBlank())
                        Text(ph.g.explanation, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    ph.g.correction?.let {
                        Text("✎ $it", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                            color = Ledger.Green, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = ::next, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                        Text("Next →")
                    }
                }
                is Phase.CollFeedback -> {
                    GradeStamp(ph.q)
                    Text(ph.g.explanation, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                    ph.g.betterOption?.let {
                        Text("✎ Stronger: $it", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                            color = Ledger.Green, fontSize = 14.sp)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        word.collocationList.take(3).forEach { Chip(it, Ledger.Green) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = ::next, colors = ButtonDefaults.buttonColors(containerColor = Ledger.Stamp)) {
                        Text("Next →")
                    }
                }
            }
        }
    }
}
