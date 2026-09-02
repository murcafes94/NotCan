package com.notcan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.notcan.app.data.CycleLifecycleManager
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.launch

@Composable
fun CycleManagementSection(cycles: List<StudyCycleEntity>) {
    val context = LocalContext.current
    val app = context.applicationContext
    val manager = remember(app) { CycleLifecycleManager(app) }
    val repository = remember(app) { StudyRepository(NotCanDatabase.getInstance(app).dao(), app) }
    val scope = rememberCoroutineScope()
    var previews by remember { mutableStateOf<Map<String, CycleLifecycleManager.CyclePreview>>(emptyMap()) }
    var pendingDelete by remember { mutableStateOf<StudyCycleEntity?>(null) }
    var busyCycleId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val idsKey = cycles.joinToString("|") { "${it.id}:${it.isActive}" }

    LaunchedEffect(idsKey) {
        previews = cycles.associate { cycle -> cycle.id to manager.previewCycle(cycle.id) }
    }

    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Administrar ciclos", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Text(
                "Puedes activar o eliminar cualquier ciclo manualmente. NotCan nunca elimina un ciclo de forma automática.",
                color = NotCanGray,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            status?.let { Text(it, color = NotCanGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }

            cycles.sortedWith(compareByDescending<StudyCycleEntity> { it.isActive }.thenByDescending { it.createdAtEpochMs }).forEach { cycle ->
                val preview = previews[cycle.id]
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(cycle.name, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                                Text(
                                    if (preview == null) "Calculando contenido…" else "${preview.subjects} materias · ${preview.classes} clases · ${preview.physicalFiles} archivos",
                                    color = NotCanGray,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                            if (cycle.isActive) Text("ACTIVO", color = NotCanBlue, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!cycle.isActive) {
                                OutlinedButton(enabled = busyCycleId == null, onClick = {
                                    busyCycleId = cycle.id
                                    scope.launch {
                                        runCatching { repository.setActiveCycle(cycle.id) }
                                            .onSuccess { status = "${cycle.name} es ahora el ciclo activo." }
                                            .onFailure { status = it.message ?: "No se pudo activar el ciclo." }
                                        busyCycleId = null
                                    }
                                }) { Text("Activar") }
                            }
                            OutlinedButton(enabled = busyCycleId == null, onClick = { pendingDelete = cycle }) {
                                Text("Eliminar", color = NotCanRed)
                            }
                        }
                    }
                }
            }

            if (cycles.isEmpty()) Text("No hay ciclos guardados.", color = NotCanGray)
        }
    }

    pendingDelete?.let { cycle ->
        val preview = previews[cycle.id]
        AlertDialog(
            onDismissRequest = { if (busyCycleId == null) pendingDelete = null },
            title = { Text("Eliminar ${cycle.name} definitivamente") },
            text = {
                Text(
                    buildString {
                        append("Se eliminará manualmente este ciclo")
                        if (preview != null) append(" con ${preview.subjects} materias, ${preview.classes} clases y ${preview.physicalFiles} archivos")
                        append(". También se borrarán audios, transcripciones, apuntes, documentos, anotaciones, calificaciones, fuentes de TuNot, vocabulario temporal y eventos asociados. El vocabulario base, permanente y personal se conserva. Esta acción no se puede deshacer.")
                    }
                )
            },
            confirmButton = {
                TextButton(enabled = busyCycleId == null, onClick = {
                    busyCycleId = cycle.id
                    scope.launch {
                        runCatching { manager.deleteCycleCompletely(cycle.id) }
                            .onSuccess { result ->
                                status = "${cycle.name} eliminado · ${result.filesDeleted}/${result.filesFound} archivos locales limpiados."
                                pendingDelete = null
                            }
                            .onFailure { status = it.message ?: "No se pudo eliminar el ciclo." }
                        busyCycleId = null
                    }
                }) { Text(if (busyCycleId == cycle.id) "Eliminando…" else "Eliminar definitivamente", color = NotCanRed) }
            },
            dismissButton = { TextButton(enabled = busyCycleId == null, onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}
