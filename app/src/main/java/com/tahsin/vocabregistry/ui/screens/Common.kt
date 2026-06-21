package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import kotlin.random.Random
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
            .background(Color(0xFFFFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFD7DEEC), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        content()
    }
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
fun buildCloze(word: Word, all: List<Word>, mode: ProficiencyTracker.DistractorMode, sentenceOverride: String? = null): Pair<String, List<String>> {
    val stem = word.word.split(" ")[0]
    val regex = Regex(Regex.escape(stem), RegexOption.IGNORE_CASE)
    val src = sentenceOverride ?: word.example
    var sentence = if (regex.containsMatchIn(src))
        regex.replace(src, "______") else src + "  →  [______]"
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

@Composable
fun inkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF111726), unfocusedTextColor = Color(0xFF111726),
    focusedBorderColor = Ledger.Brass, unfocusedBorderColor = Color(0xFFD7DEEC),
    cursorColor = Ledger.Stamp,
    focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFEDF1FA),
    unfocusedContainerColor = androidx.compose.ui.graphics.Color(0xFFEDF1FA),
)

@Composable
fun inkOutlinedColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    contentColor = Color(0xFF111726),
    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledContentColor = Color(0xFF111726),
)

/** Scatters faint gold/white stars behind a composable, evoking the night sky. */
fun Modifier.starrySky(seed: Int = 7, count: Int = 70): Modifier = this.drawBehind {
    val r = Random(seed)
    repeat(count) {
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * size.height
        val rad = r.nextFloat() * 2.4f + 0.7f
        val gold = r.nextFloat() < 0.55f
        val color = if (gold) Color(0xFFE8C547) else Color(0xFFEDF1FB)
        drawCircle(color.copy(alpha = r.nextFloat() * 0.5f + 0.18f), rad, Offset(x, y))
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF111726), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Answer option as a plain clickable box. No Material Button = no content-alpha state
 *  machine can ever fade the label. Card backgrounds are light in every theme, so the
 *  dark ink text is always high-contrast. */
@Composable
fun OptionButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(1.5.dp, Color(0xFFD7DEEC), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF111726), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
