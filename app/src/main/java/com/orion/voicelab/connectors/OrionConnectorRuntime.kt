package com.orion.voicelab.connectors

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.orion.voicelab.connectors.gmail.GmailApiClient
import com.orion.voicelab.connectors.gmail.GmailCredentialManager
import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubCredentialManager
import com.orion.voicelab.connectors.github.GitHubDeviceFlowAuthenticator

/**
 * Application-scoped connector composition root. Authentication UI and voice actions
 * share the same encrypted credential store and provider managers.
 */
class OrionConnectorRuntime(context: Context) {
    val credentialStore: ConnectorCredentialStore =
        AndroidKeystoreConnectorCredentialStore(context.applicationContext)

    val gmailCredentials = GmailCredentialManager(
        credentialStore = credentialStore,
        silentRefresh = {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/gmail.readonly")))
                .build()
            val result = Tasks.await(
                Identity.getAuthorizationClient(context.applicationContext).authorize(request)
            )
            if (result.hasResolution()) null else {
                val token = result.accessToken.orEmpty()
                if (token.isBlank()) null else GmailCredentialManager.RefreshedToken(
                    accessToken = token,
                    expiresInSeconds = result.tokenResponseParams?.getString("expires_in")?.toLongOrNull() ?: 3_600L
                )
            }
        }
    )
    val githubAuthenticator = GitHubDeviceFlowAuthenticator()
    val githubCredentials = GitHubCredentialManager(credentialStore)

    val registry = ConnectorRegistry(
        listOf(
            GmailOrionConnector(gmailCredentials, GmailApiClient(gmailCredentials)),
            GitHubOrionConnector(
                githubCredentials,
                GitHubApiClient(githubCredentials, githubAuthenticator)
            ),
            GoogleMapsOrionConnector(context.applicationContext),
            PescaIAOrionConnector()
        )
    )
    val dispatcher = ConnectorDispatcher(registry)
}
