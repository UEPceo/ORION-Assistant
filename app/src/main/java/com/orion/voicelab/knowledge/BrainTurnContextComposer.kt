package com.orion.voicelab.knowledge

/**
 * Joins independently-produced context channels into one deterministic system prompt.
 * Blank channels are omitted and each active channel is separated by real line breaks.
 */
object BrainTurnContextComposer {
    fun compose(
        basePrompt: String,
        knowledgePrompt: String,
        modelGuidance: String = "",
        planningGuidance: String = "",
        creativeContext: String = "",
        selfKnowledgeContext: String = ""
    ): String = listOf(
        basePrompt,
        knowledgePrompt,
        modelGuidance,
        planningGuidance,
        creativeContext,
        selfKnowledgeContext
    ).map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n\n")
}
