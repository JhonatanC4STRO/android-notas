package com.jhonatan.notas.sync

import com.jhonatan.notas.BuildConfig
import com.jhonatan.notas.data.local.DeviceIdProvider
import com.jhonatan.notas.data.local.TokenStore
import com.jhonatan.notas.data.remote.ApiClient
import com.jhonatan.notas.data.remote.RenewTokenResult
import com.jhonatan.notas.data.remote.UploadOperation
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton

// Unico punto de contacto entre PowerSync y nuestro backend
// (CLAUDE.md seccion 2.2 y 2.3): la UI y los repositorios de datos nunca
// hablan con el servidor directamente.
@Singleton
class NotasBackendConnector
    @Inject
    constructor(
        private val apiClient: ApiClient,
        private val tokenStore: TokenStore,
        private val deviceIdProvider: DeviceIdProvider,
    ) : PowerSyncBackendConnector() {
        override suspend fun fetchCredentials(): PowerSyncCredentials? {
            val refreshToken = tokenStore.getRefreshToken() ?: return null
            val deviceId = deviceIdProvider.getDeviceId()

            return when (val result = apiClient.renewToken(refreshToken, deviceId)) {
                is RenewTokenResult.Success ->
                    PowerSyncCredentials(endpoint = BuildConfig.POWERSYNC_URL, token = result.powersyncToken)
                is RenewTokenResult.DeviceRevoked -> throw DeviceRevokedException()
                is RenewTokenResult.Error -> error("No se pudo renovar el token de PowerSync: ${result.message}")
            }
        }

        override suspend fun uploadData(database: PowerSyncDatabase) {
            val batch = database.getCrudBatch() ?: return
            val credentials =
                getCredentialsCached() ?: error("No hay credenciales de PowerSync en cache para subir cambios")

            val operations =
                batch.crud.map { entry ->
                    UploadOperation(
                        op = entry.op.toJson(),
                        table = entry.table,
                        id = entry.id,
                        data = entry.opData?.jsonValues,
                    )
                }

            val response = apiClient.uploadData(credentials.token, operations)

            if (response.status == HttpStatusCode.OK) {
                batch.complete(null)
            } else {
                // Error de red o 5xx: no se completa el batch, PowerSync reintenta.
                error("upload-data fallo con estado ${response.status}")
            }
        }
    }
