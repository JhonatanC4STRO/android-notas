package com.jhonatan.notas.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhonatan.notas.data.SessionRepository
import com.jhonatan.notas.data.remote.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LoginUiState())
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        fun onEmailChange(value: String) {
            _uiState.update { it.copy(email = value, errorMessage = null) }
        }

        fun onPasswordChange(value: String) {
            _uiState.update { it.copy(password = value, errorMessage = null) }
        }

        fun login(onSuccess: () -> Unit) {
            val state = _uiState.value
            if (state.email.isBlank() || state.password.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Ingresa tu email y contraseña") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                when (val result = sessionRepository.login(state.email.trim(), state.password)) {
                    is LoginResult.Success -> {
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    }
                    is LoginResult.InvalidCredentials -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Email o contraseña incorrectos") }
                    }
                    is LoginResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                    }
                }
            }
        }
    }
