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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notcan.app.ai.MistralCredentialsStore
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.localai.LiveTranscriptionModelManager
import com.notcan.app.localai.LiveTranscriptionModelState
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelState
import com.notcan.app.localai.WhisperModelManager
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.settings.NotCanPreferences
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(preferences: NotCanPreferences) {
    val context = LocalContext.current
    val whisperManager = remember(context) { WhisperModelManager(context.applicationContext) }
    val liveManager = remember(context) { LiveTranscriptionModelManager(context.applicationContext) }
    val legacyStudyManager = remember(context) { StudyModelManager(context.applicationContext) }
    val credentials = remember(context) { MistralCredentialsStore(context.applicationContext) }
    val cycleDao = remember(context) { NotCanDatabase.getInstance(context.applicationContext).dao() }
    val cycles by cycleDao.observeCycles().collectAsState(initial = emptyList())
    val activeCycle = cycles.firstOrNull { it.isActive } ?: cycles.firstOrNull()
    val cycleEnded = activeCycle?.let { it.endEpochDay > 0L && LocalDate.now().toEpochDay() > it.endEpochDay } == true

    var assistantName by remember { mutableStateOf(preferences.assistantName) }
    var instructions by remember { mutableStateOf(preferences.aiInstructions) }
    var detail by remember { mutableStateOf(preferences.aiDetail) }
    var autoTranscribe by remember { mutableStateOf(preferences.autoTranscribeAfterRecording) }
    var autoCues by remember { mutableStateOf(preferences.autoDetectAcademicCues) }
    var apiKeyInput by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf(preferences.mistralAgentId) }
    var hasSavedKey by remember { mutableStateOf(credentials.hasApiKey()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
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
    val legacyStudyState = remember(refreshTick) { legacyStudyManager.state() }
    val mistralConfigured = hasSavedKey && agentId.trim().isNotBlank()

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

        if (cycleEnded) {
            CycleClosureSection(activeCycle)
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Asistente NotCan · Mistral", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text(if (mistralConfigured) "Configurado · listo para usar tu agente" else "Añade tu API key y el Agent ID que creaste en Mistral Studio", color = if (mistralConfigured) NotCanBlue else NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = agentId,
                    onValueChange = { agentId = it; saveMessage = null },
                    label = { Text("Agent ID") },
                    supportingText = { Text("Empieza normalmente por ag_… o usa el ID que muestra Mistral Studio.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it; saveMessage = null },
                    label = { Text(if (hasSavedKey) "Nueva API key (opcional)" else "API key de Mistral") },
                    supportingText = { Text(if (hasSavedKey) "Ya hay una clave cifrada en este dispositivo. Déjalo vacío para conservarla." else "La clave no se guarda en GitHub ni dentro del APK.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = agentId.trim().isNotBlank() && (hasSavedKey || apiKeyInput.isNotBlank()),
                        onClick = {
                            preferences.mistralAgentId = agentId.trim()
                            preferences.mistralConversationId = ""
                            if (apiKeyInput.isNotBlank()) {
                                runCatching { credentials.saveApiKey(apiKeyInput) }
                                    .onSuccess {
                                        hasSavedKey = true
                                        apiKeyInput = ""
                                        saveMessage = "Configuración guardada. Ya puedes probar el chat de NotCan AI."
                                    }
                                    .onFailure { saveMessage = it.message ?: "No se pudo guardar la clave" }
                            } else {
                                saveMessage = "Agent ID guardado. Se conserva la API key cifrada existente."
                            }
                        }
                    ) { Text("Guardar Mistral") }

                    if (hasSavedKey) {
                        OutlinedButton(onClick = {
                            credentials.clearApiKey()
                            preferences.mistralConversationId = ""
                            hasSavedKey = false
                            apiKeyInput = ""
                            saveMessage = "API key eliminada de este dispositivo."
                        }) { Text("Eliminar clave") }
                    }
                }

                saveMessage?.let { Text(it, color = if (mistralConfigured) NotCanBlue else NotCanGray, style = MaterialTheme.typography.bodySmall) }
                Text("La IA generativa necesita Internet. Grabación, apuntes y transcripción local siguen funcionando sin conexión.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Componentes y descargas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Recursos locales para transcripción y trabajo sin conexión.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                DownloadComponentCard(
                    title = "Transcripción en vivo",
                    subtitle = "Moonshine español · texto provisional durante la grabación",
                    sizeText = "~63 MB",
                    stateText = when (liveState) {
                        LiveTranscriptionModelState.INSTALLED -> "Instalado"
                        LiveTranscriptionModelState.DOWNLOADING -> "Descargando"
                        LiveTranscriptionModelState.NOT_INSTALLED -> "No instalado"
                    },
                    progress = liveProgress,
                    installed = liveState == LiveTranscriptionModelState.INSTALLED,
                    downloading = liveState == LiveTranscriptionModelState.DOWNLOADING,
                    onDownload = { runCatching { liveManager.enqueueDownload() }; refreshTick++ },
                    onRemove = { runCatching { liveManager.removeModel() }; refreshTick++ }
                )

                DownloadComponentCard(
                    title = "Transcripción final rápida",
                    subtitle = WhisperModelSpec.DISPLAY_NAME,
                    sizeText = "~57 MB",
                    stateText = when (whisperState) {
                        WhisperModelState.INSTALLED -> "Instalado"
                        WhisperModelState.DOWNLOADING -> "Descargando"
                        WhisperModelState.NOT_INSTALLED -> "No instalado"
                    },
                    progress = whisperProgress,
                    installed = whisperState == WhisperModelState.INSTALLED,
                    downloading = whisperState == WhisperModelState.DOWNLOADING,
                    onDownload = { runCatching { whisperManager.enqueueDownload() }; refreshTick++ },
                    onRemove = { runCatching { whisperManager.removeModel() }; refreshTick++ }
                )

                if (legacyStudyState != StudyModelState.NOT_INSTALLED) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Modelo IA local anterior", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                            Text("Qwen ya no se usa como asistente principal. Puedes eliminar su descarga para recuperar aproximadamente 639 MB.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { runCatching { legacyStudyManager.removeModel() }; refreshTick++ }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (legacyStudyState == StudyModelState.DOWNLOADING) "Cancelar y eliminar" else "Eliminar Qwen")
                            }
                        }
                    }
                }

                Text("Las descargas usan el gestor de Android y pueden continuar aunque salgas de NotCan.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Preferencias del asistente", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
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
                    supportingText = { Text("Estas preferencias se añaden al contexto que NotCan envía a tu agente.") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Nivel de detalle", color = NotCanGray)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Breve", "Equilibrado", "Profundo").forEach { option ->
                        FilterChip(selected = detail == option, onClick = { detail = option; preferences.aiDetail = option }, label = { Text(option) })
                    }
                }
            }
        }

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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)), shape = RoundedCornerShape(14.dp)) {
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
                LinearProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                Text(if (progress != null) "$progress %" else "Preparando descarga…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }

            when {
                installed -> OutlinedButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar")
                }
                downloading -> OutlinedButton(onClick = onRemove) { Text("Cancelar") }
                else -> Button(onClick = onDownload) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Descargar")
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
