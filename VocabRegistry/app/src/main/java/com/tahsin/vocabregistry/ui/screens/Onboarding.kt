package com.tahsin.vocabregistry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.vocabregistry.ui.AppViewModel

private data class ExamOption(val id: String, val title: String, val blurb: String)

private val EXAMS = listOf(
    ExamOption("IELTS", "IELTS", "Band-focused academic English"),
    ExamOption("GRE", "GRE", "Graduate-level verbal reasoning"),
    ExamOption("BCS", "BCS", "Civil service preliminary vocabulary"),
    ExamOption("Bank", "Bank job", "Banking and finance recruitment"),
    ExamOption("Research", "Research / postgrad", "Scholarly and social-science lexicon"),
)

private val HORIZONS = listOf(3 to "~3 months", 6 to "~6 months", 12 to "~a year")

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var exam by remember { mutableStateOf("IELTS") }
    var months by remember { mutableIntStateOf(6) }

    Column(
        Modifier.fillMaxSize().background(com.tahsin.vocabregistry.ui.theme.Ledger.nightSky)
            .padding(20.dp).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Eyebrow("File opening \u00B7 Welcome")
        Text(
            "Lexicon", color = com.tahsin.vocabregistry.ui.theme.Ledger.Cream,
            fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        )
        Text(
            "A spaced-repetition word ledger for serious exam preparation. Two quick questions, then a short calibration.",
            color = com.tahsin.vocabregistry.ui.theme.Ledger.GreenSoft, fontSize = 13.sp,
            lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        PaperCard {
            Text("What are you preparing for?", fontSize = 16.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, color = Color(0xFF111726))
            Spacer(Modifier.height(8.dp))
            EXAMS.forEach { opt ->
                val selected = exam == opt.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFFEDF1FA) else Color(0xFFF9FAFD))
                        .border(1.dp, if (selected) com.tahsin.vocabregistry.ui.theme.Ledger.Stamp
                            else Color(0xFFE2E7F2), RoundedCornerShape(10.dp))
                        .clickable { exam = opt.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (selected) "\u25C9" else "\u25CB", fontSize = 14.sp,
                        color = if (selected) com.tahsin.vocabregistry.ui.theme.Ledger.Stamp else Color(0xFF566077))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(opt.title, fontSize = 14.sp, fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold, color = Color(0xFF111726))
                        Text(opt.blurb, fontSize = 11.sp, color = Color(0xFF566077))
                    }
                }
            }
            Text(
                "IELTS and GRE cores are always included; BCS, Bank and Research add their own tiers. You can change any of this later in Settings.",
                fontSize = 11.sp, color = Color(0xFF566077), lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        PaperCard {
            Text("When is your exam?", fontSize = 16.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, color = Color(0xFF111726))
            Text("A rough horizon is enough \u2014 you can set the exact date in Settings.",
                fontSize = 11.sp, color = Color(0xFF566077))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HORIZONS.forEach { (m, label) ->
                    val selected = months == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) com.tahsin.vocabregistry.ui.theme.Ledger.Stamp
                                else Color(0xFFF1F4FA))
                            .clickable { months = m }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = if (selected) Color.White else Color(0xFF111726))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.completeOnboarding(exam, months) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.tahsin.vocabregistry.ui.theme.Ledger.Stamp),
        ) {
            Text("Begin calibration", fontFamily = FontFamily.Serif, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
