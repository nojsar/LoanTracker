package com.nojus.loantracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFailureMessagesTest {
    @Test
    fun cancellationMessageIsUserSafe() {
        val message = AuthFailureMessages.messageFor(AuthFailureKind.UserCanceled)

        assertTrue(message.contains("Google sign-in"))
        assertFalse(message.contains("GetCredential"))
        assertFalse(message.contains("android.credentials"))
    }

    @Test
    fun noGoogleAccountMessageGivesSettingsHint() {
        val message = AuthFailureMessages.messageFor(AuthFailureKind.NoGoogleAccount)

        assertTrue(message.contains("Settings > Passwords & accounts"))
    }

    @Test
    fun unknownMessageUsesFallbackWhenPresent() {
        assertEquals(
            "Network unavailable",
            AuthFailureMessages.messageFor(AuthFailureKind.Unknown, "Network unavailable")
        )
    }
}
