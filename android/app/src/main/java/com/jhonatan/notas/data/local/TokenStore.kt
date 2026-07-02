package com.jhonatan.notas.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Guarda la sesion local: el refreshToken de larga duracion (30 dias,
// CLAUDE.md seccion 3.5) cifrado con CryptoManager, y el userId (no
// sensible, solo un UUID) que las notas creadas localmente necesitan.
@Singleton
class TokenStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val cryptoManager: CryptoManager,
    ) {
        suspend fun saveRefreshToken(refreshToken: String) {
            val encrypted = cryptoManager.encrypt(refreshToken)
            dataStore.edit { prefs ->
                prefs[CIPHERTEXT_KEY] = encrypted.ciphertext
                prefs[IV_KEY] = encrypted.iv
            }
        }

        suspend fun getRefreshToken(): String? {
            val prefs = dataStore.data.first()
            val ciphertext = prefs[CIPHERTEXT_KEY] ?: return null
            val iv = prefs[IV_KEY] ?: return null

            return runCatching {
                cryptoManager.decrypt(CryptoManager.EncryptedPayload(ciphertext, iv))
            }.getOrNull()
        }

        suspend fun saveUserId(userId: String) {
            dataStore.edit { prefs -> prefs[USER_ID_KEY] = userId }
        }

        suspend fun getUserId(): String? = dataStore.data.first()[USER_ID_KEY]

        suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(CIPHERTEXT_KEY)
                prefs.remove(IV_KEY)
                prefs.remove(USER_ID_KEY)
            }
        }

        private companion object {
            val CIPHERTEXT_KEY = stringPreferencesKey("refresh_token_ciphertext")
            val IV_KEY = stringPreferencesKey("refresh_token_iv")
            val USER_ID_KEY = stringPreferencesKey("user_id")
        }
    }
