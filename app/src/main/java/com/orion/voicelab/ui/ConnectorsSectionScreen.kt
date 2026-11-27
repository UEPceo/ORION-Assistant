package com.orion.voicelab.ui

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
import com.orion.voicelab.connectors.github.GitHubCredentialManager
import com.orion.voicelab.connectors.github.GitHubDeviceAuthorizationResult
import com.orion.voicelab.connectors.github.GitHubDeviceFlowAuthenticator
import kotlinx.coroutines.launch

@Composable
fun ConnectorsSectionScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialStore = remember(context) { AndroidKeystoreConnectorCredentialStore(context) }
    val githubCredentials = remember(credentialStore) { GitHubCredentialManager(credentialStore) }
    val githubAuth = remember { GitHubDeviceFlowAuthenticator() }
    var githubConnected by remember { mutableStateOf(false) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubUserCode by remember { mutableStateOf<String?>(null) }
    var githubStatus by remember { mutableStateOf("No conectado") }

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
            if (connector.id.name == "GITHUB") {
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
                                        githubStatus = "GitHub conectado. Acceso inicial: repositorios públicos."
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
                            githubCredentials.disconnect()
                            githubConnected = false
                            githubUserCode = null
                            githubStatus = "GitHub desconectado. Credencial local eliminada."
                        }
                    }
                )
            } else {
                ConnectorProviderCard(connector)
            }
        }
    }
}

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
                    text = if (connected) "Conectado" else "OAuth Device Flow",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }
            Text(connector.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Permiso solicitado: leer el perfil básico. Los repositorios públicos se consultan sin ampliar permisos; los privados requerirán autorización adicional.",
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
                        "Falta ORION_GITHUB_CLIENT_ID. Consulta docs/CONNECTORS_AUTHENTICATION.md para configurar la OAuth App.",
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
