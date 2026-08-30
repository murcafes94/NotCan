package com.notcan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.data.CycleLifecycleManager
import com.notcan.app.data.local.AcademicVocabularyTermEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun CycleClosureSection(cycle: StudyCycleEntity?) {
    if (cycle == null) return

    val context = LocalContext.current
    val manager = remember(context) { CycleLifecycleManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var preview by remember(cycle.id) { mutableStateOf<CycleLifecycleManager.CyclePreview?>(null) }
    var loading by remember(cycle.id) { mutableStateOf(false) }
    var showVocabulary by remember(cycle.id) { mutableStateOf(false) }
    var confirmDelete by remember(cycle.id) { mutableStateOf(false) }
    var status by remember(cycle.id) { mutableStateOf<String?>(null) }

    val today = LocalDate.now().toEpochDay()
    val ended = cycle.endEpochDay > 0L && today > cycle.endEpochDay

    LaunchedEffect(cycle.id) {
        loading = true
        preview = runCatching { manager.previewCycle(cycle.id) }.getOrNull()
        loading = false
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Cierre del ciclo lectivo", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Text(
                if (cycle.endEpochDay <= 0L) {
                    "Define la fecha final del ciclo en Calendario antes de cerrarlo. NotCan nunca eliminará un ciclo automáticamente."
                } else if (ended) {
                    "${cycle.name} ya terminó. Puedes revisar el vocabulario aprendido y, cuando quieras, liberar el espacio del semestre."
                } else {
                    "${cycle.name} finaliza el ${formatEpochDay(cycle.endEpochDay)}. El contenido seguirá intacto hasta que confirmes manualmente su eliminación."
                },
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )

            preview?.let { info ->
                Text(
                    "${info.subjects} materias · ${info.classes} clases · ${info.physicalFiles} archivos · ${formatBytes(info.physicalBytes)}",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${info.cycleVocabulary.size} términos temporales · ${info.calendarEvents} eventos de calendario",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )

                if (info.cycleVocabulary.isNotEmpty()) {
                    OutlinedButton(onClick = { showVocabulary = !showVocabulary }) {
                        Text(if (showVocabulary) "Ocultar vocabulario" else "Revisar vocabulario")
                    }
                }

                if (showVocabulary) {
                    HorizontalDivider()
                    Text(
                        "Guarda como permanentes solo los términos que quieras reutilizar en próximos ciclos.",
                        color = NotCanGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    info.cycleVocabulary.take(40).forEach { term ->
                        VocabularyRow(term = term) {
                            scope.launch {
                                manager.keepVocabularyTermPermanently(term.id)
                                preview = manager.previewCycle(cycle.id)
                            }
                        }
                    }
                    if (info.cycleVocabulary.size > 40) {
                        Text(
                            "Se muestran 40 de ${info.cycleVocabulary.size} términos.",
                            color = NotCanGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (loading) {
                Text("Calculando contenido del ciclo…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }

            status?.let {
                Text(it, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }

            if (ended && !loading) {
                Button(onClick = { confirmDelete = true }) {
                    Text("Cerrar ciclo y liberar espacio")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!loading) confirmDelete = false },
            title = { Text("Eliminar ${cycle.name} definitivamente") },
            text = {
                Text(
                    "Se borrarán materias, clases, audios, transcripciones, apuntes, documentos, anotaciones, calificaciones, fuentes de TuNot, índices locales, vocabulario temporal y eventos sincronizados. El vocabulario base, permanente y personal se conserva. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        scope.launch {
                            runCatching { manager.deleteCycleCompletely(cycle.id) }
                                .onSuccess { result ->
                                    status = "Ciclo eliminado: ${result.filesDeleted}/${result.filesFound} archivos y ${result.sourceScopesDeleted} espacios de TuNot limpiados."
                                    confirmDelete = false
                                }
                                .onFailure { error ->
                                    status = error.message ?: "No se pudo cerrar el ciclo."
                                }
                            loading = false
                        }
                    }
                ) {
                    Text("Eliminar definitivamente", color = NotCanRed)
                }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun VocabularyRow(term: AcademicVocabularyTermEntity, onKeep: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(term.term, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
            Text(
                listOf(term.area, term.language, term.source).filter { it.isNotBlank() }.joinToString(" · "),
                color = NotCanGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
        OutlinedButton(onClick = onKeep) { Text("Conservar") }
    }
}

private fun formatEpochDay(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault("fecha definida")

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}
