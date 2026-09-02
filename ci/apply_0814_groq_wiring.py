from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:160]!r}")
    write(path, text.replace(old, new, 1))


# Preference: online Groq is preferred when configured; local Whisper remains fallback.
p = "app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt"
replace_once(
    p,
    '''    var autoTranscribeAfterRecording: Boolean\n        get() = prefs.getBoolean(KEY_AUTO_TRANSCRIBE, false)\n        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()\n\n''',
    '''    var autoTranscribeAfterRecording: Boolean\n        get() = prefs.getBoolean(KEY_AUTO_TRANSCRIBE, false)\n        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()\n\n    var preferOnlineTranscription: Boolean\n        get() = prefs.getBoolean(KEY_PREFER_ONLINE_TRANSCRIPTION, true)\n        set(value) = prefs.edit().putBoolean(KEY_PREFER_ONLINE_TRANSCRIPTION, value).apply()\n\n'''
)
replace_once(
    p,
    '        private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_recording"\n',
    '        private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe_recording"\n        private const val KEY_PREFER_ONLINE_TRANSCRIPTION = "prefer_online_transcription"\n'
)

# Auto-transcribe always enqueues; the worker chooses Groq/local/pending.
p = "app/src/main/java/com/notcan/app/recording/RecordingService.kt"
replace_once(
    p,
    '''                val preferences = NotCanPreferences(this@RecordingService)\n                if (preferences.autoTranscribeAfterRecording && WhisperModelManager(this@RecordingService).state() == WhisperModelState.INSTALLED) {\n                    BackgroundTranscriptionManager.enqueue(this@RecordingService, audioId, classSessionId, path, classTitle)\n                }\n''',
    '''                val preferences = NotCanPreferences(this@RecordingService)\n                if (preferences.autoTranscribeAfterRecording) {\n                    BackgroundTranscriptionManager.enqueue(this@RecordingService, audioId, classSessionId, path, classTitle)\n                }\n'''
)

