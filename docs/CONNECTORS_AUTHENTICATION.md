# ORION connector authentication

## GitHub — implemented authentication foundation

ORION uses the GitHub App Device Flow, intended for native devices without an embedded client secret.

Setup required outside the repository:

1. Create a GitHub App under the intended GitHub account or organization.
2. Enable **Device Flow** in that OAuth App's settings.
3. Configure its public Client ID in the local build as `ORION_GITHUB_CLIENT_ID` (Gradle property or environment variable). The Client ID is not a secret; never add a client secret to the APK or repository.
4. Build and install the app.

Configure fine-grained GitHub App repository permissions and select only the repositories ORION should access during installation. The Device Flow requests `offline_access` only to receive expiring user access and refresh tokens; GitHub App permissions, not OAuth scopes, determine repository access. Start with the minimum permissions required and add write permissions only when the corresponding confirmed actions are implemented.

The authenticator requests a short-lived device code, displays a user code/verification URL to the app layer, polls using GitHub's server-provided interval, handles pending/slow-down/denial/expiry, and returns an access token only after approval. Device codes must remain in memory. The credential manager writes the token through the Android Keystore-backed credential store.

Access tokens expire after a short period and the credential manager supports refresh-token rotation. Disconnect removes both tokens from local encrypted storage; users can also revoke the GitHub App from their GitHub account settings.

## Gmail — Google Identity authorization implemented

The app now uses Google's native Android Authorization API through Google Identity Services. It requests only `https://www.googleapis.com/auth/gmail.readonly` for the first iteration, handles the Google consent PendingIntent through the Android Activity Result API, stores the short-lived access token using the Keystore-backed store, and revokes Google access plus removes the local credential on disconnect.

Required setup outside the repository:

1. Create/select a Google Cloud project and enable the Gmail API.
2. Configure the OAuth consent screen and add the required test users while the app is in testing.
3. Create an Android OAuth client matching package `com.aistudio.orionvoicelab.kxrqmw` and the SHA-1 signing certificate used by the installed build.
4. Complete Google's verification process before public use. `gmail.readonly` is a restricted Gmail scope and is subject to Google's additional verification requirements.

No Google client secret is embedded in the APK. No refresh token is stored; the Android client obtains short-lived access tokens and must request authorization again when a token expires. Gmail draft/send scopes are not requested in this phase.

The current implementation covers authorization and secure local token lifecycle only. Gmail API message search/read operations are a separate next phase and must not be presented as working until their adapter is implemented.

## Common security rules

- No access token, device code, authorization header, email body or private repository content in logs, prompts, crash reports or diagnostics.
- The LLM never receives credentials. It may receive only sanitized action results needed to answer the user.
- Read operations are executed only after a clear user request.
- Sending email, creating or modifying remote resources, publishing comments, and deleting content require a contextual confirmation immediately before execution.
- Connector failures must not stop ASR, local LLM, TTS, memory, tasks or phone functions.
- Network calls use finite connect/read timeouts and run off the main thread.
