package com.nojus.loantracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFailureMessagesTest {
    @Test
    fun cancellationMessageIsUserSafe() {
        val message = AuthFailureMessages.messageFor(AuthFailureKind.UserCanceled)

        assertTrue(message.contains("Google account"))
        assertFalse(message.contains("GetCredential"))
        assertFalse(message.contains("android.credentials"))
        assertFalse(message.contains("Credential Manager"))
    }

    @Test
    fun noGoogleAccountMessageGivesSettingsHint() {
        val message = AuthFailureMessages.messageFor(AuthFailureKind.NoGoogleAccount)

        assertTrue(message.contains("Settings > Passwords & accounts"))
        assertTrue(message.contains("SHA fingerprints"))
        assertTrue(message.contains("Firebase"))
        assertFalse(message.contains("Credential Manager"))
    }

    @Test
    fun unknownMessageUsesFallbackWhenPresent() {
        assertEquals(
            "Network unavailable",
            AuthFailureMessages.messageFor(AuthFailureKind.Unknown, "Network unavailable")
        )
    }
}
