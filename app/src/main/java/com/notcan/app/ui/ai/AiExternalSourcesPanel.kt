package com.notcan.app.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.sources.ClassSourceStore
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** External PDF/DOCX/EPUB/WEB library for TuNot. All saved sources are indexed per class. */
@Composable
internal fun AiExternalSourcesPanel(
    subjectName: String?,
    classTitle: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { ClassSourceStore(context.applicationContext) }
    val scopeKey = remember(subjectName, classTitle) { store.scopeKey(subjectName, classTitle) }
    val coroutineScope = rememberCoroutineScope()
    var sources by remember(scopeKey) { mutableStateOf(store.list(scopeKey)) }
    var indexing by remember(scopeKey) { mutableStateOf(false) }
    var error by remember(scopeKey) { mutableStateOf<String?>(null) }
    var query by remember(scopeKey) { mutableStateOf("") }
    var hits by remember(scopeKey) { mutableStateOf<List<ClassSourceStore.SearchHit>>(emptyList()) }
    var deleteCandidate by remember(scopeKey) { mutableStateOf<ClassSourceStore.SourceItem?>(null) }

    fun refresh() { sources = store.list(scopeKey) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        indexing = true
        error = null
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { store.import(scopeKey, uri) }
            }.onFailure { error = it.message ?: "No se pudo añadir la fuente" }
            refresh()
            indexing = false
        }
    }

    LaunchedEffect(query, sources) {
        hits = if (query.trim().length >= 2) {
            withContext(Dispatchers.IO) { store.search(scopeKey, query) }
        } else emptyList()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fuentes externas", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("PDF, DOCX, EPUB y web · se indexan para buscar y para TuNot", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { launcher.launch(ClassSourceStore.SUPPORTED_MIME_TYPES) },
                enabled = !indexing
            ) {
                if (indexing) CircularProgressIndicator(modifier = Modifier.width(17.dp).height(17.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (indexing) "Indexando…" else "Añadir fuente")
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        TuNotWebSourcesPanel(
            store = store,
            scopeKey = scopeKey,
            onSourcesChanged = ::refresh,
            modifier = Modifier.fillMaxWidth()
        )

        if (sources.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Todavía no hay fuentes guardadas", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text("Añade bibliografía o guarda una página web. NotCan indexará el contenido para que TuNot pueda usarlo en esta clase.", color = NotCanGray)
                }
            }
        } else {
            sources.forEach { source ->
                SourceFileCard(
                    item = source,
                    onEnabledChange = { enabled ->
                        store.setEnabled(scopeKey, source.id, enabled)
                        refresh()
                    },
                    onReindex = {
                        indexing = true
                        coroutineScope.launch {
                            runCatching { withContext(Dispatchers.IO) { store.reindex(source) } }
                                .onFailure { error = it.message ?: "No se pudo reindexar" }
                            refresh()
                            indexing = false
                        }
                    },
                    onDelete = { deleteCandidate = source }
                )
            }
        }

        if (sources.any { it.indexed }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Buscar dentro de las fuentes") },
                placeholder = { Text("Palabra o frase…") }
            )
            if (query.trim().length >= 2) {
                if (hits.isEmpty()) Text("Sin coincidencias", color = NotCanGray)
                else {
                    Text("${hits.size} coincidencia(s)", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height((hits.size.coerceAtMost(4) * 88).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hits, key = { "${it.sourceId}:${it.offset}" }) { hit ->
                            Surface(color = NotCanSurface.copy(alpha = 0.66f), shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("${hit.sourceName} · ${hit.sourceType}", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Text(hit.excerpt, color = NotCanOffWhite, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar fuente") },
            text = { Text("Se eliminará ${item.displayName} y su índice local. Tus apuntes, audios y transcripciones no se modificarán.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(scopeKey, item.id)
                    deleteCandidate = null
                    refresh()
                }) { Text("Eliminar", color = NotCanRed) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SourceFileCard(
    item: ClassSourceStore.SourceItem,
    onEnabledChange: (Boolean) -> Unit,
    onReindex: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = NotCanBlue.copy(alpha = 0.13f), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Description, null, tint = NotCanBlue, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, color = NotCanOffWhite, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.type} · ${if (item.indexed) "Indexada" else "Sin índice"}${if (!item.enabled) " · pausada" else ""}",
                    color = if (item.indexed) NotCanGray else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Checkbox(checked = item.enabled, onCheckedChange = onEnabledChange)
            if (!item.indexed) IconButton(onClick = onReindex) { Icon(Icons.Default.Refresh, "Reintentar indexación", tint = NotCanBlue) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar fuente", tint = NotCanRed) }
        }
    }
}
