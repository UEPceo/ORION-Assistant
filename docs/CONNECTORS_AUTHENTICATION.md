# ORION connector authentication

## GitHub — implemented authentication foundation

ORION uses the GitHub OAuth App Device Flow, intended for native devices without an embedded client secret.

Setup required outside the repository:

1. Create a GitHub OAuth App under the intended GitHub account or organization.
2. Enable **Device Flow** in that OAuth App's settings.
3. Configure its public Client ID in the local build as `ORION_GITHUB_CLIENT_ID` (Gradle property or environment variable). The Client ID is not a secret; never add a client secret to the APK or repository.
4. Build and install the app.

The current first-pass scope is `read:user` only. Public repository metadata can be queried without expanding OAuth permissions. Private repositories require a separate permission review and a new authorization grant; do not request broader scopes until that feature is implemented and explicitly enabled.

The authenticator requests a short-lived device code, displays a user code/verification URL to the app layer, polls using GitHub's server-provided interval, handles pending/slow-down/denial/expiry, and returns an access token only after approval. Device codes must remain in memory. The credential manager writes the token through the Android Keystore-backed credential store.

Disconnect deletes the locally stored token. The next hardening step is to add provider-side token revocation where supported and a token validity check before enabling GitHub actions.

## Gmail — required Google authorization configuration

Gmail must use Google's native Android authorization flow (Google Identity Services / Authorization API) with a Google Cloud project, an Android OAuth client matching the app package and signing certificate, and a separately configured OAuth consent screen.

Do not use a client secret in the Android app. Do not substitute a generic embedded webview or a password-based flow. Request only the Gmail scopes required by each feature; start with read-only access for search/read, and request draft/send scopes only when those features are enabled and explained to the user.

The repository does not yet contain a configured Google Cloud project/client ID or signing-certificate fingerprints, so Gmail authorization cannot be activated safely in this commit. Its adapter must remain unavailable until those values are provisioned.

## Common security rules

- No access token, device code, authorization header, email body or private repository content in logs, prompts, crash reports or diagnostics.
- The LLM never receives credentials. It may receive only sanitized action results needed to answer the user.
- Read operations are executed only after a clear user request.
- Sending email, creating or modifying remote resources, publishing comments, and deleting content require a contextual confirmation immediately before execution.
- Connector failures must not stop ASR, local LLM, TTS, memory, tasks or phone functions.
- Network calls use finite connect/read timeouts and run off the main thread.
