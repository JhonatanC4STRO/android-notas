package com.jhonatan.notas.data.remote

import com.jhonatan.notas.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// Unico punto de contacto con el backend (CLAUDE.md seccion 2.1 y 6): la UI
// y los ViewModels nunca llaman esto directamente, solo SessionRepository y
// NotasBackendConnector.
@Singleton
class ApiClient
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        private val baseUrl = BuildConfig.BACKEND_URL

        suspend fun login(
            email: String,
            password: String,
            deviceId: String,
        ): LoginResult =
            try {
                val response =
                    httpClient.post("$baseUrl/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(LoginRequest(email, password, deviceId))
                    }
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val body = response.body<LoginResponse>()
                        LoginResult.Success(body.refreshToken, body.powersyncToken)
                    }
                    HttpStatusCode.Unauthorized -> LoginResult.InvalidCredentials
                    else -> LoginResult.Error("Error inesperado del servidor (${response.status.value})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginResult.Error(e.message ?: "No se pudo conectar con el servidor")
            }

        suspend fun renewToken(
            refreshToken: String,
            deviceId: String,
        ): RenewTokenResult =
            try {
                val response =
                    httpClient.post("$baseUrl/auth/token") {
                        contentType(ContentType.Application.Json)
                        setBody(RenewTokenRequest(refreshToken, deviceId))
                    }
                when (response.status) {
                    HttpStatusCode.OK -> RenewTokenResult.Success(response.body<RenewTokenResponse>().powersyncToken)
                    HttpStatusCode.Unauthorized -> {
                        val error = response.body<ErrorResponse>()
                        if (error.code == "DEVICE_REVOKED") {
                            RenewTokenResult.DeviceRevoked
                        } else {
                            RenewTokenResult.Error(error.code)
                        }
                    }
                    else -> RenewTokenResult.Error("Error inesperado del servidor (${response.status.value})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RenewTokenResult.Error(e.message ?: "No se pudo conectar con el servidor")
            }

        // Sin try/catch: los errores de red o 5xx deben propagarse para que
        // NotasBackendConnector.uploadData() los relance y PowerSync reintente el lote.
        suspend fun uploadData(
            powersyncToken: String,
            batch: List<UploadOperation>,
        ): HttpResponse =
            httpClient.post("$baseUrl/api/upload-data") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $powersyncToken")
                setBody(UploadDataRequest(batch))
            }
    }
