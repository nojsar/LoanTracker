package com.nojus.loantracker.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val webClientId: String,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun currentUserFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Must be called with an Activity context - the credential picker UI requires it. */
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = runCatching {
        val credentialManager = CredentialManager.create(activity)

        // For an explicit "Continue with Google" button we use the Sign in with Google
        // flow. Unlike GetGoogleIdOption (a returning-user bottom sheet that can throw
        // USER_CANCELED on some OEM devices), this reliably shows the account chooser on
        // every device and Android version.
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        // GMS occasionally fails the first token fetch right after install with a transient
        // error like "[28404] Failed to retrieve an ID token". Retry once after a short delay.
        val response = try {
            credentialManager.getCredential(activity, request)
        } catch (e: GetCredentialCustomException) {
            Log.w(TAG, "Transient credential error (${e.type}), retrying once", e)
            delay(600)
            credentialManager.getCredential(activity, request)
        }
        val idToken = extractIdToken(response)

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user ?: error("Firebase user was null after sign-in")
        // Do not fail sign-in if profile write fails, for example when Firestore rules
        // have not been published yet.
        runCatching { persistProfile(user) }
            .onFailure { Log.w(TAG, "persistProfile failed", it) }
        user
    }.onFailure { Log.e(TAG, "Google sign-in failed", it) }

    private fun extractIdToken(response: GetCredentialResponse): String {
        val credential = response.credential
        require(credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private suspend fun persistProfile(user: FirebaseUser) {
        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty().lowercase(),
            displayName = user.displayName.orEmpty(),
            photoUrl = user.photoUrl?.toString().orEmpty()
        )
        firestore.collection("users").document(user.uid).set(profile).await()
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    companion object {
        private const val TAG = "AuthRepository"

        fun humanizeError(t: Throwable): String =
            AuthFailureMessages.messageFor(classify(t), t.localizedMessage)

        /** Raw developer-facing detail shown only in debug builds. */
        fun diagnostic(t: Throwable): String = buildString {
            append(t.javaClass.simpleName)
            (t as? GetCredentialException)?.type?.let { append(" [").append(it).append("]") }
            t.message?.let { append(": ").append(it) }
        }

        private fun classify(t: Throwable): AuthFailureKind = when (t) {
            is GetCredentialCancellationException -> AuthFailureKind.UserCanceled
            is NoCredentialException -> AuthFailureKind.NoGoogleAccount
            is GetCredentialException -> AuthFailureKind.CredentialManager
            else -> AuthFailureKind.Unknown
        }
    }
}
