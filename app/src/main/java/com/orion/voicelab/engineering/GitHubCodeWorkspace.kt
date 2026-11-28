package com.orion.voicelab.engineering

import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubBranchSummary
import com.orion.voicelab.connectors.github.GitHubFileContent
import com.orion.voicelab.connectors.github.GitHubFileSummary

data class CodeWorkspaceSnapshot(val repository:String,val branch:String,val headSha:String,val files:List<GitHubFileContent>,val totalBytes:Int)
data class CodeWorkspaceLimits(val maxFiles:Int=25,val maxFileBytes:Int=200*1024,val maxTotalBytes:Int=1024*1024)

/** Explicit-file workspace; no whole-repository clone or implicit context expansion. */
class GitHubCodeWorkspace(private val api:GitHubApiClient,private val limits:CodeWorkspaceLimits=CodeWorkspaceLimits()) {
    suspend fun browse(repository:String,branch:String,folder:String=""):Result<List<GitHubFileSummary>> {
        if(folder.isNotEmpty()&&!CodeChangeValidator.isSafePath(folder)) return Result.failure(IllegalArgumentException("La carpeta solicitada no es segura."))
        return api.listFiles(repository,folder,branch)
    }
    suspend fun open(repository:String,branch:String,selectedPaths:List<String>):Result<CodeWorkspaceSnapshot> = runCatching {
        require(selectedPaths.isNotEmpty()) { "Selecciona al menos un archivo." }
        require(selectedPaths.size<=limits.maxFiles) { "Se supera el límite de archivos." }
        require(selectedPaths.distinct().size==selectedPaths.size) { "Hay rutas duplicadas." }
        selectedPaths.forEach { require(CodeChangeValidator.isSafePath(it)) { "Ruta no permitida: ${it.take(120)}." } }
        val head:GitHubBranchSummary=api.getBranchHead(repository,branch).getOrThrow()
        val files=selectedPaths.map { path -> api.getFile(repository,path,branch).getOrThrow().also {
            require(it.sizeBytes<=limits.maxFileBytes) { "El archivo ${it.path} supera el límite." }
        } }
        val total=files.sumOf { it.sizeBytes }
        require(total<=limits.maxTotalBytes) { "El contexto supera el límite total." }
        CodeWorkspaceSnapshot(repository,branch,head.sha,files,total)
    }
}
