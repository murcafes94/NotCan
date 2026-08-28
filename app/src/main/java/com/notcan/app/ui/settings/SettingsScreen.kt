package com.notcan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.localai.LiveTranscriptionModelManager
import com.notcan.app.localai.LiveTranscriptionModelSpec
import com.notcan.app.localai.LiveTranscriptionModelState
import com.notcan.app.localai.WhisperModelManager
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.settings.NotCanPreferences
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(preferences: NotCanPreferences) {
    val context = LocalContext.current
    val whisperManager = remember(context) { WhisperModelManager(context.applicationContext) }
    val liveManager = remember(context) { LiveTranscriptionModelManager(context.applicationContext) }

    var assistantName by remember { mutableStateOf(preferences.assistantName) }
    var instructions by remember { mutableStateOf(preferences.aiInstructions) }
    var detail by remember { mutableStateOf(preferences.aiDetail) }
    var autoFocus by remember { mutableStateOf(preferences.autoFocusOnRecording) }
    var autoTranscribe by remember { mutableStateOf(preferences.autoTranscribeAfterRecording) }
    var autoCues by remember { mutableStateOf(preferences.autoDetectAcademicCues) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            refreshTick++
        }
    }

    val whisperState = remember(refreshTick) { whisperManager.state() }
    val whisperProgress = remember(refreshTick) { whisperManager.progressPercent() }
    val liveState = remember(refreshTick) { liveManager.state() }
    val liveProgress = remember(refreshTick) { liveManager.progressPercent() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = NotCanBlue)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Configuración", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Ajusta NotCan a tu forma de estudiar.", color = NotCanGray)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Componentes y descargas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Instala aquí los recursos que NotCan necesita para trabajar sin conexión.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                DownloadComponentCard(
                    title = "Transcripción en vivo",
                    subtitle = "Moonshine español · para texto durante la grabación",
                    sizeText = "~63 MB",
                    stateText = when (liveState) {
                        LiveTranscriptionModelState.INSTALLED -> "Instalado"
                        LiveTranscriptionModelState.DOWNLOADING -> "Descargando"
                        LiveTranscriptionModelState.NOT_INSTALLED -> "No instalado"
                    },
                    progress = liveProgress,
                    installed = liveState == LiveTranscriptionModelState.INSTALLED,
                    downloading = liveState == LiveTranscriptionModelState.DOWNLOADING,
                    onDownload = {
                        runCatching { liveManager.enqueueDownload() }
                        refreshTick++
                    },
                    onRemove = {
                        runCatching { liveManager.removeModel() }
                        refreshTick++
                    }
                )

                DownloadComponentCard(
                    title = "Transcripción final de alta precisión",
                    subtitle = WhisperModelSpec.DISPLAY_NAME,
                    sizeText = "~1,5 GB",
                    stateText = when (whisperState) {
                        WhisperModelState.INSTALLED -> "Instalado"
                        WhisperModelState.DOWNLOADING -> "Descargando"
                        WhisperModelState.NOT_INSTALLED -> "No instalado"
                    },
                    progress = whisperProgress,
                    installed = whisperState == WhisperModelState.INSTALLED,
                    downloading = whisperState == WhisperModelState.DOWNLOADING,
                    onDownload = {
                        runCatching { whisperManager.enqueueDownload() }
                        refreshTick++
                    },
                    onRemove = {
                        runCatching { whisperManager.removeModel() }
                        refreshTick++
                    }
                )

                Text(
                    "Las descargas grandes se realizan con el gestor de Android, continúan aunque cierres NotCan y evitan datos móviles por defecto.",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Personalidad de la IA", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = assistantName,
                    onValueChange = { assistantName = it; preferences.assistantName = it },
                    label = { Text("Nombre del asistente") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it; preferences.aiInstructions = it },
                    label = { Text("Cómo quiero que responda") },
                    supportingText = { Text("Ej.: directo, profundo, con vocabulario teológico, citar primero los apuntes…") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Nivel de detalle", color = NotCanGray)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Breve", "Equilibrado", "Profundo").forEach { option ->
                        FilterChip(
                            selected = detail == option,
                            onClick = { detail = option; preferences.aiDetail = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
        }

        SettingsSwitch(
            title = "Modo concentración al grabar",
            subtitle = "Oculta navegación y elementos secundarios mientras una clase está activa.",
            checked = autoFocus,
            onCheckedChange = { autoFocus = it; preferences.autoFocusOnRecording = it }
        )
        SettingsSwitch(
            title = "Transcribir al terminar",
            subtitle = "Si Whisper está instalado, encola automáticamente la transcripción final en segundo plano.",
            checked = autoTranscribe,
            onCheckedChange = { autoTranscribe = it; preferences.autoTranscribeAfterRecording = it }
        )
        SettingsSwitch(
            title = "Detectar tareas, exámenes y énfasis",
            subtitle = "Busca expresiones como tarea, deber, trabajo, examen, ojo o muy importante en la transcripción.",
            checked = autoCues,
            onCheckedChange = { autoCues = it; preferences.autoDetectAcademicCues = it }
        )

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.7f)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Procesos en segundo plano", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text("Las transcripciones finales usan trabajo persistente con notificación. Los modelos instalados se guardan dentro del almacenamiento privado de NotCan.", color = NotCanGray)
            }
        }
    }
}

@Composable
private fun DownloadComponentCard(
    title: String,
    subtitle: String,
    sizeText: String,
    stateText: String,
    progress: Int?,
    installed: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SpatialAudioOff, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text(subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
                Text(sizeText, color = NotCanGray, style = MaterialTheme.typography.labelMedium)
            }

            Text(stateText, color = if (installed) NotCanBlue else NotCanGray, style = MaterialTheme.typography.labelMedium)

            if (downloading) {
                LinearProgressIndicator(
                    progress = (progress ?: 0) / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (progress != null) "$progress %" else "Preparando descarga…",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    installed -> OutlinedButton(onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar")
                    }
                    downloading -> OutlinedButton(onClick = onRemove) {
                        Text("Cancelar")
                    }
                    else -> Button(onClick = onDownload) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Descargar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text(subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
