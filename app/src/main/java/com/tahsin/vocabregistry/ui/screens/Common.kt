package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.vocabregistry.data.model.Word
import com.tahsin.vocabregistry.domain.ProficiencyTracker
import com.tahsin.vocabregistry.ui.theme.Ledger

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Ledger.Paper, RoundedCornerShape(6.dp))
            .border(1.dp, Ledger.PaperEdge, RoundedCornerShape(6.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Chip(text: String, color: Color = Ledger.Brass) {
    Text(
        text, fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun GradeStamp(q: Int, label: String? = null) {
    val color = when { q >= 4 -> Ledger.Green; q >= 3 -> Ledger.Brass; else -> Ledger.Stamp }
    val text = label ?: when { q >= 4 -> "Passed"; q >= 3 -> "Adequate"; else -> "Resubmit" }
    Text(
        "$text · $q/5".uppercase(),
        color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, letterSpacing = 2.sp,
        modifier = Modifier
            .rotate(-3f)
            .border(2.5.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(), color = Ledger.Brass, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, letterSpacing = 2.sp)
}

/** Adaptive distractors: difficulty follows the learner's proficiency level. */
fun buildCloze(word: Word, all: List<Word>, mode: ProficiencyTracker.DistractorMode): Pair<String, List<String>> {
    val stem = word.word.split(" ")[0]
    val regex = Regex(Regex.escape(stem), RegexOption.IGNORE_CASE)
    var sentence = if (regex.containsMatchIn(word.example))
        regex.replace(word.example, "______") else word.example + "  →  [______]"
    // article must not betray the answer
    sentence = Regex("\\b(a|an)(\\s+)(?=______)", RegexOption.IGNORE_CASE).replace(sentence) { m ->
        (if (m.groupValues[1][0].isUpperCase()) "A/an" else "a/an") + m.groupValues[2]
    }
    val candidates = when (mode) {
        ProficiencyTracker.DistractorMode.RANDOM_POS ->
            all.filter { it.id != word.id && it.pos == word.pos }
        ProficiencyTracker.DistractorMode.SAME_THEME ->
            all.filter { it.id != word.id && it.pos == word.pos && (it.theme == word.theme || it.tier == word.tier) }
        ProficiencyTracker.DistractorMode.NEAR_SYNONYM -> {
            val syn = word.synonymList.map { it.lowercase() }.toSet()
            val near = all.filter { c ->
                c.id != word.id && c.pos == word.pos &&
                (c.theme == word.theme || c.synonymList.any { it.lowercase() in syn })
            }
            near.ifEmpty { all.filter { it.id != word.id && it.pos == word.pos } }
        }
    }.ifEmpty { all.filter { it.id != word.id } }
    val picks = candidates.shuffled().distinctBy { it.word }.take(3).map { it.word }
    return sentence to (picks + word.word).shuffled()
}
