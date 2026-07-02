package com.jhonatan.notas.ui.notas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhonatan.notas.data.NotasRepository
import com.jhonatan.notas.domain.Nota
import com.powersync.PowerSyncDatabase
import com.powersync.sync.SyncStatusData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SyncIndicatorState { CONECTADO, SINCRONIZANDO, SIN_CONEXION }

private fun SyncStatusData.toIndicatorState(): SyncIndicatorState =
    when {
        !connected -> SyncIndicatorState.SIN_CONEXION
        uploading || downloading -> SyncIndicatorState.SINCRONIZANDO
        else -> SyncIndicatorState.CONECTADO
    }

@HiltViewModel
class NotasViewModel
    @Inject
    constructor(
        private val notasRepository: NotasRepository,
        powerSyncDatabase: PowerSyncDatabase,
    ) : ViewModel() {
        val notas: StateFlow<List<Nota>> =
            notasRepository
                .observarNotas()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val syncState: StateFlow<SyncIndicatorState> =
            powerSyncDatabase.currentStatus
                .asFlow()
                .map { it.toIndicatorState() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    powerSyncDatabase.currentStatus.toIndicatorState(),
                )

        fun crearNota(
            titulo: String,
            contenido: String,
        ) {
            viewModelScope.launch { notasRepository.crearNota(titulo, contenido) }
        }

        fun editarNota(
            id: String,
            titulo: String,
            contenido: String,
        ) {
            viewModelScope.launch { notasRepository.editarNota(id, titulo, contenido) }
        }

        fun borrarNota(id: String) {
            viewModelScope.launch { notasRepository.borrarNota(id) }
        }
    }
