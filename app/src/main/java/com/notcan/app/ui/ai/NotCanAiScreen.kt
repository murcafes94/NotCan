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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
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
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
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
    whisperModelState: WhisperModelState,
    whisperModelProgress: Int?,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onDownloadWhisperModel: () -> Unit,
    onRemoveWhisperModel: () -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onAsk: (String) -> Unit,
    onClear: () -> Unit
) {
    var question by remember(classTitle) { mutableStateOf("") }
    val latestAudio = audioRecordings.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("IA y transcripción", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Selecciona una clase" }, color = NotCanGray)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("${WhisperModelSpec.DISPLAY_NAME} · local", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("~1,5 GB · sin tokens · sin subir el audio", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                when (whisperModelState) {
                    WhisperModelState.NOT_INSTALLED -> {
                        Text("El modelo se descarga una sola vez y queda guardado en la tablet.", color = NotCanGray)
                        Button(onClick = onDownloadWhisperModel) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Descargar modelo por Wi‑Fi")
                        }
                    }
                    WhisperModelState.DOWNLOADING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                whisperModelProgress?.let { "Descargando… $it%" } ?: "Descargando modelo…",
                                color = NotCanGray
                            )
                        }
                    }
                    WhisperModelState.INSTALLED -> {
                        Text("Modelo listo para transcribir clases completamente offline.", color = NotCanGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = latestAudio != null && !localWhisperBusy,
                                onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }
                            ) {
                                Text(if (localWhisperBusy) "Transcribiendo…" else "Transcribir último audio")
                            }
                            OutlinedButton(onClick = onRemoveWhisperModel, enabled = !localWhisperBusy) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Eliminar modelo")
                            }
                        }
                    }
                }
                localWhisperError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (transcripts.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Última transcripción", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(transcripts.first().body.take(5000), color = NotCanOffWhite)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("IA generativa en nube · opcional", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Solo se usa cuando tú la solicitas y puede consumir cuota de Gemini.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!configured) {
                    Text("Gemini no está configurado. NotCan puede grabar, transcribir y editar apuntes sin él.", color = NotCanGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { question = "Haz un resumen estructurado de esta clase con las ideas principales." }) { Text("Resumen") }
                    OutlinedButton(onClick = { question = "Crea preguntas de examen con sus respuestas basadas en esta clase." }) { Text("Examen") }
                    OutlinedButton(onClick = { question = "Crea flashcards breves de los conceptos más importantes de esta clase." }) { Text("Flashcards") }
                    OutlinedButton(onClick = { question = "Genera una jerarquía para un mapa mental de esta clase." }) { Text("Mapa") }
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta o instrucción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(enabled = configured && !busy && question.isNotBlank(), onClick = { onAsk(question) }) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Usar Gemini")
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
