package com.orion.voicelab.engineering

import org.junit.Assert.*
import org.junit.Test

class CodeChangeValidatorTest {
    private fun proposal(
        workBranch: String = "orion/feature",
        changes: List<CodeFileChange> = listOf(
            CodeFileChange("src/Main.kt", CodeFileOperation.CREATE, "class Main")
        )
    ) = CodeChangeProposal(
        repository = "owner/project",
        baseBranch = "main",
        workBranch = workBranch,
        title = "Add feature",
        summary = "Implement the requested feature.",
        changes = changes
    )

    @Test fun acceptsSafeNewFileOnDedicatedBranch() {
        val result = CodeChangeValidator.validate(proposal())
        assertTrue(result.valid)
        assertTrue(result.approvalRequired)
        assertFalse(result.requiresElevatedReview)
    }

    @Test fun rejectsMainAsWorkBranch() {
        assertFalse(CodeChangeValidator.validate(proposal(workBranch = "main")).valid)
    }

    @Test fun rejectsPathTraversalAndGitMetadata() {
        listOf("../secrets.txt", "src/../../secret", ".git/config", "/etc/passwd", "src\\Main.kt")
            .forEach { path -> assertFalse(path, CodeChangeValidator.isSafePath(path)) }
    }

    @Test fun updateAndDeleteRequireExpectedBlobSha() {
        val update = CodeFileChange("src/A.kt", CodeFileOperation.UPDATE, "new", null)
        val delete = CodeFileChange("src/B.kt", CodeFileOperation.DELETE, null, null)
        assertFalse(CodeChangeValidator.validate(proposal(changes = listOf(update))).valid)
        assertFalse(CodeChangeValidator.validate(proposal(changes = listOf(delete))).valid)
    }

    @Test fun rejectsPossibleCredentials() {
        val content = "val token = \"ghp_abcdefghijklmnopqrstuvwxyz1234567890\""
        val result = CodeChangeValidator.validate(
            proposal(changes = listOf(CodeFileChange("src/Config.kt", CodeFileOperation.CREATE, content)))
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("secreto") })
    }

    @Test fun flagsBuildAndWorkflowChangesForElevatedReview() {
        val result = CodeChangeValidator.validate(
            proposal(changes = listOf(CodeFileChange("app/build.gradle.kts", CodeFileOperation.CREATE, "plugins {}")))
        )
        assertTrue(result.valid)
        assertTrue(result.requiresElevatedReview)
    }

    @Test fun blocksCredentialFilesAndPrivateKeyMaterialByPath() {
        listOf(
            ".env", ".env.production", "config/.env.production", "config/local.properties", ".npmrc", "secrets/config.json",
            ".ssh/id_rsa", ".aws/credentials", "secrets.json", ".envrc", ".docker/config.json", ".kube/config",
            ".git-credentials", "terraform.tfstate", "infra/prod.tfvars", "tokens.json",
            "config/service-account-prod.json", "signing/release.p12", "certs/private.pem"
        ).forEach { path ->
            assertFalse("Sensitive path must be blocked: $path", CodeChangeValidator.isSafePath(path))
        }
    }

    @Test fun rejectsFileDirectoryConflictsInsideSinglePatch() {
        val proposal = proposal(changes = listOf(
            CodeFileChange("config", CodeFileOperation.CREATE, "file"),
            CodeFileChange("config/settings.json", CodeFileOperation.CREATE, "{}")
        ))
        val result = CodeChangeValidator.validate(proposal)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("conflictos entre archivo y directorio") })
    }

    @Test fun rejectsControlCharactersInPaths() {
        assertFalse(CodeChangeValidator.isSafePath("src/Main.kt\nInjected"))
        assertFalse(CodeChangeValidator.isSafePath("src/Main.kt\tInjected"))
    }

    @Test fun secretScannerCanProtectWorkspaceContext() {
        assertTrue(CodeChangeValidator.containsPotentialSecret("api_key = \"sk-abcdefghijklmnopqrstuvwxyz123456\""))
        assertTrue(CodeChangeValidator.containsPotentialSecret("Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345"))
        assertFalse(CodeChangeValidator.containsPotentialSecret("fun calculateTotal(items: List<Int>) = items.sum()"))
    }

    @Test fun rejectsOversizedPatch() {
        val huge = "x".repeat(CodeChangeValidator.MAX_FILE_BYTES + 1)
        val result = CodeChangeValidator.validate(
            proposal(changes = listOf(CodeFileChange("src/Large.kt", CodeFileOperation.CREATE, huge)))
        )
        assertFalse(result.valid)
    }
}
