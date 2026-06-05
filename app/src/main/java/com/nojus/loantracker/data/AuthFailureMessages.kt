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
            "No Google account is available on this device. Add one in Settings > Passwords & accounts, then try again."
        AuthFailureKind.CredentialManager ->
            "Google sign-in failed before an account reached the app. " +
                "Make sure Google Play services is up to date, then try again."
        AuthFailureKind.Unknown ->
            fallback?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."
    }
}
