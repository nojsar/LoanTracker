# Google Sign-In Release Fix

## What broke

The broken published build was caused by `app/google-services.json` containing only one Android OAuth SHA-1:

```text
12:99:54:45:DA:C9:CB:DE:C7:EB:52:29:CC:51:62:40:9E:AF:56:C1
```

That fingerprint is the local debug keystore. The Play Store version is signed with a release certificate, so Google Credential Manager cancels before it can return a Google ID token. Firebase never gets a chance to authenticate the user.

The updated Firebase config now also includes this Android OAuth SHA-1:

```text
89:49:49:2C:5A:9B:1C:E8:8B:0D:10:2D:6D:9B:77:AF:A4:36:DE:9A
```

The locally generated upload bundle at `app/release/app-release.aab` is signed with this upload certificate:

```text
SHA-1:   C8:BD:78:FD:26:45:F8:10:8A:68:8E:2A:BE:BF:06:59:71:68:18:E0
SHA-256: 85:D9:DE:F1:62:CA:AF:93:6F:84:FF:63:96:65:CC:2E:81:45:EC:62:82:8F:43:7E:45:C9:FC:78:15:1F:6E:42
```

For Play Store installs, the critical value is the Play app signing certificate, not just this upload certificate.

## Permanent fix

1. Open Play Console.
2. Go to **Test and release > Setup > App signing**.
3. Under **App signing key certificate**, copy the SHA-1 and SHA-256 fingerprints.
4. In Firebase Console, open **Project settings > Your apps > Android app** for package `com.nojus.loantracker`.
5. Add the Play app signing SHA-1 and SHA-256 fingerprints.
6. Also add the upload certificate SHA-1 and SHA-256 above if you install upload-signed builds outside Play.
7. Download the updated `google-services.json`.
8. Replace `app/google-services.json`.
9. Run the verifier. Build and publish a new release only when app-code changes also need to ship.

Firebase's Android Google sign-in guide requires the app SHA fingerprint and an updated `google-services.json`: https://firebase.google.com/docs/auth/android/google-signin

Google documents where to find Play app signing fingerprints in Play Console: https://support.google.com/googleplay/android-developer/answer/9842756

## Verification

Run:

```powershell
.\gradlew.bat :app:verifyGoogleSignInReleaseConfig :app:testDebugUnitTest :app:assembleRelease
```

`verifyGoogleSignInReleaseConfig` fails while `google-services.json` is debug-only. With the updated Firebase config, it passes and release builds will no longer ship with broken Google sign-in.
