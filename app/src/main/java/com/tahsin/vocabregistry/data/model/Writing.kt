package com.tahsin.vocabregistry.data.model

import kotlinx.serialization.Serializable

/**
 * A Task 2 writing item. Fields are optional so the same schema covers both
 * fully-authored dual-band exemplars and lighter practice-bank prompts.
 *
 *   id      stable id
 *   type    question type (Opinion / Discussion / Adv-Disadv / Problem-Solution / Two-Part)
 *   topic   topic family (Education, Environment, Technology, …)
 *   prompt  the task statement
 *   band6   a Band-6 model answer (may be empty for bank-only prompts)
 *   band8   a Band-8 model answer (may be empty)
 *   gap     bullet points: what lifts the 6 to an 8
 *   phrases topic-specific phrase bank
 *   targets target vocabulary to deploy
 */
@Serializable
data class WritingJson(
    val id: Int,
    val type: String,
    val topic: String,
    val prompt: String,
    val band6: String = "",
    val band8: String = "",
    val gap: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val targets: List<String> = emptyList(),
) {
    val hasSamples: Boolean get() = band6.isNotBlank() && band8.isNotBlank()
}
