# ORION connector authentication

## GitHub — implemented authentication foundation

ORION uses the GitHub App Device Flow, intended for native devices without an embedded client secret.

Setup required outside the repository:

1. Create a GitHub App under the intended GitHub account or organization.
2. Enable **Device Flow** in that GitHub App's settings.
3. Configure its public Client ID as `ORION_GITHUB_CLIENT_ID` (Gradle property or environment variable). The Client ID is not a secret; never add a client secret to the APK or repository.
4. For GitHub Actions, set repository variable or secret `ORION_GITHUB_CLIENT_ID`; `build-apk.yml` passes it into Gradle when present. A missing ID no longer blocks compilation, but the GitHub connector reports that it is not configured and cannot authorize users until the value is supplied.
5. Build and install the app.

Configure the GitHub App with the following permissions for ORION's software-engineering capability:

| Permission | Access | Purpose |
|---|---|---|
| Metadata | Read-only | Resolve repositories and refs |
| Contents | Read and write | Read source, create blobs/trees and commit code |
| Pull requests | Read and write | Open and update pull requests |
| Issues | Read and write | Read issues and later create engineering issue comments when explicitly requested |
| Actions | Read and write | Inspect CI runs and request/re-run permitted workflow runs |
| Workflows | Read and write | Modify workflow files when the user explicitly authorizes that change |

Do not grant Administration, Secrets, Environments, Organization administration, Members, or account-level permissions. Repository secrets are never read by ORION. Actions runners must receive only the permissions configured in their workflow; ORION must not enumerate or retrieve secret values.

During installation, choose **All repositories** if ORION should work across all repositories owned by that account, or **Only select repositories** and add each repository ORION may access. Organization repositories can require an organization owner to approve the app and its permissions. A repository not selected for the installation remains inaccessible even if the app has write permission types.

The Device Flow requests `offline_access` only to receive expiring user access and refresh tokens; GitHub App installation permissions determine actual repository access. After changing App permissions, save the changes and approve/update the existing installation when GitHub requests it. ORION may need to be disconnected and authorized again to obtain a token reflecting the updated installation permissions.

The authenticator requests a short-lived device code, displays a user code/verification URL to the app layer, polls using GitHub's server-provided interval, handles pending/slow-down/denial/expiry, and returns an access token only after approval. Device codes must remain in memory. The credential manager writes the token through the Android Keystore-backed credential store.

Access tokens expire after a short period and the credential manager serializes refresh-token rotation with a Mutex. A network failure during refresh is reported as a transient connection failure, not as an expired token; only an invalid/expired refresh grant requires authorization again. Disconnect removes both tokens from local encrypted storage; users can also revoke the GitHub App from their GitHub account settings. `GET /user/repos` is explicitly supported for GitHub App user access tokens. It returns repositories the authenticated user has explicit read/write/admin access to (owned, collaborator and organization-member repositories); ORION requests `visibility=all` and all three `affiliation` values. GitHub requires repository Metadata read permission for private repository metadata. Results are paginated (ORION returns 30 per page and accepts `página 2`, etc.); this endpoint does not expose repositories the user cannot access through the app/organization permissions.

## Gmail — Google Identity authorization implemented

The app now uses Google's native Android Authorization API through Google Identity Services. It requests only `https://www.googleapis.com/auth/gmail.readonly`, handles the Google consent PendingIntent through the Android Activity Result API, stores the short-lived access token using the Keystore-backed store, and revokes Google access plus removes the local credential on disconnect. When a token expires, the connector silently calls `authorize()` again; if prior consent remains valid, Google returns a fresh token without UI. If Google requires an interactive resolution or the network is unavailable, the request fails safely and the user can reconnect from Settings.

Required setup outside the repository:

1. Create/select a Google Cloud project and enable the Gmail API.
2. Configure the OAuth consent screen and add the required test users while the app is in testing.
3. Create an Android OAuth client matching package `com.aistudio.orionvoicelab.kxrqmw` and the SHA-1 signing certificate used by the installed build. For CI, configure the same stable debug keystore for every run using repository secrets `ORION_DEBUG_KEYSTORE_BASE64`, `ORION_DEBUG_STORE_PASSWORD`, `ORION_DEBUG_KEY_ALIAS` and `ORION_DEBUG_KEY_PASSWORD`. The workflow decodes this keystore and prints its certificate fingerprint with `keytool`; register that SHA-1 in Google Cloud. If these secrets are absent, CI still builds a debug APK using Gradle's runner-generated key, but Gmail OAuth will not work in that artifact because its SHA-1 is not stable. Do not distribute that fallback APK as a Gmail-enabled test build.
4. Complete Google's verification process before public use. `gmail.readonly` is a restricted Gmail scope and is subject to Google's additional verification requirements.

