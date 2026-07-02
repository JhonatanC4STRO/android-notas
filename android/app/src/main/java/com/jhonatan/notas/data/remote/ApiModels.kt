package com.jhonatan.notas.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String,
)

@Serializable
data class LoginResponse(
    val refreshToken: String,
    val powersyncToken: String,
)

@Serializable
data class RenewTokenRequest(
    val refreshToken: String,
    val deviceId: String,
)

@Serializable
data class RenewTokenResponse(
    val powersyncToken: String,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String? = null,
)

@Serializable
data class UploadOperation(
    val op: String,
    val table: String,
    val id: String,
    val data: JsonObject?,
)

@Serializable
data class UploadDataRequest(
    val batch: List<UploadOperation>,
)

sealed interface LoginResult {
    data class Success(val refreshToken: String, val powersyncToken: String) : LoginResult

    data object InvalidCredentials : LoginResult

    data class Error(val message: String) : LoginResult
}

sealed interface RenewTokenResult {
    data class Success(val powersyncToken: String) : RenewTokenResult

    data object DeviceRevoked : RenewTokenResult

    data class Error(val message: String) : RenewTokenResult
}
