package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orion.voicelab.engineering.CodeEngineeringSession
import com.orion.voicelab.engineering.CodeEngineeringUiState
import com.orion.voicelab.engineering.CodeFileOperation

@Composable
fun CodeEngineeringCard(
    state: CodeEngineeringUiState,
    onStartSession: (String,String,String,String,Boolean)->Unit,
    onPrepare: (CodeEngineeringSession,String,List<String>)->Unit,
    onCommit: (CodeEngineeringSession,String,Boolean)->Unit,
    onCreatePullRequest: (CodeEngineeringSession,String,String,Boolean)->Unit,
    onRevoke: (CodeEngineeringSession)->Unit
) {
    var repository by remember { mutableStateOf("") }
    var baseBranch by remember { mutableStateOf("main") }
    var workBranch by remember { mutableStateOf("orion/task") }
    var task by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf("") }
    var sessionConfirmed by remember(state.session?.grant?.id) { mutableStateOf(false) }
    var diffConfirmed by remember(state.preparedChange?.diffDigest) { mutableStateOf(false) }
    val session=state.session
    val prepared=state.preparedChange

    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Programación de repositorios",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("ORION inspecciona archivos seleccionados, genera un parche con el modelo local/híbrido y solo escribe tras revisar y aprobar el diff. El código se valida en CI; nunca se ejecuta dentro del proceso Android.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(repository,{repository=it},label={Text("Repositorio propietario/nombre")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=session==null)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(baseBranch,{baseBranch=it},label={Text("Rama base")},modifier=Modifier.weight(1f),singleLine=true,enabled=session==null)
                OutlinedTextField(workBranch,{workBranch=it},label={Text("Rama de trabajo")},modifier=Modifier.weight(1f),singleLine=true,enabled=session==null)
            }
            OutlinedTextField(task,{task=it},label={Text("Qué debe programar o corregir ORION")},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=session==null)
            if(session==null) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(sessionConfirmed,{sessionConfirmed=it})
                    Text("Autorizo crear una rama y trabajar solo en este repositorio y esta tarea.",style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick={onStartSession(repository.trim(),baseBranch.trim(),workBranch.trim(),task.trim(),true)},enabled=!state.isWorking&&sessionConfirmed&&repository.contains('/')&&task.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
                    Text("Crear rama y autorizar tarea")
                }
            } else {
                Text("Sesión activa: ${session.grant.repository} · ${session.grant.workBranch}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                OutlinedTextField(selectedPaths,{selectedPaths=it},label={Text("Archivos a inspeccionar (separados por comas)")},modifier=Modifier.fillMaxWidth(),minLines=2)
                Button(onClick={onPrepare(session,task.trim(),selectedPaths.split(',', '\n').map{it.trim()}.filter{it.isNotBlank()})},enabled=!state.isWorking&&task.isNotBlank()&&selectedPaths.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
                    Text("Inspeccionar y preparar propuesta")
                }
                OutlinedButton(onClick={onRevoke(session)},enabled=!state.isWorking,modifier=Modifier.fillMaxWidth()) { Text("Revocar sesión") }
            }
            if(state.isWorking) Text("ORION está trabajando…",style=MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            state.lastCommitSha?.let { Text("Commit creado: $it",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall) }
            state.pullRequestUrl?.let { Text("Pull request draft creado: $it",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall) }
            if (session != null && state.lastCommitSha != null && state.pullRequestUrl == null) {
                Button(
                    onClick = {
                        onCreatePullRequest(
                            session,
                            "Cambios ORION",
                            """Cambios preparados por ORION.
Commit: ${state.lastCommitSha}
Revisa el diff y los resultados de CI antes de fusionar.""",
                            true
                        )
                    },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Crear pull request draft y ejecutar CI") }
            }
            if(prepared!=null&&session!=null) {
                Text("Propuesta para revisión",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Text(prepared.proposal.title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                Text(prepared.proposal.summary,style=MaterialTheme.typography.bodySmall)
                Text("Archivos: ${prepared.proposal.changes.size} · ${prepared.validation.totalContentBytes} bytes · huella ${prepared.diffDigest.take(16)}",style=MaterialTheme.typography.labelSmall)
                prepared.validation.warnings.forEach { Text("Revisión reforzada: $it",color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall) }
                Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    prepared.proposal.changes.forEach { change ->
                        val original=prepared.snapshot.files.firstOrNull{it.path==change.path}
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("${change.operation} · ${change.path}",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
                            if(change.operation!=CodeFileOperation.CREATE) {
                                Text("VERSIÓN ACTUAL",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
                                Text(original?.content.orEmpty(),style=MaterialTheme.typography.bodySmall)
                            }
                            if(change.operation!=CodeFileOperation.DELETE) {
                                Text("PROPUESTA",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                Text(change.content.orEmpty(),style=MaterialTheme.typography.bodySmall)
                            } else Text("El archivo se eliminará.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(diffConfirmed,{diffConfirmed=it})
                    Text("He revisado el contenido completo del diff y autorizo exactamente estos cambios.",style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick={onCommit(session,prepared.diffDigest,true)},enabled=!state.isWorking&&diffConfirmed,modifier=Modifier.fillMaxWidth()) {
                    Text("Aprobar diff y aplicar en la rama")
                }
            }
        }
    }
}
