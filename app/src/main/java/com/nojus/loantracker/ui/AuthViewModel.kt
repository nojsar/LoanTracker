package com.nojus.loantracker.ui

import android.app.Activity
import android.content.Context
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
    private val repo: AuthRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = repo.currentUserFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.currentUser
    )

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(activity: Activity) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repo.signInWithGoogle(activity)
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Idle },
                onFailure = { t -> AuthUiState.Error(AuthRepository.humanizeError(t)) }
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
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(AuthRepository(webClientId)) as T
                }
            }
    }
}
