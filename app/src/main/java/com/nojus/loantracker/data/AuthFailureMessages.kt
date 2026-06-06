package com.nojus.loantracker.data

internal enum class AuthFailureKind {
    UserCanceled,
    NoGoogleAccount,
    CredentialManager,
    Unknown
}

internal object AuthFailureMessages {
    fun messageFor(
        kind: AuthFailureKind,
        fallback: String? = null
    ): String = when (kind) {
        AuthFailureKind.UserCanceled ->
            "No Google account was selected. Tap Continue with Google and choose an account."
        AuthFailureKind.NoGoogleAccount ->
            "No usable Google sign-in credential was found. Add a Google account in Settings > Passwords & accounts. " +
                "If one is already there, register this build's SHA fingerprints in Firebase, then try again."
        AuthFailureKind.CredentialManager ->
            "Google sign-in failed before an account reached the app. " +
                "Make sure Google Play services is up to date, then try again."
        AuthFailureKind.Unknown ->
            fallback?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."
    }
}
