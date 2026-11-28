package com.orion.voicelab.connectors.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubApiClientValidationTest {
    @Test
    fun acceptsOwnerAndRepositoryOnly() {
        assertTrue(GitHubApiClient.isValidRepository("UEPceo/ORION-Assistant"))
    }

    @Test
    fun rejectsTraversalSegmentsAndExtraPathComponents() {
        listOf("../owner/repo", "../owner", "owner/..", "./owner/repo", "owner/repo/../../other", "owner/.", "owner/repo?x=1").forEach {
            assertFalse("Should reject $it", GitHubApiClient.isValidRepository(it))
            assertFalse("Pattern must not accept $it", GitHubApiClient.REPOSITORY_PATTERN.matches(it))
        }
    }
}
