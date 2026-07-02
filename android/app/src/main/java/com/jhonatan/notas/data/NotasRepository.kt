package com.jhonatan.notas.data

import com.jhonatan.notas.data.local.TokenStore
import com.jhonatan.notas.domain.Nota
import com.powersync.PowerSyncDatabase
import com.powersync.db.getLong
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// CRUD sobre la tabla local "notas" de PowerSync (CLAUDE.md seccion 2.1 y 3):
// solo lee/escribe SQLite local. La sincronizacion es responsabilidad
// exclusiva de PowerSync; este repositorio no sabe que existe un servidor.
@Singleton
class NotasRepository
    @Inject
    constructor(
        private val powerSyncDatabase: PowerSyncDatabase,
        private val tokenStore: TokenStore,
    ) {
        fun observarNotas(): Flow<List<Nota>> =
            powerSyncDatabase.watch(
                sql = "SELECT id, titulo, contenido, user_id, updated_at FROM notas WHERE deleted = 0 ORDER BY updated_at DESC",
            ) { cursor ->
                Nota(
                    id = cursor.getString("id"),
                    titulo = cursor.getString("titulo"),
                    contenido = cursor.getString("contenido"),
                    userId = cursor.getString("user_id"),
                    updatedAt = cursor.getLong("updated_at"),
                )
            }

        suspend fun crearNota(
            titulo: String,
            contenido: String,
        ) {
            val userId = tokenStore.getUserId() ?: error("No hay un usuario logueado")
            val id = UUID.randomUUID().toString()
            val updatedAt = System.currentTimeMillis()

            powerSyncDatabase.execute(
                "INSERT INTO notas (id, titulo, contenido, user_id, updated_at, deleted) VALUES (?, ?, ?, ?, ?, 0)",
                listOf(id, titulo, contenido, userId, updatedAt),
            )
        }

        suspend fun editarNota(
            id: String,
            titulo: String,
            contenido: String,
        ) {
            val updatedAt = System.currentTimeMillis()

            powerSyncDatabase.execute(
                "UPDATE notas SET titulo = ?, contenido = ?, updated_at = ? WHERE id = ?",
                listOf(titulo, contenido, updatedAt, id),
            )
        }

        // Tombstone (CLAUDE.md seccion 3.3): jamas un DELETE fisico.
        suspend fun borrarNota(id: String) {
            val updatedAt = System.currentTimeMillis()

            powerSyncDatabase.execute(
                "UPDATE notas SET deleted = 1, updated_at = ? WHERE id = ?",
                listOf(updatedAt, id),
            )
        }
    }