# Hybrid background worker.
p = "app/src/main/java/com/notcan/app/localai/BackgroundTranscriptionWorker.kt"
replace_once(p, 'import android.content.Context\n', 'import android.content.Context\nimport android.net.ConnectivityManager\nimport android.net.NetworkCapabilities\n')
replace_once(p, 'import com.notcan.app.data.local.NotePageEntity\n', 'import com.notcan.app.ai.GroqCredentialsStore\nimport com.notcan.app.data.local.NotePageEntity\n')
replace_once(
    p,
    '''            val rawTranscription = LocalWhisperEngine(applicationContext).transcribeM4aDetailed(audio)\n            val transcription = AcademicTranscriptionContext.correct(rawTranscription, academicTerms)\n            val plainText = transcription.text.trim()\n''',
    '''            val preferences = NotCanPreferences(applicationContext)\n            val groqConfigured = preferences.preferOnlineTranscription &&\n                GroqCredentialsStore(applicationContext).hasApiKey()\n            val networkAvailable = isNetworkAvailable()\n\n            val provider: String\n            val modelName: String\n            val transcriptStatus: String\n            val transcription: WhisperTranscriptionResult\n\n            if (groqConfigured && networkAvailable) {\n                setForeground(foregroundInfo("Transcribiendo $displayName con Whisper Large V3 online…", online = true))\n                transcription = GroqTranscriptionService(applicationContext).transcribeM4aDetailed(\n                    audio = audio,\n                    terms = academicTerms,\n                    subjectName = subject?.name,\n                    classTitle = displayName\n                )\n                provider = "Groq online"\n                modelName = "${GroqTranscriptionService.DISPLAY_NAME} · español · literal"\n                transcriptStatus = "FINAL_GROQ_TIMED"\n            } else {\n                if (groqConfigured && !networkAvailable && WhisperModelManager(applicationContext).state() != WhisperModelState.INSTALLED) {\n                    notifyFailed(displayName, "Sin Internet y sin Whisper local instalado. Se reintentará después.")\n                    return Result.retry()\n                }\n                val rawLocal = LocalWhisperEngine(applicationContext).transcribeM4aDetailed(audio)\n                transcription = AcademicTranscriptionContext.correct(rawLocal, academicTerms)\n                provider = if (groqConfigured) "Whisper local · respaldo sin Internet" else "Whisper local"\n                modelName = if (academicTerms.isNotEmpty()) {\n                    "${WhisperModelSpec.DISPLAY_NAME} · contexto académico"\n                } else {\n                    WhisperModelSpec.DISPLAY_NAME\n                }\n                transcriptStatus = "FINAL_LOCAL_TIMED"\n            }\n            val plainText = transcription.text.trim()\n'''
)
replace_once(
    p,
    '''                    status = "FINAL_LOCAL_TIMED",\n                    modelName = if (academicTerms.isNotEmpty()) {\n                        "${WhisperModelSpec.DISPLAY_NAME} · contexto académico"\n                    } else {\n                        WhisperModelSpec.DISPLAY_NAME\n                    },\n''',
    '''                    status = transcriptStatus,\n                    modelName = modelName,\n'''
)
replace_once(p, '            notifyFinished(displayName, academicTerms.isNotEmpty())\n', '            notifyFinished(displayName, academicTerms.isNotEmpty(), provider)\n')
replace_once(
    p,
    '''        } catch (t: Throwable) {\n            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "No se pudo transcribir el audio")))\n        }\n    }\n''',
    '''        } catch (t: Throwable) {\n            val message = t.message ?: "No se pudo transcribir el audio"\n            notifyFailed(displayName, message)\n            Result.failure(workDataOf(KEY_ERROR to message))\n        }\n    }\n'''
)
replace_once(p, '    private fun foregroundInfo(message: String): ForegroundInfo {\n', '    private fun foregroundInfo(message: String, online: Boolean = false): ForegroundInfo {\n')
replace_once(
    p,
    '''            .setContentTitle("NotCan · Transcripción local")\n            .setContentText(message)\n            .setStyle(\n                NotificationCompat.BigTextStyle().bigText(\n                    "${WhisperModelSpec.DISPLAY_NAME} está transcribiendo en segundo plano. Puedes cerrar NotCan."\n                )\n            )\n''',
    '''            .setContentTitle(if (online) "NotCan · Transcripción online" else "NotCan · Transcripción final")\n            .setContentText(message)\n            .setStyle(\n                NotificationCompat.BigTextStyle().bigText(\n                    if (online) {\n                        "Whisper Large V3 de Groq está procesando el audio. El archivo original permanece guardado en NotCan."\n                    } else {\n                        "${WhisperModelSpec.DISPLAY_NAME} está transcribiendo en segundo plano. Puedes cerrar NotCan."\n                    }\n                )\n            )\n'''
)
replace_once(p, '    private fun notifyFinished(displayName: String, usedAcademicContext: Boolean) {\n', '    private fun notifyFinished(displayName: String, usedAcademicContext: Boolean, provider: String) {\n')
replace_once(
    p,
    '''                    if (usedAcademicContext) {\n                        "$displayName · apunte editable y capítulos creados"\n                    } else {\n                        "$displayName · apunte editable creado"\n                    }\n''',
    '''                    if (usedAcademicContext) {\n                        "$displayName · $provider · apunte y capítulos creados"\n                    } else {\n                        "$displayName · $provider · apunte editable creado"\n                    }\n'''
)
replace_once(
    p,
    '    companion object {\n',
    '''    private fun isNetworkAvailable(): Boolean {\n        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager\n        val network = manager.activeNetwork ?: return false\n        val capabilities = manager.getNetworkCapabilities(network) ?: return false\n        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&\n            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)\n    }\n\n    private fun notifyFailed(displayName: String, message: String) {\n        createChannel()\n        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager\n        manager.notify(\n            FAILED_NOTIFICATION_ID,\n            NotificationCompat.Builder(applicationContext, CHANNEL_ID)\n                .setSmallIcon(android.R.drawable.ic_dialog_alert)\n                .setContentTitle("No se pudo terminar la transcripción")\n                .setContentText("$displayName · ${message.take(120)}")\n                .setStyle(NotificationCompat.BigTextStyle().bigText(message))\n                .setAutoCancel(true)\n                .build()\n        )\n    }\n\n    companion object {\n'''
)
replace_once(p, '        private const val COMPLETED_NOTIFICATION_ID = 2402\n', '        private const val COMPLETED_NOTIFICATION_ID = 2402\n        private const val FAILED_NOTIFICATION_ID = 2403\n')

# Settings: Groq key/toggle plus clearer fallback language.
p = "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
replace_once(p, 'import com.notcan.app.ai.MistralCredentialsStore\n', 'import com.notcan.app.ai.GroqCredentialsStore\nimport com.notcan.app.ai.MistralCredentialsStore\n')
replace_once(p, '    val credentials = remember(context) { MistralCredentialsStore(context.applicationContext) }\n', '    val credentials = remember(context) { MistralCredentialsStore(context.applicationContext) }\n    val groqCredentials = remember(context) { GroqCredentialsStore(context.applicationContext) }\n')
replace_once(p, '    var autoTranscribe by remember { mutableStateOf(preferences.autoTranscribeAfterRecording) }\n', '    var autoTranscribe by remember { mutableStateOf(preferences.autoTranscribeAfterRecording) }\n    var preferOnlineTranscription by remember { mutableStateOf(preferences.preferOnlineTranscription) }\n')
replace_once(p, '    var apiKeyInput by remember { mutableStateOf("") }\n', '    var apiKeyInput by remember { mutableStateOf("") }\n    var groqApiKeyInput by remember { mutableStateOf("") }\n')
replace_once(p, '    var hasSavedKey by remember { mutableStateOf(runCatching { credentials.hasApiKey() }.getOrDefault(false)) }\n', '    var hasSavedKey by remember { mutableStateOf(runCatching { credentials.hasApiKey() }.getOrDefault(false)) }\n    var hasGroqKey by remember { mutableStateOf(runCatching { groqCredentials.hasApiKey() }.getOrDefault(false)) }\n')

