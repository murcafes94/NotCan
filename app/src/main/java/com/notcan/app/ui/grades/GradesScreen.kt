package com.notcan.app.ui.grades

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.GradeItemEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.util.Locale

@Composable
fun GradesScreen(
    subjectName: String?,
    items: List<GradeItemEntity>,
    onAdd: (String, Double, Double, Double) -> Unit,
    onDelete: (String) -> Unit
) {
    var title by remember(subjectName) { mutableStateOf("") }
    var score by remember(subjectName) { mutableStateOf("") }
    var maxScore by remember(subjectName) { mutableStateOf("100") }
    var weight by remember(subjectName) { mutableStateOf("") }
    var error by remember(subjectName) { mutableStateOf<String?>(null) }

    val totalWeight = items.sumOf { it.weightPercent.coerceAtLeast(0.0) }
    val weightedEarned = items.sumOf { it.weightedContribution }
    val hasWeights = totalWeight > 0.0
    val simpleAverage = if (items.isNotEmpty()) items.map { it.normalized * 100.0 }.average() else 0.0
    val completedAverage = if (hasWeights) weightedEarned / totalWeight * 100.0 else simpleAverage

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Calificaciones", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(subjectName ?: "Selecciona una materia", color = NotCanGray)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Resumen automático", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Promedio sobre actividades registradas: ${fmt(completedAverage)}%", color = NotCanOffWhite)
                    if (hasWeights) {
                        Text("Aporte acumulado al total de la materia: ${fmt(weightedEarned)} / 100", color = NotCanGray)
                        Text("Porcentaje ya evaluado: ${fmt(totalWeight)}%", color = if (totalWeight > 100.0) MaterialTheme.colorScheme.error else NotCanGray)
                        if (totalWeight < 100.0) Text("Falta por evaluar: ${fmt(100.0 - totalWeight)}%", color = NotCanGray)
                    } else {
                        Text("Sin ponderación configurada · se usa promedio simple.", color = NotCanGray)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Añadir calificación", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Actividad · examen, ensayo, lectura…") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = score, onValueChange = { score = it }, label = { Text("Obtenido") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = maxScore, onValueChange = { maxScore = it }, label = { Text("Máximo (opcional)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso % (opcional)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = subjectName != null,
                        onClick = {
                            val s = score.replace(',', '.').toDoubleOrNull()
                            val m = maxScore.replace(',', '.').toDoubleOrNull() ?: 100.0
                            val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (s == null || m <= 0.0 || w !in 0.0..100.0) {
                                error = "Revisa la nota y, si los usas, el máximo o porcentaje."
                            } else {
                                onAdd(title, s, m, w)
                                title = ""; score = ""; weight = ""; error = null
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar")
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item { Text("Todavía no has registrado calificaciones para esta materia.", color = NotCanGray) }
        } else {
            items(items, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.8f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                            Text(
                                if (item.weightPercent > 0.0) "${fmt(item.score)} / ${fmt(item.maxScore)} · ${fmt(item.normalized * 100.0)}% · peso ${fmt(item.weightPercent)}%"
                                else "${fmt(item.score)} / ${fmt(item.maxScore)} · ${fmt(item.normalized * 100.0)}%",
                                color = NotCanGray
                            )
                        }
                        if (item.weightPercent > 0.0) Text("+${fmt(item.weightedContribution)}", color = NotCanBlue, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, "Eliminar calificación") }
                    }
                }
            }
        }
    }
}

private fun fmt(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
