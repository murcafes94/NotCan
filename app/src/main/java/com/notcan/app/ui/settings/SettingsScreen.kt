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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notcan.app.ai.GroqCredentialsStore
import com.notcan.app.ai.MistralCredentialsStore
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.StudyCycleEntity
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Configuración defensiva: ninguna consulta al gestor de descargas debe poder cerrar
 * NotCan. Los estados de modelos se leen con fallback seguro y la pantalla conserva
 * el período académico, Mistral y las preferencias principales.
 */
@Composable
fun SettingsScreen(preferences: NotCanPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whisperManager = remember(context) { WhisperModelManager(context.applicationContext) }
    val liveManager = remember(context) { LiveTranscriptionModelManager(context.applicationContext) }
    val legacyStudyManager = remember(context) { StudyModelManager(context.applicationContext) }
    val credentials = remember(context) { MistralCredentialsStore(context.applicationContext) }
    val groqCredentials = remember(context) { GroqCredentialsStore(context.applicationContext) }
    val cycleDao = remember(context) { NotCanDatabase.getInstance(context.applicationContext).dao() }
    val cycles by cycleDao.observeCycles().collectAsState(initial = emptyList())
    val activeCycle = cycles.firstOrNull { it.isActive } ?: cycles.firstOrNull()

    var assistantName by remember { mutableStateOf(preferences.assistantName) }
    var instructions by remember { mutableStateOf(preferences.aiInstructions) }
    var detail by remember { mutableStateOf(preferences.aiDetail) }
    var autoTranscribe by remember { mutableStateOf(preferences.autoTranscribeAfterRecording) }
    var preferOnlineTranscription by remember { mutableStateOf(preferences.preferOnlineTranscription) }
    var autoCues by remember { mutableStateOf(preferences.autoDetectAcademicCues) }
    var apiKeyInput by remember { mutableStateOf("") }
    var groqApiKeyInput by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf(preferences.mistralAgentId) }
    var hasSavedKey by remember { mutableStateOf(runCatching { credentials.hasApiKey() }.getOrDefault(false)) }
    var hasGroqKey by remember { mutableStateOf(runCatching { groqCredentials.hasApiKey() }.getOrDefault(false)) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            refreshTick++
        }
    }

    val whisperState = remember(refreshTick) {
        runCatching { whisperManager.state() }.getOrDefault(WhisperModelState.NOT_INSTALLED)
    }
    val whisperProgress = remember(refreshTick) {
        runCatching { whisperManager.progressPercent() }.getOrNull()
    }
    val liveState = remember(refreshTick) {
        runCatching { liveManager.state() }.getOrDefault(LiveTranscriptionModelState.NOT_INSTALLED)
    }
    val liveProgress = remember(refreshTick) {
        runCatching { liveManager.progressPercent() }.getOrNull()
    }
    val legacyStudyState = remember(refreshTick) {
        runCatching { legacyStudyManager.state() }.getOrDefault(StudyModelState.NOT_INSTALLED)
    }
    val mistralConfigured = hasSavedKey && agentId.trim().isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
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

        SupabaseAccountSection()

        AcademicPeriodSettings(
            cycle = activeCycle,
            onSave = { start, end ->
                activeCycle?.let { cycle ->
                    scope.launch {
                        runCatching { cycleDao.updateCycleDates(cycle.id, start.toEpochDay(), end.toEpochDay()) }
                            .onFailure { saveMessage = it.message ?: "No se pudieron guardar las fechas" }
                    }
                }
            }
        )

        activeCycle?.let { CycleClosureSection(it) }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("TuNot · Mistral", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (mistralConfigured) "Configurado · listo para usar" else "Añade tu API key y Agent ID",
                            color = if (mistralConfigured) NotCanBlue else NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = agentId,
                    onValueChange = { agentId = it; saveMessage = null },
                    label = { Text("Agent ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it; saveMessage = null },
                    label = { Text(if (hasSavedKey) "Nueva API key (opcional)" else "API key de Mistral") },
                    supportingText = {
                        Text(if (hasSavedKey) "Ya hay una clave cifrada. Déjalo vacío para conservarla." else "Se guarda cifrada en este dispositivo.")
                    },
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
                                        saveMessage = "Mistral guardado correctamente."
                                    }
                                    .onFailure { saveMessage = it.message ?: "No se pudo guardar la clave" }
                            } else {
                                saveMessage = "Agent ID guardado."
                            }
                        }
                    ) { Text("Guardar") }
                    if (hasSavedKey) {
                        OutlinedButton(onClick = {
                            runCatching { credentials.clearApiKey() }
                            preferences.mistralConversationId = ""
                            hasSavedKey = false
                            apiKeyInput = ""
                            saveMessage = "API key eliminada."
                        }) { Text("Eliminar clave") }
                    }
                }
                saveMessage?.let { Text(it, color = NotCanGray, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Transcripción online · Groq", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hasGroqKey) "Whisper Large V3 listo" else "Añade una API key gratuita de Groq",
                            color = if (hasGroqKey) NotCanBlue else NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = preferOnlineTranscription,
                        onCheckedChange = {
                            preferOnlineTranscription = it
                            preferences.preferOnlineTranscription = it
                        }
                    )
                }

                Text(
                    "Cuando está activado y hay Internet, la transcripción final envía el audio a Groq y usa Whisper Large V3. Si no hay conexión, NotCan usa Whisper local cuando está instalado. El plan gratuito y sus límites dependen de Groq.",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = groqApiKeyInput,
                    onValueChange = { groqApiKeyInput = it; saveMessage = null },
                    label = { Text(if (hasGroqKey) "Nueva API key de Groq (opcional)" else "API key de Groq") },
                    supportingText = {
                        Text(if (hasGroqKey) "Ya hay una clave cifrada. Déjalo vacío para conservarla." else "La clave se guarda cifrada en este dispositivo.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = hasGroqKey || groqApiKeyInput.isNotBlank(),
                        onClick = {
                            if (groqApiKeyInput.isNotBlank()) {
                                runCatching { groqCredentials.saveApiKey(groqApiKeyInput) }
                                    .onSuccess {
                                        hasGroqKey = true
                                        groqApiKeyInput = ""
                                        preferOnlineTranscription = true
                                        preferences.preferOnlineTranscription = true
                                        saveMessage = "Groq guardado. La transcripción online está activada."
                                    }
                                    .onFailure { saveMessage = it.message ?: "No se pudo guardar la clave de Groq" }
                            } else {
                                preferOnlineTranscription = true
                                preferences.preferOnlineTranscription = true
                                saveMessage = "Transcripción online activada."
                            }
                        }
                    ) { Text("Guardar y activar") }
                    if (hasGroqKey) {
                        OutlinedButton(onClick = {
                            runCatching { groqCredentials.clearApiKey() }
                            hasGroqKey = false
                            groqApiKeyInput = ""
                            saveMessage = "API key de Groq eliminada."
                        }) { Text("Eliminar clave") }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Componentes offline", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Recursos locales de voz y transcripción.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                DownloadComponentCard(
                    title = "Transcripción en vivo",
                    subtitle = "Moonshine español · provisional durante la grabación",
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
                            .onFailure { saveMessage = "Moonshine: ${it.message ?: "no se pudo iniciar la descarga"}" }
                        refreshTick++
                    },
                    onRemove = { runCatching { liveManager.removeModel() }; refreshTick++ }
                )

                DownloadComponentCard(
                    title = "Transcripción final · respaldo offline",
                    subtitle = "${WhisperModelSpec.DISPLAY_NAME} · se usa sin Internet o si Groq está desactivado",
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
                            .onFailure { saveMessage = "Whisper: ${it.message ?: "no se pudo iniciar la descarga"}" }
                        refreshTick++
                    },
                    onRemove = { runCatching { whisperManager.removeModel() }; refreshTick++ }
                )

                if (legacyStudyState != StudyModelState.NOT_INSTALLED) {
                    DownloadComponentCard(
                        title = "Modelo IA local anterior",
                        subtitle = "Qwen ya no es el asistente principal; puedes liberar ese espacio.",
                        stateText = if (legacyStudyState == StudyModelState.DOWNLOADING) "Descargando" else "Instalado",
                        progress = null,
                        installed = legacyStudyState == StudyModelState.INSTALLED,
                        downloading = legacyStudyState == StudyModelState.DOWNLOADING,
                        onDownload = {},
                        onRemove = { runCatching { legacyStudyManager.removeModel() }; refreshTick++ },
                        allowDownload = false
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Preferencias de TuNot", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
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
                    minLines = 3,
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
            title = "Transcribir al terminar",
            subtitle = "Al detener la grabación, usa Groq online si está configurado; sin Internet intenta Whisper local.",
            checked = autoTranscribe,
            onCheckedChange = { autoTranscribe = it; preferences.autoTranscribeAfterRecording = it }
        )
        SettingsSwitch(
            title = "Detectar tareas, exámenes y énfasis",
            subtitle = "Identifica señales académicas dentro de la transcripción.",
            checked = autoCues,
            onCheckedChange = { autoCues = it; preferences.autoDetectAcademicCues = it }
        )
    }
}

