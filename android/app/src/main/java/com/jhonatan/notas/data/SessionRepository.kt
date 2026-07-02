package com.jhonatan.notas.data

import com.jhonatan.notas.data.local.DeviceIdProvider
import com.jhonatan.notas.data.local.TokenStore
import com.jhonatan.notas.data.remote.ApiClient
import com.jhonatan.notas.data.remote.JwtUtils
import com.jhonatan.notas.data.remote.LoginResult
import com.jhonatan.notas.sync.NotasBackendConnector
import com.jhonatan.notas.sync.SessionManager
import com.powersync.PowerSyncDatabase
import javax.inject.Inject
import javax.inject.Singleton

// Orquesta login, guardado de sesion y conexion de PowerSync. Ni la UI ni
// los ViewModels llaman a ApiClient o PowerSyncDatabase directamente.
@Singleton
class SessionRepository
    @Inject
    constructor(
        private val apiClient: ApiClient,
        private val tokenStore: TokenStore,
        private val deviceIdProvider: DeviceIdProvider,
        private val powerSyncDatabase: PowerSyncDatabase,
        private val connector: NotasBackendConnector,
        private val sessionManager: SessionManager,
    ) {
        suspend fun login(
            email: String,
            password: String,
        ): LoginResult {
            val deviceId = deviceIdProvider.getDeviceId()
            val result = apiClient.login(email, password, deviceId)

            if (result is LoginResult.Success) {
                tokenStore.saveRefreshToken(result.refreshToken)
                JwtUtils.decodeUserId(result.powersyncToken)?.let { tokenStore.saveUserId(it) }
                powerSyncDatabase.connect(connector)
                sessionManager.onSessionStarted()
            }

            return result
        }

        suspend fun hasStoredSession(): Boolean = tokenStore.getRefreshToken() != null

        // Si hay una sesion guardada, conecta PowerSync (que renovara el
        // powersyncToken via el connector) y devuelve true.
        suspend fun connectIfSessionExists(): Boolean {
            if (!hasStoredSession()) return false
            powerSyncDatabase.connect(connector)
            sessionManager.onSessionStarted()
            return true
        }
    }