No Google client secret is embedded in the APK. No refresh token is stored by ORION. When the short-lived access token expires, the Android client calls Google's `authorize()` again; if previous consent is still valid, Google returns a fresh token without showing the consent UI. If a new interactive resolution is required, the user must reconnect from Settings. Gmail draft/send scopes are not requested in this phase.

Gmail API integration supports bounded message search and metadata-only lookup (sender, subject and date). Message bodies and snippets are deliberately excluded from connector results, conversation history and LLM prompts to prevent indirect prompt injection. Attachments are not downloaded; drafts and sending are not implemented.

## Common security rules

- No access token, device code, authorization header, email body or private repository content in logs, prompts, crash reports or diagnostics.
- The LLM never receives credentials or Gmail message bodies/snippets. It may receive only bounded metadata results needed to answer the user.
- Read operations are executed only after a clear user request; follow-up requests may reuse the repository selected in the immediately preceding connector interaction.
- GitHub read operations include repositories, issues and issue details, commits, repository file listings and Actions workflow runs.
- GitHub write operations include creating a work branch, applying a validated multi-file patch as one Git commit, and opening a draft pull request. Every write is classified as a reversible write and requires a one-shot confirmation bound to the exact operation. The default branch is never a target for direct commits.
- Multi-file commits use the Git Data API. ORION checks the branch head SHA and each existing file blob SHA against the reviewed proposal before creating the tree and commit; it updates the branch ref only after the commit is complete. A changed branch or file invalidates the proposal and requires a fresh review.
- Gmail read-only operations include advanced message search and metadata lookup; email bodies and snippets are not passed into the assistant conversation.
- Sending email, creating or modifying remote resources, publishing comments, and deleting content require a contextual, one-shot confirmation bound to the exact action and expiring after 60 seconds. A refusal or unrelated next request clears the pending confirmation.
- Connector failures must not stop ASR, local LLM, TTS, memory, tasks or phone functions.
- Network calls use finite connect/read timeouts and run off the main thread.


## CI secrets and stable debug certificate

The debug APK workflow accepts these optional configuration values:
- `ORION_GITHUB_CLIENT_ID` as a repository variable or secret. Without it, compilation succeeds but GitHub Device Flow remains disabled.
- `ORION_DEBUG_KEYSTORE_BASE64` containing the base64-encoded stable debug keystore.
- `ORION_DEBUG_STORE_PASSWORD`, `ORION_DEBUG_KEY_ALIAS` and `ORION_DEBUG_KEY_PASSWORD`. Supply all four signing secrets together or leave all four empty. When empty, incomplete, malformed or unusable, the workflow warns and Gradle generates its normal CI debug key; Gmail OAuth is unavailable in that artifact.

The keystore is used only to sign CI debug APKs. Keep it private in GitHub Actions secrets. The package name and SHA-1 fingerprint must match the Android OAuth client registered in Google Cloud. Release signing remains a separate certificate and needs its own Google Android OAuth client fingerprint if Gmail is enabled in release builds.

## Connector hardening and test coverage

- GitHub repository names and file paths reject traversal segments, encoded separators and ambiguous path components. The action gate uses separate read and write allowlists; code writes cannot be labelled read-only to bypass confirmation. Generic placeholders such as `get` or `fetch` are not trusted. Repository listing requests explicit owner, collaborator and organization-member affiliations; actual visibility still follows GitHub App installation and granted metadata permissions.
- GitHub refresh-token rotation is serialized. A transient network failure remains a retryable service failure; only an invalid/expired refresh grant maps to reauthorization.
- Google Maps navigation uses the documented `https://www.google.com/maps/dir/?api=1` URL and `travelmode=transit` for public transport.
- PescaIA redirects are followed manually, limited to five hops, HTTPS-only, standard HTTPS port, and exact apex/www host allowlist. The lightweight reader does not execute JavaScript; if static HTML yields almost no text it returns no result instead of inventing content.
- Automated regression tests cover Gmail silent renewal and metadata-only API results, GitHub refresh concurrency/error classification and repository validation, Maps URL modes and phrase parsing, PescaIA URL allowlisting, and AES-GCM credential persistence/tamper rejection. The Keystore test injects a test AES key to verify the encryption envelope; AndroidKeyStore hardware/provider behavior still requires device-level validation.


The repository's GitHub App Device Flow Client ID and Google Cloud Android OAuth client/certificate registration are owner-controlled provider configuration. GitHub Actions cannot safely create these app registrations or change Google Cloud OAuth clients on the repository owner's behalf. The workflow therefore treats absent values as connector-configuration warnings rather than compile failures.
