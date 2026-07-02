package com.jhonatan.notas.data.powersync

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

// Espejo local de la tabla "notas" de CLAUDE.md (seccion 4). El "id" lo
// gestiona PowerSync automaticamente; no se declara como columna.
private val notasTable =
    Table(
        "notas",
        listOf(
            Column.text("titulo"),
            Column.text("contenido"),
            Column.text("user_id"),
            Column.integer("updated_at"),
            Column.integer("deleted"),
        ),
    )

val notasSchema: Schema = Schema(listOf(notasTable))
