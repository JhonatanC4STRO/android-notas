package com.jhonatan.notas.data.remote

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Solo lee el claim "sub" del payload del JWT (base64, sin verificar firma):
// es un dato que ya confiamos porque el propio backend nos lo acaba de emitir.
// La verificacion de firma real ocurre del lado del servidor.
object JwtUtils {
    fun decodeUserId(jwt: String): String? =
        runCatching {
            val payloadSegment = jwt.split(".")[1]
            val decodedBytes = Base64.decode(payloadSegment, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = Json.parseToJsonElement(String(decodedBytes, Charsets.UTF_8)).jsonObject
            json["sub"]?.jsonPrimitive?.content
        }.getOrNull()
}
