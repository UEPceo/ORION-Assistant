package com.orion.voicelab.creative

import com.orion.voicelab.knowledge.BrainQueryRouter
import com.orion.voicelab.knowledge.KnowledgeDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeBrainTest {
    @Test
    fun routesSocialMediaAndDesignToDedicatedDomains() {
        assertEquals(KnowledgeDomain.SOCIAL_MEDIA, BrainQueryRouter.route("Prepara una estrategia para TikTok y Reels").domain)
        assertEquals(KnowledgeDomain.CREATIVE_DESIGN, BrainQueryRouter.route("Diseña una identidad visual y una miniatura").domain)
        assertEquals(KnowledgeDomain.IMAGE_VIDEO, BrainQueryRouter.route("Analiza este vídeo").domain)
    }

    @Test
    fun creativeSkillRouterPrioritizesExplicitMediaActions() {
        assertEquals(CreativeSkill.IMAGE_GENERATION, CreativeSkillRouter.route("Genera una imagen para Instagram"))
        assertEquals(CreativeSkill.VIDEO_UNDERSTANDING, CreativeSkillRouter.route("Resume este vídeo"))
        assertEquals(CreativeSkill.SOCIAL_STRATEGY, CreativeSkillRouter.route("Planifica el calendario de TikTok"))
    }

    @Test
    fun videoSamplingIsBoundedAndChronological() {
        val frames = VideoFrameSamplingPolicy.timestamps(durationMs = 60_000L, maxFrames = 8)
        assertEquals(8, frames.size)
        assertEquals(frames.sorted(), frames)
        assertEquals(0L, frames.first())
        assertTrue(frames.last() < 60_000L)
    }

    @Test
    fun noMediaProviderIsReportedAsUnconfigured() {
        val runtime = OrionMediaRuntime()
        assertEquals(MediaCapabilityStatus.REQUIRES_CONFIGURATION, runtime.imageGenerationStatus())
        assertEquals(MediaCapabilityStatus.REQUIRES_CONFIGURATION, runtime.videoGenerationStatus())
        assertEquals(MediaCapabilityStatus.REQUIRES_CONFIGURATION, runtime.imageUnderstandingStatus("image/jpeg"))
    }

    @Test
    fun socialPlanContainsMeasurementAndSafetyCaveats() {
        val plan = SocialMediaStrategyEngine.plan(
            SocialContentBrief(
                platform = SocialPlatform.INSTAGRAM,
                goal = SocialGoal.SAVES,
                audience = "pescadores recreativos",
                format = SocialFormat.SHORT_VIDEO,
                topic = "errores al pescar desde playa"
            )
        )
        assertEquals(20, plan.recommendedLengthSeconds)
        assertTrue(plan.measurementPlan.any { it.contains("Retención") })
        assertTrue(plan.caveats.any { it.contains("viralidad") })
    }

    @Test
    fun scoreUsesAvailableSignalsOnly() {
        assertNull(SocialMediaStrategyEngine.outcomeScore(SocialPerformanceSignals()))
        val score = SocialMediaStrategyEngine.outcomeScore(
            SocialPerformanceSignals(audienceRetention = 0.8, saves = 0.6)
        )
        assertTrue(score != null && score in 0.0..1.0)
    }
}
