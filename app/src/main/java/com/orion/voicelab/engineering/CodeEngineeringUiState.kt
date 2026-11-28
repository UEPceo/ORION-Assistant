package com.orion.voicelab.engineering

data class CodeEngineeringUiState(
    val isWorking: Boolean = false,
    val session: CodeEngineeringSession? = null,
    val preparedChange: PreparedCodeChange? = null,
    val lastCommitSha: String? = null,
    val pullRequestUrl: String? = null,
    val error: String? = null
)
