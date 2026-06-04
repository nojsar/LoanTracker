package com.nojus.loantracker.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.nojus.loantracker.R
import com.nojus.loantracker.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val repo: AuthRepository,
    private val diagnosticsEnabled: Boolean = false
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = repo.currentUserFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.currentUser
    )

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** One-shot developer-facing detail for diagnosing silent sign-in failures (shown as a Toast). */
    private val _diagnostic = MutableStateFlow<String?>(null)
    val diagnostic: StateFlow<String?> = _diagnostic.asStateFlow()
    fun clearDiagnostic() { _diagnostic.value = null }

    fun signIn(activity: Activity) {
        _uiState.value = AuthUiState.Loading
        _diagnostic.value = null
        viewModelScope.launch {
            val result = repo.signInWithGoogle(activity)
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Idle },
                onFailure = { t ->
                    if (diagnosticsEnabled) {
                        _diagnostic.value = AuthRepository.diagnostic(t)
                    }
                    AuthUiState.Error(AuthRepository.humanizeError(t))
                }
            )
        }
    }

    fun clearError() { _uiState.value = AuthUiState.Idle }

    fun signOut(context: Context) {
        viewModelScope.launch { repo.signOut(context.applicationContext) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val webClientId = appContext.getString(R.string.default_web_client_id)
                    val diagnosticsEnabled =
                        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(
                        repo = AuthRepository(webClientId),
                        diagnosticsEnabled = diagnosticsEnabled
                    ) as T
                }
            }
    }
}
