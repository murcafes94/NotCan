package com.notcan.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface

@Composable
fun NotCanAiScreen(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    error: String?,
    result: String,
    transcripts: List<TranscriptEntity>,
    audioRecordings: List<AudioRecordingEntity>,
    liveTranscript: String,
    liveStatus: String,
    onAsk: (String) -> Unit,
    onTranscribeAudio: (String) -> Unit,
    onClear: () -> Unit
) {
    var question by remember(classTitle) { mutableStateOf("") }
    val latestAudio = audioRecordings.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("IA de NotCan", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Selecciona una clase para darle contexto" },
                    color = NotCanGray
                )
            }
        }

        if (!configured) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemini preparado, falta vincular Firebase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "La app sigue funcionando offline. Para activar Gemini hay que registrar com.notcan.app en Firebase AI Logic y añadir la configuración de Firebase/App Check.",
                        color = NotCanGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Transcripción", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(liveStatus, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
                if (liveTranscript.isNotBlank()) {
                    Text("En vivo", color = NotCanBlue, style = MaterialTheme.typography.labelLarge)
                    Text(liveTranscript.takeLast(4000), color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                }
                if (transcripts.isNotEmpty()) {
                    Text("Guardada en esta clase", color = NotCanBlue, style = MaterialTheme.typography.labelLarge)
                    Text(transcripts.first().body.take(5000), color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                }
                if (latestAudio != null) {
                    OutlinedButton(enabled = !busy && configured, onClick = { onTranscribeAudio(latestAudio.id) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Revisar último audio con Gemini")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estudiar esta clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { question = "Haz un resumen estructurado de esta clase con las ideas principales." }) { Text("Resumen") }
                    OutlinedButton(onClick = { question = "Crea preguntas de examen con sus respuestas basadas en esta clase." }) { Text("Examen") }
                    OutlinedButton(onClick = { question = "Crea flashcards breves de los conceptos más importantes de esta clase." }) { Text("Flashcards") }
                    OutlinedButton(onClick = { question = "Genera una jerarquía para un mapa mental de esta clase." }) { Text("Mapa") }
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta o instrucción para Gemini") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(enabled = configured && !busy && question.isNotBlank(), onClick = { onAsk(question) }) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Preguntar a Gemini")
                }
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("No se pudo completar la solicitud", fontWeight = FontWeight.SemiBold)
                    Text(it)
                }
            }
        }

        if (result.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Respuesta", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onClear) { Text("Limpiar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(result, color = NotCanOffWhite, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
