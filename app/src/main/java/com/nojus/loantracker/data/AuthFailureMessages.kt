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
            "Google sign-in was canceled before an account was selected. " +
                "If this happens every time, update the app or contact support."
        AuthFailureKind.NoGoogleAccount ->
            "Couldn't sign in: no Google account is available to Credential Manager on this device. " +
                "Make sure you're signed into a Google account in Settings > Passwords & accounts."
        AuthFailureKind.CredentialManager ->
            "Google sign-in failed before an account reached the app. " +
                "Make sure Google Play services is up to date, then try again."
        AuthFailureKind.Unknown ->
            fallback?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."
    }
}
