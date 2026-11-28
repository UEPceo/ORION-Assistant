package com.orion.voicelab.engineering

import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.llm.LlmGenerationResult
import com.orion.voicelab.llm.LocalLanguageModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalModelCodePatchGeneratorTest {
    private class FakeModel(private val output:String):LocalLanguageModel {
        override val modelDisplayName="fake"
        override val modelLicense="test"
        override fun isModelInstalled()=true
        override fun isLoaded()=true
        override fun modelSizeMb()=1L
        override suspend fun load()=0L
        override suspend fun generateReply(systemPrompt:String,history:List<ChatMessage>,onToken:(String)->Unit)=
            LlmGenerationResult(output,1,2,3,false)
        override fun cancelGeneration() {}
        override fun unload() {}
    }

    private val snapshot=CodeWorkspaceSnapshot("owner/repo","orion/task","a".repeat(40),emptyList(),0)

    @Test fun parsesTypedCreateProposalAndUsesTrustedRepositoryScope()=runBlocking {
        val model=FakeModel("""{"title":"Add file","summary":"Create source","changes":[{"path":"src/A.kt","operation":"CREATE","content":"class A","expectedBlobSha":null}]}""")
        val result=LocalModelCodePatchGenerator(model).generate("Create A",snapshot,"main").getOrThrow()
        assertEquals("owner/repo",result.repository)
        assertEquals("main",result.baseBranch)
        assertEquals("orion/task",result.workBranch)
        assertEquals(CodeFileOperation.CREATE,result.changes.single().operation)
    }

    @Test fun rejectsMalformedJsonAndUnknownOperations()=runBlocking {
        assertFalse(LocalModelCodePatchGenerator(FakeModel("not json")).generate("task",snapshot,"main").isSuccess)
        val invalid="""{"title":"Bad","summary":"Bad","changes":[{"path":"A.kt","operation":"RUN_SHELL","content":"echo","expectedBlobSha":null}]}"""
        assertFalse(LocalModelCodePatchGenerator(FakeModel(invalid)).generate("task",snapshot,"main").isSuccess)
    }

    @Test fun rejectsGenerationWhenModelIsNotLoaded()=runBlocking {
        val base=FakeModel("""{"title":"A","summary":"B","changes":[{"path":"A.kt","operation":"CREATE","content":"x","expectedBlobSha":null}]}""")
        val unloaded=object:LocalLanguageModel by base { override fun isLoaded()=false }
        assertFalse(LocalModelCodePatchGenerator(unloaded).generate("task",snapshot,"main").isSuccess)
    }
}
