package com.jhonatan.notas.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.powersync.PowerSyncDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

// Corre cada 15 minutos (encolado por SessionManager) con la app cerrada:
// abre/reutiliza la conexion de PowerSync solo el tiempo necesario para
// drenar la cola de subida local, y luego la cierra si fue ella quien la abrio.
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val powerSyncDatabase: PowerSyncDatabase,
        private val connector: NotasBackendConnector,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val yaEstabaConectado = powerSyncDatabase.currentStatus.connected
            var conectadaPorEsteWorker = false

            return try {
                withTimeout(SYNC_TIMEOUT_MS) {
                    if (powerSyncDatabase.getCrudBatch(limit = 1) != null) {
                        if (!yaEstabaConectado) {
                            powerSyncDatabase.connect(connector)
                            conectadaPorEsteWorker = true
                        }

                        while (powerSyncDatabase.getCrudBatch(limit = 1) != null) {
                            delay(POLL_INTERVAL_MS)
                        }
                    }
                }

                Result.success()
            } catch (e: TimeoutCancellationException) {
                // No se alcanzo a vaciar la cola: PowerSync/WorkManager reintentaran
                // con backoff (el proximo periodo, o antes si se configura backoff).
                Result.retry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.retry()
            } finally {
                if (conectadaPorEsteWorker) {
                    powerSyncDatabase.disconnect()
                }
            }
        }

        companion object {
            const val WORK_NAME = "notas_sync_worker"
            private const val SYNC_TIMEOUT_MS = 60_000L
            private const val POLL_INTERVAL_MS = 500L
        }
    }
