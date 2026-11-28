package com.orion.voicelab.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orion.voicelab.connectors.AndroidKeystoreConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorCatalog
import com.orion.voicelab.connectors.ConnectorDescriptor
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.orion.voicelab.connectors.gmail.GmailCredentialManager
import com.orion.voicelab.connectors.github.GitHubCredentialManager
import com.orion.voicelab.connectors.github.GitHubDeviceAuthorizationResult
import com.orion.voicelab.connectors.github.GitHubDeviceFlowAuthenticator
import kotlinx.coroutines.launch

@Composable
fun ConnectorsSectionScreen(
    viewModel: VoiceLabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val codeEngineeringState by viewModel.codeEngineeringState.collectAsStateWithLifecycle()
    val credentialStore = remember(context) { AndroidKeystoreConnectorCredentialStore(context) }
    val githubCredentials = remember(credentialStore) { GitHubCredentialManager(credentialStore) }
    val githubAuth = remember { GitHubDeviceFlowAuthenticator() }
    val gmailCredentials = remember(credentialStore, context) {
        GmailCredentialManager(credentialStore, silentRefresh = {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GMAIL_READONLY_SCOPE)))
                .build()
            val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request))
            if (result.hasResolution()) null else {
                val token = result.accessToken.orEmpty()
                if (token.isBlank()) null else GmailCredentialManager.RefreshedToken(
                    token,
                    result.tokenResponseParams?.getString("expires_in")?.toLongOrNull() ?: 3_600L
                )
            }
        })
    }
    var gmailConnected by remember { mutableStateOf(false) }
    var gmailBusy by remember { mutableStateOf(false) }
    var gmailStatus by remember { mutableStateOf("No conectado") }

    val saveGmailAuthorization: (AuthorizationResult) -> Unit = { authorizationResult ->
        scope.launch {
            try {
                val accessToken = authorizationResult.accessToken.orEmpty()
                require(accessToken.isNotBlank()) { "Google no devolvió un token de acceso." }
                val expiresIn = authorizationResult.tokenResponseParams
                    ?.getString("expires_in")
                    ?.toLongOrNull()
                    ?: 3_600L
                gmailCredentials.save(accessToken, expiresIn)
                gmailConnected = true
                gmailStatus = "Gmail autorizado para lectura. ORION renovará el token silenciosamente cuando Google lo permita."
            } catch (_: Exception) {
                gmailConnected = false
                gmailStatus = "No se pudo guardar la autorización de Gmail."
            } finally {
                gmailBusy = false
            }
        }
    }

    val gmailAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data!!)
            }.onSuccess(saveGmailAuthorization)
                .onFailure {
                    gmailBusy = false
                    gmailStatus = "Google no pudo completar la autorización."
                }
        } else {
            gmailBusy = false
            gmailStatus = "Autorización de Gmail cancelada."
        }
    }
    var githubConnected by remember { mutableStateOf(false) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubUserCode by remember { mutableStateOf<String?>(null) }
    var githubStatus by remember { mutableStateOf("No conectado") }

    LaunchedEffect(gmailCredentials) {
        gmailConnected = runCatching { gmailCredentials.isConnected() }.getOrDefault(false)
        gmailStatus = if (gmailConnected) "Token de lectura guardado de forma cifrada" else "No conectado"
    }

    LaunchedEffect(githubCredentials) {
        githubConnected = runCatching { githubCredentials.isConnected() }.getOrDefault(false)
        githubStatus = if (githubConnected) "Token guardado de forma cifrada" else "No conectado"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Conectores de ORION",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Servicios externos independientes del núcleo offline. La conexión a Internet solo se utilizará cuando actives un proveedor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Seguridad: las credenciales se almacenan cifradas con Android Keystore. Enviar, publicar, modificar o borrar contenido requerirá confirmación.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(ConnectorCatalog.planned, key = { it.id.name }) { connector ->
            if (connector.id.name == "GMAIL") {
                GmailConnectorCard(
                    connector = connector,
                    connected = gmailConnected,
                    busy = gmailBusy,
                    status = gmailStatus,
                    onConnect = {
                        gmailBusy = true
                        gmailStatus = "Solicitando permiso de lectura a Google…"
                        val request = AuthorizationRequest.builder()
                            .setRequestedScopes(
                                listOf(Scope(GMAIL_READONLY_SCOPE))
                            )
                            .build()
                        try {
                            Identity.getAuthorizationClient(context)
                                .authorize(request)
                                .addOnSuccessListener { authorizationResult ->
                                    if (authorizationResult.hasResolution()) {
                                        val pendingIntent = authorizationResult.pendingIntent
                                        if (pendingIntent == null) {
                                            gmailBusy = false
                                            gmailStatus = "Google no proporcionó la pantalla de autorización."
                                        } else {
                                            runCatching {
                                                gmailAuthorizationLauncher.launch(
                                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                                )
                                            }.onFailure {
                                                gmailBusy = false
                                                gmailStatus = "No se pudo abrir la autorización de Google."
                                            }
                                        }
                                    } else {
                                        saveGmailAuthorization(authorizationResult)
                                    }
                                }
                                .addOnFailureListener {
                                    gmailBusy = false
                                    gmailStatus = "No se pudo iniciar la autorización de Gmail."
                                }
                        } catch (_: Exception) {
                            gmailBusy = false
                            gmailStatus = "Google Identity no está disponible en este dispositivo."
                        }
                    },
                    onDisconnect = {
                        gmailBusy = true
                        try {
                            Identity.getAuthorizationClient(context)
                                .revokeAccess(RevokeAccessRequest.builder().build())
                                .addOnCompleteListener { revokeTask ->
                                    scope.launch {
                                        runCatching { gmailCredentials.disconnect() }
                                        gmailConnected = false
                                        gmailBusy = false
                                        gmailStatus = if (revokeTask.isSuccessful) {
                                            "Acceso de Google revocado y credencial local eliminada."
                                        } else {
                                            "Credencial local eliminada; no se pudo confirmar la revocación en Google."
                                        }
                                    }
                                }
                        } catch (_: Exception) {
                            scope.launch {
                                runCatching { gmailCredentials.disconnect() }
                                gmailConnected = false
                                gmailBusy = false
                                gmailStatus = "Credencial local eliminada; Google Identity no pudo confirmar la revocación."
                            }
                        }
                    }
                )
            } else if (connector.id.name == "GITHUB") {
                GitHubConnectorCard(
                    connector = connector,
                    configured = githubAuth.isConfigured(),
                    connected = githubConnected,
                    busy = githubBusy,
                    userCode = githubUserCode,
                    status = githubStatus,
                    onConnect = {
                        scope.launch {
                            githubBusy = true
                            githubUserCode = null
                            try {
                                githubStatus = "Solicitando código seguro a GitHub…"
                                val authorization = githubAuth.requestAuthorization()
                                if (authorization.isFailure) {
                                    githubStatus = authorization.exceptionOrNull()?.message
                                        ?: "No se pudo iniciar la autorización."
                                    return@launch
                                }

                                val deviceAuthorization = authorization.getOrThrow()
                                githubUserCode = deviceAuthorization.userCode
                                githubStatus = "Introduce el código en GitHub para autorizar ORION."
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(deviceAuthorization.verificationUri)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    githubStatus = "Abre github.com/login/device e introduce el código."
                                }

                                when (val result = githubAuth.awaitAuthorization(deviceAuthorization)) {
                                    is GitHubDeviceAuthorizationResult.Authorized -> {
                                        githubCredentials.save(result)
                                        githubConnected = true
                                        githubStatus = "GitHub App autorizada. ORION puede leer repositorios y aplicar cambios revisados en ramas de trabajo, además de abrir pull requests draft."
                                        githubUserCode = null
                                    }
                                    GitHubDeviceAuthorizationResult.AccessDenied -> {
                                        githubStatus = "Autorización rechazada en GitHub."
                                        githubUserCode = null
                                    }
                                    GitHubDeviceAuthorizationResult.Expired -> {
                                        githubStatus = "El código ha caducado. Puedes iniciar otra conexión."
                                        githubUserCode = null
                                    }
                                    is GitHubDeviceAuthorizationResult.Failed -> {
                                        githubStatus = result.message
                                        githubUserCode = null
                                    }
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                githubStatus = "No se pudo completar la conexión. Comprueba Internet e inténtalo de nuevo."
                                githubUserCode = null
                            } finally {
                                githubBusy = false
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            val removed = runCatching { githubCredentials.disconnect() }.isSuccess
                            githubConnected = false
                            githubUserCode = null
                            githubStatus = if (removed) {
                                "GitHub desconectado. Credencial local eliminada."
                            } else {
                                "No se pudo eliminar la credencial local de GitHub."
                            }
                        }
                    }
                )
            } else if (connector.id.name == "ANDROID_NOTES") {
                GoogleKeepConnectorCard()
            } else if (connector.id.name == "ANDROID_CALENDAR") {
                AndroidCalendarConnectorCard()
            } else if (connector.id.name == "GOOGLE_MAPS") {
                GoogleMapsConnectorCard()
            } else if (connector.id.name == "PESCAIA") {
                PescaIAConnectorCard()
            } else {
                ConnectorProviderCard(connector)
            }
        }
        item {
            CodeEngineeringCard(
                state = codeEngineeringState,
                onStartSession = { repository, base, branch, task, confirmed ->
                    viewModel.startCodeEngineeringSession(repository, base, branch, task, confirmed)
                },
                onPrepare = { session, task, paths ->
                    viewModel.prepareCodeEngineeringChange(session, task, paths)
                },
                onCommit = { session, digest, confirmed ->
                    viewModel.commitApprovedCodeEngineeringChange(session, digest, confirmed)
                },
                onRevoke = { session -> viewModel.revokeCodeEngineeringSession(session) }
            )
        }
    }
}

