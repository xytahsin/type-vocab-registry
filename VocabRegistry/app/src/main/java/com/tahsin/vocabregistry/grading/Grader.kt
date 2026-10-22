package com.tahsin.vocabregistry.grading

import com.tahsin.vocabregistry.data.model.Word
import com.tahsin.vocabregistry.domain.ProficiencyLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ProductionGrade(
    val grammar: Double = 0.0,
    val collocation: Double = 0.0,
    val semantic: Double = 0.0,
    val register: Double = 0.0,
    val correction: String? = null,
    val explanation: String = "",
    val errorTags: List<String> = emptyList(),
    val provisional: Boolean = false,
) {
    val aggregateQ: Int get() =
        Math.round((0.2 * grammar + 0.3 * collocation + 0.3 * semantic + 0.2 * register) * 5).toInt()
    val registerQ: Int get() = Math.round(register * 5).toInt()
}

@Serializable
data class CollocationGrade(
    val collocation: Double = 0.0,
    val explanation: String = "",
    val betterOption: String? = null,
    val provisional: Boolean = false,
) { val q: Int get() = Math.round(collocation * 5).toInt() }

class Grader(private val apiKeyProvider: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun call(prompt: String, maxTokens: Int): String? = withContext(Dispatchers.IO) {
        val key = apiKeyProvider() ?: return@withContext null
        if (key.isBlank()) return@withContext null
        val body: JsonObject = buildJsonObject {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            }
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = json.parseToJsonElement(resp.body!!.string()).jsonObject
                root["content"]!!.jsonArray
                    .mapNotNull { it.jsonObject }
                    .filter { it["type"]?.jsonPrimitive?.content == "text" }
                    .joinToString("") { it["text"]!!.jsonPrimitive.content }
            }
        }.getOrNull()
    }

    private fun stripFences(s: String) = s.replace("```json", "").replace("```", "").trim()

    /**
     * ADAPTIVE STRICTNESS: the prompt embeds the learner's current proficiency band.
     * The identical sentence is graded harder as the learner levels up, and more
     * gently if they have been demoted — the grading system follows the learner.
     */
    suspend fun gradeProduction(word: Word, sentence: String, level: ProficiencyLevel): ProductionGrade {
        val prompt = """
You are an IELTS examiner and academic-English collocation expert. Grade the learner's use of the target word. Return ONLY JSON, no prose, no markdown fences.

LEARNER PROFICIENCY: ${level.title}. Hold this learner to IELTS band ${level.graderBand} standards for lexical resource — a sentence that would not earn band ${level.graderBand} should score below 0.8 on the relevant dimensions. Calibrate strictness to this band, not to an absolute scale.

TARGET WORD: ${word.word} (${word.pos}, tier ${word.tier})
DEFINITION: ${word.definition}
EXPECTED COLLOCATIONS: ${word.collocationList.joinToString("; ")}
${word.confusable?.let { "COMMON CONFUSION: $it" } ?: ""}
REGISTER REQUIRED: academic / formal written English
LEARNER SENTENCE: "$sentence"

Return exactly:
{"grammar":0-1,"collocation":0-1,"semantic":0-1,"register":0-1,"correction":"corrected sentence if any score < 0.8, else null","explanation":"one sentence on the single most important fix","errorTags":["wrong_collocation"|"register_mismatch"|"wrong_sense"|"grammar"|"spelling"|"none"]}
""".trimIndent()
        val raw = call(prompt, 1000) ?: return Heuristics.production(word, sentence)
        return runCatching { json.decodeFromString<ProductionGrade>(stripFences(raw)) }
            .getOrElse { Heuristics.production(word, sentence) }
    }

    suspend fun gradeCollocation(word: Word, answer: String, level: ProficiencyLevel): CollocationGrade {
        val strict = if (level >= ProficiencyLevel.PROFICIENT)
            "Be strict: near-misses and weak pairings score below 0.6." else
            "Be encouraging: accept any natural, plausible pairing."
        val prompt = """
You are a collocation expert. The learner produced a collocation or short phrase for the target word. Return ONLY JSON, no markdown.
$strict

TARGET WORD: ${word.word} (${word.pos})
KNOWN GOOD COLLOCATIONS: ${word.collocationList.joinToString("; ")}
LEARNER ANSWER: "$answer"

Return exactly:
{"collocation":0-1,"explanation":"one short sentence","betterOption":"a stronger collocation if theirs is weak, else null"}
""".trimIndent()
        val raw = call(prompt, 500) ?: return Heuristics.collocation(word, answer)
        return runCatching { json.decodeFromString<CollocationGrade>(stripFences(raw)) }
            .getOrElse { Heuristics.collocation(word, answer) }
    }

    suspend fun gradeEssay(promptText: String, essay: String, targets: List<String>, level: ProficiencyLevel): String? {
        val p = """
You are an IELTS Writing examiner. Analyse this Task 2 response against band ${level.graderBand} expectations. Return ONLY JSON, no markdown.

PROMPT: $promptText
LEARNER TEXT: ""${'"'}$essay""${'"'}
LEARNER'S TARGET VOCABULARY: ${targets.joinToString(", ")}

Return exactly:
{"band":number,"usedTargets":[..],"misused":[..],"missedOpportunities":[{"word":"..","where":".."}],"topFix":".."}
""".trimIndent()
        return call(p, 1200)?.let { stripFences(it) }
    }
}

object Heuristics {
    fun production(word: Word, text: String): ProductionGrade {
        val t = text.lowercase()
        val stem = word.word.lowercase().split(" ")[0].let { it.take(maxOf(4, it.length - 3)) }
        val usedWord = t.contains(stem)
        val usedColl = word.collocationList.any { c ->
            c.lowercase().replace(word.word.lowercase(), "").trim()
                .split(" ").firstOrNull { it.length > 3 }?.let { t.contains(it) } == true
        }
        val longEnough = t.split(Regex("\\s+")).size >= 6
        val base = (if (usedWord) 0.5 else 0.1) + (if (usedColl) 0.3 else 0.0) + (if (longEnough) 0.2 else 0.0)
        return ProductionGrade(
            grammar = base, collocation = if (usedColl) 0.8 else 0.4,
            semantic = if (usedWord) 0.6 else 0.2, register = 0.6,
            explanation = "Provisional score (grader unreachable) — will be reconciled when online.",
            errorTags = listOf("provisional"), provisional = true,
        )
    }
    fun collocation(word: Word, answer: String): CollocationGrade {
        val t = answer.trim().lowercase()
        val hit = word.collocationList.any { c ->
            val n = c.lowercase(); t.contains(n) || n.contains(t)
        }
        return CollocationGrade(
            collocation = if (hit) 1.0 else 0.6,
            explanation = if (hit) "Matches a known strong collocation."
                          else "Provisional — known good: ${word.collocationList.joinToString(" · ")}",
            provisional = !hit,
        )
    }
}
