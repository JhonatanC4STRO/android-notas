package com.jhonatan.notas.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jhonatan.notas.data.local.TokenStore
import com.powersync.PowerSyncDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Centraliza el ciclo de vida de la sesion (CLAUDE.md seccion 3.5): tanto la
// expulsion por otro dispositivo como un futuro logout explicito pasan por
// aqui, para no duplicar la logica de limpieza en varios lugares.
@Singleton
class SessionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val powerSyncDatabase: PowerSyncDatabase,
        private val tokenStore: TokenStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val handlingRevocation = AtomicBoolean(false)

        private val _sessionEndedMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val sessionEndedMessages: SharedFlow<String> = _sessionEndedMessages.asSharedFlow()

        init {
            // El SDK atrapa cualquier excepcion de fetchCredentials() (incluida
            // DeviceRevokedException) y la expone aqui, en downloadError.
            scope.launch {
                powerSyncDatabase.currentStatus
                    .asFlow()
                    .mapNotNull { it.downloadError as? DeviceRevokedException }
                    .collect {
                        if (handlingRevocation.compareAndSet(false, true)) {
                            onDeviceRevoked()
                        }
                    }
            }
        }

        // Llamar justo despues de conectar exitosamente (login o auto-login).
        fun onSessionStarted() {
            handlingRevocation.set(false)
            scheduleSyncWorker()
        }

        suspend fun logout() {
            clearLocalSession()
        }

        private suspend fun onDeviceRevoked() {
            clearLocalSession()
            _sessionEndedMessages.emit("Tu sesión se cerró porque iniciaste sesión en un dispositivo nuevo")
        }

        private suspend fun clearLocalSession() {
            // 1. Borra TODA la base local de PowerSync de forma segura.
            powerSyncDatabase.disconnectAndClear()
            // 2. Borra refreshToken/userId de DataStore.
            tokenStore.clear()
            // 3. No tiene sentido seguir subiendo cambios de una sesion muerta.
            cancelSyncWorker()
        }

        private fun scheduleSyncWorker() {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun cancelSyncWorker() {
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
        }
    }
