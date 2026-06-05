# Google Play GitHub Automation

This repository uploads a new Android App Bundle to Google Play whenever `versionCode` changes on `main`.

The workflow is `.github/workflows/google-play-internal.yml`. It builds the release bundle, signs it with the upload key from GitHub secrets, runs the auth release verifier, and publishes with Gradle Play Publisher.

## Default behavior

- Push to `main`: uploads only when `app/build.gradle.kts` has a different `versionCode` than the previous pushed commit.
- Manual run: open **Actions > Upload Android release to Google Play > Run workflow** and choose `internal`, `alpha`, `beta`, or `production`.
- Default track: `internal`.

## Required GitHub secrets

Add these in **GitHub > LoanTracker > Settings > Secrets and variables > Actions > Repository secrets**.

| Secret | Value |
| --- | --- |
| `ANDROID_UPLOAD_KEYSTORE_BASE64` | Base64 text of the local upload keystore file. |
| `ANDROID_UPLOAD_KEYSTORE_PASSWORD` | Upload keystore password. |
| `ANDROID_UPLOAD_KEY_ALIAS` | Upload key alias, currently `key0`. |
| `ANDROID_UPLOAD_KEY_PASSWORD` | Upload key password. |
| `ANDROID_PUBLISHER_CREDENTIALS` | Raw JSON contents of the Google Play service account key. |

Create the keystore secret from this project root on Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("key")) | Set-Clipboard
```

Paste the clipboard value into `ANDROID_UPLOAD_KEYSTORE_BASE64`.

## Google Play service account

1. In Google Cloud, enable the **Google Play Android Developer API**.
2. Create a service account and JSON key.
3. In Play Console, invite the service account email under **Users and permissions**.
4. Grant this app access to the target release track. For the default workflow, grant access to the internal testing track.
5. Paste the JSON key contents into `ANDROID_PUBLISHER_CREDENTIALS`.

Keep the JSON key and upload keystore out of Git. They are intentionally consumed only from GitHub secrets.

## Publishing a new version

1. Increase `versionCode` in `app/build.gradle.kts`.
2. Update `versionName` if needed.
3. Commit and push to `main`.
4. Watch the GitHub Actions run.
5. The new bundle appears on the configured Google Play track.

Play rejects duplicate or lower `versionCode` values, so every published build must use a strictly higher `versionCode`.
