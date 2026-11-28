package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class ExplicitKnowledgeLearningIntentTest {
    @Test fun requiresExplicitLearningCommandAndHttpsUrl() {
        assertNull(ExplicitKnowledgeLearningIntent.route("https://developer.android.com explica esta página"))
        assertNull(ExplicitKnowledgeLearningIntent.route("aprende de esta fuente http://example.org/page"))
        assertNull(ExplicitKnowledgeLearningIntent.route("aprende de esta fuente"))
    }

    @Test fun extractsSourceAndTopicWithoutCommandText() {
        val request = ExplicitKnowledgeLearningIntent.route(
            "Aprende de esta fuente sobre Kotlin: https://kotlinlang.org/docs/coroutines.html."
        )
        assertNotNull(request)
        assertEquals("https://kotlinlang.org/docs/coroutines.html", request!!.url)
        assertEquals("sobre Kotlin:", request.topic)
    }

    @Test fun rejectsOverlongUrls() {
        val request = ExplicitKnowledgeLearningIntent.route(
            "aprende de esta fuente https://" + "a".repeat(1_600) + ".example/path"
        )
        assertNull(request)
    }
}