@Composable
private fun DownloadComponentCard(
    title: String,
    subtitle: String,
    stateText: String,
    progress: Int?,
    installed: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    allowDownload: Boolean = true
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text(subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
                Text(stateText, color = if (installed) NotCanBlue else NotCanGray, style = MaterialTheme.typography.labelMedium)
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = { (progress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(progress?.let { "$it%" } ?: "Descargando…", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!installed && !downloading && allowDownload) Button(onClick = onDownload) { Text("Descargar") }
                if (installed || downloading) OutlinedButton(onClick = onRemove) { Text(if (downloading) "Cancelar" else "Eliminar") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicPeriodSettings(cycle: StudyCycleEntity?, onSave: (LocalDate, LocalDate) -> Unit) {
    var startDate by remember(cycle?.id, cycle?.startEpochDay) {
        mutableStateOf(cycle?.startEpochDay?.takeIf { it > 0 }?.let(LocalDate::ofEpochDay))
    }
    var endDate by remember(cycle?.id, cycle?.endEpochDay) {
        mutableStateOf(cycle?.endEpochDay?.takeIf { it > 0 }?.let(LocalDate::ofEpochDay))
    }
    var startPickerOpen by remember { mutableStateOf(false) }
    var endPickerOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Período académico", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(cycle?.name ?: "Selecciona o crea un ciclo", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = cycle != null, onClick = { startPickerOpen = true }) {
                    Text(startDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Fecha inicio")
                }
                OutlinedButton(enabled = cycle != null, onClick = { endPickerOpen = true }) {
                    Text(endDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Fecha fin")
                }
            }
            Button(
                enabled = cycle != null && startDate != null && endDate != null,
                onClick = {
                    val start = startDate
                    val end = endDate
                    if (start == null || end == null) return@Button
                    if (end.isBefore(start)) error = "La fecha final no puede ser anterior a la inicial."
                    else {
                        error = null
                        onSave(start, end)
                    }
                }
            ) { Text("Guardar período") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (startPickerOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { startPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    startPickerOpen = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { startPickerOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    if (endPickerOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { endPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    endPickerOpen = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { endPickerOpen = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }
}