@Composable
private fun GmailConnectorCard(
    connector: ConnectorDescriptor,
    connected: Boolean,
    busy: Boolean,
    status: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(connector.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (connected) "Autorizado" else "Google Identity",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }
            Text(connector.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Permiso inicial: lectura de mensajes. No se solicitan permisos para enviar ni modificar correo. Google puede exigir verificación de la app para este alcance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Esperando autorización de Google…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (connected) {
                OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Desconectar Gmail")
                }
            } else {
                Button(onClick = onConnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Autorizando…" else "Conectar Gmail")
                }
            }
        }
    }
}

private const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

@Composable
private fun GitHubConnectorCard(
    connector: ConnectorDescriptor,
    configured: Boolean,
    connected: Boolean,
    busy: Boolean,
    userCode: String?,
    status: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(connector.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (connected) "Autorizado" else "GitHub App Device Flow",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }
            Text(connector.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Los permisos efectivos los define la GitHub App y los repositorios que selecciones al instalarla. ORION no solicita scopes amplios de OAuth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (userCode != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Código de autorización", style = MaterialTheme.typography.labelMedium)
                        Text(userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Escribe este código en github.com/login/device", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("Esperando autorización de GitHub…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (connected) {
                OutlinedButton(onClick = onDisconnect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Desconectar GitHub")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = configured && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Esperando autorización…" else "Conectar GitHub")
                }
                if (!configured) {
                    Text(
                        "Falta ORION_GITHUB_CLIENT_ID. Consulta docs/CONNECTORS_AUTHENTICATION.md para configurar la GitHub App.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorProviderCard(
    connector: ConnectorDescriptor
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = connector.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Próximamente",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Text(
                text = connector.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Permisos previstos: " + connector.capabilities.joinToString(" · ") {
                    when (it.name) {
                        "SEARCH" -> "Buscar"
                        "READ" -> "Leer"
                        "CREATE_DRAFT" -> "Borradores"
                        "SEND" -> "Enviar con confirmación"
                        "CREATE" -> "Crear con confirmación"
                        "UPDATE" -> "Modificar con confirmación"
                        "DELETE" -> "Borrar con confirmación"
                        "COMMENT" -> "Comentar con confirmación"
                        else -> it.name
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
