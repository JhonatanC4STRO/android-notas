package com.jhonatan.notas.ui.notas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhonatan.notas.domain.Nota
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotasScreen(
    modifier: Modifier = Modifier,
    viewModel: NotasViewModel = hiltViewModel(),
) {
    val notas by viewModel.notas.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    var mostrarDialogoCrear by rememberSaveable { mutableStateOf(false) }
    var notaEnEdicion by remember { mutableStateOf<Nota?>(null) }
    var notaABorrar by remember { mutableStateOf<Nota?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Notas") },
                actions = { SyncIndicator(syncState) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogoCrear = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear nota")
            }
        },
    ) { paddingValues ->
        if (notas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No tienes notas todavía")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notas, key = { it.id }) { nota ->
                    NotaCard(
                        nota = nota,
                        onClick = { notaEnEdicion = nota },
                        onDeleteClick = { notaABorrar = nota },
                    )
                }
            }
        }
    }

    if (mostrarDialogoCrear) {
        NotaEditDialog(
            titulo = "Nueva nota",
            initialTitulo = "",
            initialContenido = "",
            onDismiss = { mostrarDialogoCrear = false },
            onConfirm = { titulo, contenido ->
                viewModel.crearNota(titulo, contenido)
                mostrarDialogoCrear = false
            },
        )
    }

    notaEnEdicion?.let { nota ->
        NotaEditDialog(
            titulo = "Editar nota",
            initialTitulo = nota.titulo,
            initialContenido = nota.contenido,
            onDismiss = { notaEnEdicion = null },
            onConfirm = { nuevoTitulo, nuevoContenido ->
                viewModel.editarNota(nota.id, nuevoTitulo, nuevoContenido)
                notaEnEdicion = null
            },
        )
    }

    notaABorrar?.let { nota ->
        AlertDialog(
            onDismissRequest = { notaABorrar = null },
            title = { Text("Borrar nota") },
            text = { Text("¿Seguro que quieres borrar \"${nota.titulo}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.borrarNota(nota.id)
                    notaABorrar = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { notaABorrar = null }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotaCard(
    nota: Nota,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nota.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nota.contenido,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = formatFecha(nota.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar nota")
            }
        }
    }
}

@Composable
private fun NotaEditDialog(
    titulo: String,
    initialTitulo: String,
    initialContenido: String,
    onDismiss: () -> Unit,
    onConfirm: (titulo: String, contenido: String) -> Unit,
) {
    var tituloTexto by rememberSaveable { mutableStateOf(initialTitulo) }
    var contenidoTexto by rememberSaveable { mutableStateOf(initialContenido) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tituloTexto,
                    onValueChange = { tituloTexto = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contenidoTexto,
                    onValueChange = { contenidoTexto = it },
                    label = { Text("Contenido") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tituloTexto, contenidoTexto) },
                enabled = tituloTexto.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun SyncIndicator(state: SyncIndicatorState) {
    when (state) {
        SyncIndicatorState.CONECTADO -> {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Conectado",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        SyncIndicatorState.SIN_CONEXION -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Sin conexión",
                tint = Color.Gray,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        SyncIndicatorState.SINCRONIZANDO -> {
            val infiniteTransition = rememberInfiniteTransition(label = "sync-rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "sync-rotation-value",
            )
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Sincronizando",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp).rotate(rotation),
            )
        }
    }
}

private fun formatFecha(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
