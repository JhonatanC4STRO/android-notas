package com.jhonatan.notas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhonatan.notas.data.SessionRepository
import com.jhonatan.notas.sync.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { LOADING, LOGIN, NOTAS }

// Al iniciar la app: si hay una sesion guardada, conecta PowerSync
// automaticamente y arranca directo en "notas" (CLAUDE.md / Paso 7, punto 6).
// Tambien observa a SessionManager para saber si la sesion se cerro sola
// (dispositivo revocado) y avisarle a la UI.
@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        sessionManager: SessionManager,
    ) : ViewModel() {
        private val _startDestination = MutableStateFlow(StartDestination.LOADING)
        val startDestination: StateFlow<StartDestination> = _startDestination.asStateFlow()

        private val _sessionEndedMessage = MutableStateFlow<String?>(null)
        val sessionEndedMessage: StateFlow<String?> = _sessionEndedMessage.asStateFlow()

        init {
            viewModelScope.launch {
                val hasActiveSession = sessionRepository.connectIfSessionExists()
                _startDestination.value = if (hasActiveSession) StartDestination.NOTAS else StartDestination.LOGIN
            }

            viewModelScope.launch {
                sessionManager.sessionEndedMessages.collect { message ->
                    _sessionEndedMessage.value = message
                }
            }
        }

        fun consumeSessionEndedMessage() {
            _sessionEndedMessage.value = null
        }
    }