anchor = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {\n            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {\n                Row(verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = NotCanBlue)\n'''
insert = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {\n            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {\n                Row(verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Default.Key, contentDescription = null, tint = NotCanBlue)\n                    Spacer(Modifier.width(8.dp))\n                    Column(Modifier.weight(1f)) {\n                        Text("Transcripción online · Groq", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)\n                        Text(\n                            if (hasGroqKey) "Whisper Large V3 listo" else "Añade una API key gratuita de Groq",\n                            color = if (hasGroqKey) NotCanBlue else NotCanGray,\n                            style = MaterialTheme.typography.bodySmall\n                        )\n                    }\n                    Switch(\n                        checked = preferOnlineTranscription,\n                        onCheckedChange = {\n                            preferOnlineTranscription = it\n                            preferences.preferOnlineTranscription = it\n                        }\n                    )\n                }\n\n                Text(\n                    "Cuando está activado y hay Internet, la transcripción final envía el audio a Groq y usa Whisper Large V3. Si no hay conexión, NotCan usa Whisper local cuando está instalado. El plan gratuito y sus límites dependen de Groq.",\n                    color = NotCanGray,\n                    style = MaterialTheme.typography.bodySmall\n                )\n\n                OutlinedTextField(\n                    value = groqApiKeyInput,\n                    onValueChange = { groqApiKeyInput = it; saveMessage = null },\n                    label = { Text(if (hasGroqKey) "Nueva API key de Groq (opcional)" else "API key de Groq") },\n                    supportingText = {\n                        Text(if (hasGroqKey) "Ya hay una clave cifrada. Déjalo vacío para conservarla." else "La clave se guarda cifrada en este dispositivo.")\n                    },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    visualTransformation = PasswordVisualTransformation(),\n                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)\n                )\n\n                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Button(\n                        enabled = hasGroqKey || groqApiKeyInput.isNotBlank(),\n                        onClick = {\n                            if (groqApiKeyInput.isNotBlank()) {\n                                runCatching { groqCredentials.saveApiKey(groqApiKeyInput) }\n                                    .onSuccess {\n                                        hasGroqKey = true\n                                        groqApiKeyInput = ""\n                                        preferOnlineTranscription = true\n                                        preferences.preferOnlineTranscription = true\n                                        saveMessage = "Groq guardado. La transcripción online está activada."\n                                    }\n                                    .onFailure { saveMessage = it.message ?: "No se pudo guardar la clave de Groq" }\n                            } else {\n                                preferOnlineTranscription = true\n                                preferences.preferOnlineTranscription = true\n                                saveMessage = "Transcripción online activada."\n                            }\n                        }\n                    ) { Text("Guardar y activar") }\n                    if (hasGroqKey) {\n                        OutlinedButton(onClick = {\n                            runCatching { groqCredentials.clearApiKey() }\n                            hasGroqKey = false\n                            groqApiKeyInput = ""\n                            saveMessage = "API key de Groq eliminada."\n                        }) { Text("Eliminar clave") }\n                    }\n                }\n            }\n        }\n\n''' + anchor
replace_once(p, anchor, insert)
replace_once(p, '                    title = "Transcripción final",\n                    subtitle = WhisperModelSpec.DISPLAY_NAME,\n', '                    title = "Transcripción final · respaldo offline",\n                    subtitle = "${WhisperModelSpec.DISPLAY_NAME} · se usa sin Internet o si Groq está desactivado",\n')
replace_once(p, '            subtitle = "Si Whisper está instalado, prepara automáticamente la transcripción final.",\n', '            subtitle = "Al detener la grabación, usa Groq online si está configurado; sin Internet intenta Whisper local.",\n')

# 0.8.14 test build.
p = "app/build.gradle.kts"
replace_once(p, '        versionCode = 30\n        versionName = "0.8.13"\n', '        versionCode = 31\n        versionName = "0.8.14"\n')

print("0.8.14 Groq wiring patch applied successfully")
