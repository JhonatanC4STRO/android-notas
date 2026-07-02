package com.jhonatan.notas.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// El deviceId es el identificador de hardware de CLAUDE.md (seccion 3.4):
// se genera una sola vez y se reutiliza siempre desde entonces.
@Singleton
class DeviceIdProvider
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val mutex = Mutex()

        suspend fun getDeviceId(): String {
            dataStore.data.first()[DEVICE_ID_KEY]?.let { return it }

            return mutex.withLock {
                dataStore.data.first()[DEVICE_ID_KEY]?.let { return it }

                val newDeviceId = UUID.randomUUID().toString()
                dataStore.edit { prefs -> prefs[DEVICE_ID_KEY] = newDeviceId }
                newDeviceId
            }
        }

        private companion object {
            val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        }
    }
