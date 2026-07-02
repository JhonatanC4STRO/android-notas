package com.jhonatan.notas.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// Cifra/descifra strings con una llave AES-GCM que nunca sale del
// Android Keystore, para guardar el refreshToken de forma segura en DataStore.
@Singleton
class CryptoManager
    @Inject
    constructor() {
        private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        data class EncryptedPayload(val ciphertext: String, val iv: String)

        fun encrypt(plainText: String): EncryptedPayload {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            return EncryptedPayload(
                ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            )
        }

        fun decrypt(payload: EncryptedPayload): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(payload.iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            val plainBytes = cipher.doFinal(Base64.decode(payload.ciphertext, Base64.NO_WRAP))
            return String(plainBytes, Charsets.UTF_8)
        }

        private fun getOrCreateKey(): SecretKey {
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec =
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        private companion object {
            const val ANDROID_KEY_STORE = "AndroidKeyStore"
            const val KEY_ALIAS = "notas_refresh_token_key"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val GCM_TAG_LENGTH_BITS = 128
        }
    }
