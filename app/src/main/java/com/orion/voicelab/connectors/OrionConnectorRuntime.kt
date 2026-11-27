package com.orion.voicelab.connectors

import android.content.Context
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

    val gmailCredentials = GmailCredentialManager(credentialStore)
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
