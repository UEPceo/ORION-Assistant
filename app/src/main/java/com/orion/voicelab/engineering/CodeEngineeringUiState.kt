package com.orion.voicelab.engineering

data class CodeEngineeringUiState(
    val isWorking: Boolean = false,
    val session: CodeEngineeringSession? = null,
    val preparedChange: PreparedCodeChange? = null,
    val lastCommitSha: String? = null,
    val pullRequestUrl: String? = null,
    val ciInspection: CodeEngineeringCiInspection? = null,
    val ciError: String? = null,
    val repairAttemptsUsed: Int = 0,
    val error: String? = null
)
