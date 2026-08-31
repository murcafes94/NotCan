package com.notcan.app.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TaskItemEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(
    subjects: List<SubjectEntity>,
    items: List<TaskItemEntity>,
    onAdd: (String?, String, String, Long?, String, String) -> Unit,
    onCompleted: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    val pending = items.count { !it.isCompleted }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, null, tint = NotCanBlue)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tareas", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("$pending pendiente(s) · ${items.count { it.isCompleted }} completada(s)", color = NotCanGray)
                }
                Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Añadir") }
            }
        }
        if (items.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("Tu checklist académico está vacío", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Añade tareas, controles de lectura, lecciones, exposiciones, ensayos o exámenes.", color = NotCanGray)
                }
            }
        }
        items(items, key = { it.id }) { task ->
            val subject = subjects.firstOrNull { it.id == task.subjectId }?.name
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { onCompleted(task.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, color = if (task.isCompleted) NotCanGray else NotCanOffWhite, fontWeight = FontWeight.Medium, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                        val metadata = buildList {
                            add(task.type)
                            subject?.let(::add)
                            task.dueAtEpochMs?.let { add("Entrega ${formatDate(it)}") }
                            if (task.priority != "Normal") add(task.priority)
                        }.joinToString(" · ")
                        if (metadata.isNotBlank()) Text(metadata, color = if (task.priority == "Alta" && !task.isCompleted) MaterialTheme.colorScheme.error else NotCanGray, style = MaterialTheme.typography.bodySmall)
                        if (task.notes.isNotBlank()) Text(task.notes, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            }
        }
    }

    if (adding) AddTaskDialog(subjects, onDismiss = { adding = false }) { subjectId, title, type, due, priority, notes ->
        onAdd(subjectId, title, type, due, priority, notes)
        adding = false
    }
}

@Composable
private fun AddTaskDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Long?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Tarea") }
    var subjectId by remember { mutableStateOf<String?>(null) }
    var due by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val types = listOf("Tarea", "Control de lectura", "Lección", "Exposición", "Ensayo", "Examen", "Otro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo pendiente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Tipo", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { value -> FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value) }) }
                }
                Text("Materia (opcional)", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = subjectId == null, onClick = { subjectId = null }, label = { Text("General") })
                    subjects.forEach { subject -> FilterChip(selected = subjectId == subject.id, onClick = { subjectId = subject.id }, label = { Text(subject.name) }) }
                }
                OutlinedTextField(due, { due = it }, label = { Text("Entrega opcional · AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Prioridad", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Baja", "Normal", "Alta").forEach { value -> FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(value) }) }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas opcionales") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "Escribe un título."; return@TextButton }
                val dueEpoch = if (due.isBlank()) null else runCatching {
                    LocalDate.parse(due.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
                if (due.isNotBlank() && dueEpoch == null) { error = "Usa la fecha AAAA-MM-DD."; return@TextButton }
                onSave(subjectId, title, type, dueEpoch, priority, notes)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatDate(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
