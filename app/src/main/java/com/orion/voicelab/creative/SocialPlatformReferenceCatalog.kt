package com.orion.voicelab.creative

/** Official starting points for periodic platform-policy and recommender-system review. */
data class SocialPlatformReference(
    val platform: SocialPlatform,
    val topic: String,
    val publisher: String,
    val url: String,
    val freshnessWarning: String
)

object SocialPlatformReferenceCatalog {
    val references = listOf(
        SocialPlatformReference(
            SocialPlatform.INSTAGRAM,
            "Feed ranking system card",
            "Meta AI",
            "https://ai.meta.com/tools/system-cards/instagram-feed-ranking/",
            "System card is historical; verify current Reels, Explore and Feed guidance separately."
        ),
        SocialPlatformReference(
            SocialPlatform.TIKTOK,
            "How TikTok recommends content",
            "TikTok Support",
            "https://support.tiktok.com/en/using-tiktok/exploring-videos/how-tiktok-recommends-content",
            "Review periodically because ranking signals and eligibility rules can change."
        ),
        SocialPlatformReference(
            SocialPlatform.YOUTUBE,
            "YouTube recommendation system",
            "YouTube Help",
            "https://support.google.com/youtube/answer/16533387",
            "Use current Creator guidance and the channel's own Analytics alongside this overview."
        ),
        SocialPlatformReference(
            SocialPlatform.LINKEDIN,
            "How the Feed ranks content",
            "LinkedIn Help",
            "https://www.linkedin.com/help/linkedin/answer/a9554004",
            "Feed systems evolve; cross-check with recent LinkedIn engineering/product updates."
        ),
        SocialPlatformReference(
            SocialPlatform.LINKEDIN,
            "Improving the Feed",
            "LinkedIn News",
            "https://news.linkedin.com/2026/ImprovingTheFeed",
            "Product update dated March 12, 2026; re-check for subsequent changes."
        )
    )

    fun forPlatform(platform: SocialPlatform): List<SocialPlatformReference> =
        references.filter { it.platform == platform }
}
