package com.jhonatan.notas.domain

data class Nota(
    val id: String,
    val titulo: String,
    val contenido: String,
    val userId: String,
    val updatedAt: Long,
)
