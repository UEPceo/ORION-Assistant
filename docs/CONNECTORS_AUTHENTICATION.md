# ORION connector authentication

## GitHub — implemented authentication foundation

ORION uses the GitHub App Device Flow, intended for native devices without an embedded client secret.

Setup required outside the repository:

1. Create a GitHub App under the intended GitHub account or organization.
2. Enable **Device Flow** in that GitHub App's settings.
3. Configure its public Client ID as `ORION_GITHUB_CLIENT_ID` (Gradle property or environment variable). The Client ID is not a secret; never add a client secret to the APK or repository.
4. For GitHub Actions, set repository variable or secret `ORION_GITHUB_CLIENT_ID`; `build-apk.yml` passes it into Gradle. CI stops early if it is missing, rather than producing an APK with a broken connector.
5. Build and install the app.

Configure fine-grained GitHub App repository permissions and select only the repositories ORION should access during installation. The read-only integration requires repository metadata read, Issues read, Contents read and Actions read (for CI/workflow status). The Device Flow requests `offline_access` only to receive expiring user access and refresh tokens; GitHub App permissions, not OAuth scopes, determine repository access. Do not grant write permissions in this phase.

The authenticator requests a short-lived device code, displays a user code/verification URL to the app layer, polls using GitHub's server-provided interval, handles pending/slow-down/denial/expiry, and returns an access token only after approval. Device codes must remain in memory. The credential manager writes the token through the Android Keystore-backed credential store.

Access tokens expire after a short period and the credential manager serializes refresh-token rotation with a Mutex. A network failure during refresh is reported as a transient connection failure, not as an expired token; only an invalid/expired refresh grant requires authorization again. Disconnect removes both tokens from local encrypted storage; users can also revoke the GitHub App from their GitHub account settings. `/user/repos` returns repositories visible to the authenticated GitHub App user token and its installations/permissions; it must not be interpreted as a guarantee that every repository in the user's account is visible.

## Gmail — Google Identity authorization implemented

The app now uses Google's native Android Authorization API through Google Identity Services. It requests only `https://www.googleapis.com/auth/gmail.readonly`, handles the Google consent PendingIntent through the Android Activity Result API, stores the short-lived access token using the Keystore-backed store, and revokes Google access plus removes the local credential on disconnect. When a token expires, the connector silently calls `authorize()` again; if prior consent remains valid, Google returns a fresh token without UI. If Google requires an interactive resolution or the network is unavailable, the request fails safely and the user can reconnect from Settings.

Required setup outside the repository:

1. Create/select a Google Cloud project and enable the Gmail API.
2. Configure the OAuth consent screen and add the required test users while the app is in testing.
3. Create an Android OAuth client matching package `com.aistudio.orionvoicelab.kxrqmw` and the SHA-1 signing certificate used by the installed build. For CI, configure the same stable debug keystore for every run using repository secrets `ORION_DEBUG_KEYSTORE_BASE64`, `ORION_DEBUG_STORE_PASSWORD`, `ORION_DEBUG_KEY_ALIAS` and `ORION_DEBUG_KEY_PASSWORD`. The workflow decodes this keystore and prints its certificate fingerprint with `keytool`; register that SHA-1 in Google Cloud. Do not use the ephemeral runner-generated debug key.
4. Complete Google's verification process before public use. `gmail.readonly` is a restricted Gmail scope and is subject to Google's additional verification requirements.

No Google client secret is embedded in the APK. No refresh token is stored; the Android client obtains short-lived access tokens and must request authorization again when a token expires. Gmail draft/send scopes are not requested in this phase.

Gmail API integration supports bounded message search and metadata-only lookup (sender, subject and date). Message bodies and snippets are deliberately excluded from connector results, conversation history and LLM prompts to prevent indirect prompt injection. Attachments are not downloaded; drafts and sending are not implemented.

## Common security rules

- No access token, device code, authorization header, email body or private repository content in logs, prompts, crash reports or diagnostics.
- The LLM never receives credentials or Gmail message bodies/snippets. It may receive only bounded metadata results needed to answer the user.
- Read operations are executed only after a clear user request; follow-up requests may reuse the repository selected in the immediately preceding connector interaction.
- GitHub read-only operations include repositories, issues and issue details, commits, repository file listings and Actions workflow runs.
- Gmail read-only operations include advanced message search and metadata lookup; email bodies and snippets are not passed into the assistant conversation.
- Sending email, creating or modifying remote resources, publishing comments, and deleting content require a contextual, one-shot confirmation bound to the exact action and expiring after 60 seconds. A refusal or unrelated next request clears the pending confirmation.
- Connector failures must not stop ASR, local LLM, TTS, memory, tasks or phone functions.
- Network calls use finite connect/read timeouts and run off the main thread.


## CI secrets and stable debug certificate

The debug APK workflow requires:
- `ORION_GITHUB_CLIENT_ID` as a repository variable or secret.
- `ORION_DEBUG_KEYSTORE_BASE64` containing the base64-encoded stable debug keystore.
- `ORION_DEBUG_STORE_PASSWORD`, `ORION_DEBUG_KEY_ALIAS` and `ORION_DEBUG_KEY_PASSWORD`.

The keystore is used only to sign CI debug APKs. Keep it private in GitHub Actions secrets. The package name and SHA-1 fingerprint must match the Android OAuth client registered in Google Cloud. Release signing remains a separate certificate and needs its own Google Android OAuth client fingerprint if Gmail is enabled in release builds.
