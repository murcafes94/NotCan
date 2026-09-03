from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:180]!r}")
    write(path, text.replace(old, new, 1))


# 1) Central trace store: preserve raw recognizer output + final metadata sidecars.
write(
    "app/src/main/java/com/notcan/app/localai/TranscriptionTraceStore.kt",
    '''package com.notcan.app.localai

import org.json.JSONObject
import java.io.File

/**
 * Keeps a private audit trail next to each recording so NotCan can compare what the
 * recognizer heard with the user-facing transcript without polluting Room or TuNot context.
 */
object TranscriptionTraceStore {
    fun writeRaw(audio: File, stage: String, text: String): File? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        return runCatching {
            File("${audio.absolutePath}.$stage.raw.txt").also { it.writeText(clean) }
        }.getOrNull()
    }

    fun writeMetadata(
        audio: File,
        provider: String,
        model: String,
        finalStatus: String,
        academicTermCount: Int,
        rawFile: File?,
        postCorrectionApplied: Boolean
    ) {
        runCatching {
            val json = JSONObject()
                .put("provider", provider)
                .put("model", model)
                .put("finalStatus", finalStatus)
                .put("academicTermCount", academicTermCount)
                .put("recognitionContextUsed", academicTermCount > 0)
                .put("postCorrectionApplied", postCorrectionApplied)
                .put("rawSidecar", rawFile?.name ?: JSONObject.NULL)
                .put("updatedAtEpochMs", System.currentTimeMillis())
            File("${audio.absolutePath}.transcription.json").writeText(json.toString(2))
        }
    }

    fun deleteForAudio(audio: File) {
        val prefix = audio.absolutePath
        listOf(
            "$prefix.moonshine.raw.txt",
            "$prefix.whisper.local.raw.txt",
            "$prefix.whisper.groq.raw.txt",
            "$prefix.transcription.json",
            "$prefix.markers.csv"
        ).forEach { path -> runCatching { File(path).delete() } }
    }
}
'''
)

# 2) Shared selection policy: once Whisper final exists, do not duplicate Moonshine in TuNot.
write(
    "app/src/main/java/com/notcan/app/localai/TranscriptionSelection.kt",
    '''package com.notcan.app.localai

import com.notcan.app.data.local.TranscriptEntity

object TranscriptionSelection {
    fun preferredForAi(items: List<TranscriptEntity>): List<TranscriptEntity> {
        val usable = items.filterNot { transcript ->
            transcript.status.startsWith("PROCESSING", ignoreCase = true) ||
                transcript.status.startsWith("WAITING", ignoreCase = true) ||
                transcript.status.startsWith("FAILED", ignoreCase = true)
        }
        return usable
            .groupBy { it.audioId ?: it.id }
            .values
            .mapNotNull { group ->
                group.filter { it.status.startsWith("FINAL", ignoreCase = true) }
                    .maxByOrNull { it.updatedAtEpochMs }
                    ?: group.maxByOrNull { it.updatedAtEpochMs }
            }
            .sortedBy { it.createdAtEpochMs }
    }
}
'''
)

# 3) RecordingService: route Moonshine raw trace through central store.
p = "app/src/main/java/com/notcan/app/recording/RecordingService.kt"
replace_once(
    p,
    'import com.notcan.app.localai.LocalLiveTranscriber\n',
    'import com.notcan.app.localai.LocalLiveTranscriber\nimport com.notcan.app.localai.TranscriptionTraceStore\n'
)
replace_once(
    p,
    '''                if (rawLiveText.isNotBlank() && rawLiveText != liveText) {
                    runCatching { File("$path.moonshine.raw.txt").writeText(rawLiveText) }
                }
''',
    '''                if (rawLiveText.isNotBlank() && rawLiveText != liveText) {
                    TranscriptionTraceStore.writeRaw(file, "moonshine", rawLiveText)
                }
'''
)

# 4) Background final transcription: visible processing row, raw Whisper sidecars and metadata.
p = "app/src/main/java/com/notcan/app/localai/BackgroundTranscriptionWorker.kt"
replace_once(
    p,
    '''            val dao = NotCanDatabase.getInstance(applicationContext).dao()
            val classSession = dao.getClassSession(classSessionId)
''',
    '''            val dao = NotCanDatabase.getInstance(applicationContext).dao()
            val transcriptId = "final-$audioId"
            val processingNow = System.currentTimeMillis()
            dao.insertTranscript(
                TranscriptEntity(
                    id = transcriptId,
                    classSessionId = classSessionId,
                    audioId = audioId,
                    body = "Procesando transcripción final…",
                    status = "PROCESSING_FINAL",
                    modelName = "Whisper · procesando",
                    createdAtEpochMs = processingNow,
                    updatedAtEpochMs = processingNow
                )
            )
            val classSession = dao.getClassSession(classSessionId)
'''
)
replace_once(
    p,
    '''                transcription = GroqTranscriptionService(applicationContext).transcribeM4aDetailed(
                    audio = audio,
                    terms = academicTerms,
                    subjectName = subject?.name,
                    classTitle = displayName
                )
                provider = "Groq online"
                modelName = "${GroqTranscriptionService.DISPLAY_NAME} · español · literal"
                transcriptStatus = "FINAL_GROQ_TIMED"
''',
    '''                transcription = GroqTranscriptionService(applicationContext).transcribeM4aDetailed(
                    audio = audio,
                    terms = academicTerms,
                    subjectName = subject?.name,
                    classTitle = displayName
                )
                val rawSidecar = TranscriptionTraceStore.writeRaw(audio, "whisper.groq", transcription.text)
                provider = "Groq online"
                modelName = "${GroqTranscriptionService.DISPLAY_NAME} · español · literal"
                transcriptStatus = "FINAL_GROQ_TIMED"
                TranscriptionTraceStore.writeMetadata(
                    audio = audio,
                    provider = provider,
                    model = modelName,
                    finalStatus = transcriptStatus,
                    academicTermCount = academicTerms.size,
                    rawFile = rawSidecar,
                    postCorrectionApplied = false
                )
'''
)
replace_once(
    p,
    '''                if (groqConfigured && !networkAvailable && WhisperModelManager(applicationContext).state() != WhisperModelState.INSTALLED) {
                    notifyFailed(displayName, "Sin Internet y sin Whisper local instalado. Se reintentará después.")
                    return Result.retry()
                }
                val rawLocal = LocalWhisperEngine(applicationContext).transcribeM4aDetailed(audio)
                transcription = AcademicTranscriptionContext.correct(rawLocal, academicTerms)
''',
    '''                if (groqConfigured && !networkAvailable && WhisperModelManager(applicationContext).state() != WhisperModelState.INSTALLED) {
                    val waitingNow = System.currentTimeMillis()
                    dao.insertTranscript(
                        TranscriptEntity(
                            id = transcriptId,
                            classSessionId = classSessionId,
                            audioId = audioId,
                            body = "Esperando Internet o Whisper local para terminar la transcripción…",
                            status = "WAITING_FINAL",
                            modelName = "Whisper · en espera",
                            createdAtEpochMs = processingNow,
                            updatedAtEpochMs = waitingNow
                        )
                    )
                    notifyFailed(displayName, "Sin Internet y sin Whisper local instalado. Se reintentará después.")
                    return Result.retry()
                }
                val rawLocal = LocalWhisperEngine(applicationContext).transcribeM4aDetailed(audio)
                val rawSidecar = TranscriptionTraceStore.writeRaw(audio, "whisper.local", rawLocal.text)
                transcription = AcademicTranscriptionContext.correct(rawLocal, academicTerms)
'''
)
replace_once(
    p,
    '''                transcriptStatus = "FINAL_LOCAL_TIMED"
            }
''',
    '''                transcriptStatus = "FINAL_LOCAL_TIMED"
                TranscriptionTraceStore.writeMetadata(
                    audio = audio,
                    provider = provider,
                    model = modelName,
                    finalStatus = transcriptStatus,
                    academicTermCount = academicTerms.size,
                    rawFile = rawSidecar,
                    postCorrectionApplied = academicTerms.isNotEmpty() && rawLocal.text.trim() != transcription.text.trim()
                )
            }
'''
)
replace_once(
    p,
    '''            val now = System.currentTimeMillis()
            val transcriptId = "final-$audioId"
            dao.insertTranscript(
''',
    '''            val now = System.currentTimeMillis()
            dao.insertTranscript(
'''
)
# On terminal failure, replace the processing card with an explicit failure state.
replace_once(
    p,
    '''        } catch (t: Throwable) {
            val message = t.message ?: "No se pudo transcribir el audio"
            notifyFailed(displayName, message)
            Result.failure(workDataOf(KEY_ERROR to message))
        }
''',
    '''        } catch (t: Throwable) {
            val message = t.message ?: "No se pudo transcribir el audio"
            runCatching {
                val failedNow = System.currentTimeMillis()
                NotCanDatabase.getInstance(applicationContext).dao().insertTranscript(
                    TranscriptEntity(
                        id = "final-$audioId",
                        classSessionId = classSessionId,
                        audioId = audioId,
                        body = "No se pudo completar la transcripción final. Puedes reintentarla desde esta clase.",
                        status = "FAILED_FINAL",
                        modelName = "Whisper · fallo",
                        createdAtEpochMs = failedNow,
                        updatedAtEpochMs = failedNow
                    )
                )
            }
            notifyFailed(displayName, message)
            Result.failure(workDataOf(KEY_ERROR to message))
        }
'''
)

# 5) UI: explicit provisional / processing / final provenance, and manual hybrid route.
p = "app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt"
replace_once(
    p,
    '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
            }
''',
    '''            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Moonshine · provisional", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
            }
'''
)
replace_once(
    p,
    '''    val latestAudio = audioRecordings.firstOrNull()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
''',
    '''    val latestAudio = audioRecordings.firstOrNull()
    val pipelineBusy = busy || transcripts.any {
        it.status.startsWith("PROCESSING", ignoreCase = true) || it.status.startsWith("WAITING", ignoreCase = true)
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
'''
)
replace_once(
    p,
    '''                    Text(
                        when (modelState) {
                            WhisperModelState.INSTALLED -> "Whisper large-v3-turbo listo. Puede continuar en segundo plano."
                            WhisperModelState.DOWNLOADING -> "Whisper se está descargando en segundo plano."
                            WhisperModelState.NOT_INSTALLED -> "Descarga Whisper desde IA → Fuentes. La transcripción provisional de clase usa Moonshine."
                        },
                        color = NotCanGray
                    )
                    Button(enabled = latestAudio != null && modelState == WhisperModelState.INSTALLED && !busy, onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }) {
                        Text(if (busy) "Procesando…" else "Transcribir último audio")
                    }
''',
    '''                    Text(
                        when {
                            transcripts.any { it.status.startsWith("PROCESSING", ignoreCase = true) } -> "Whisper está procesando el audio. El original permanece guardado."
                            transcripts.any { it.status.startsWith("WAITING", ignoreCase = true) } -> "En espera: NotCan usará Groq al recuperar Internet o Whisper local si está instalado."
                            modelState == WhisperModelState.INSTALLED -> "Ruta híbrida lista: Groq Whisper Large V3 online; Whisper local como respaldo."
                            modelState == WhisperModelState.DOWNLOADING -> "Whisper local se está descargando. Groq puede usarse online si está configurado."
                            else -> "Groq puede hacer la transcripción final online; Moonshine sigue siendo solo provisional."
                        },
                        color = NotCanGray
                    )
                    Button(enabled = latestAudio != null && !pipelineBusy, onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }) {
                        Text(if (pipelineBusy) "Procesando…" else "Generar transcripción final")
                    }
'''
)
replace_once(
    p,
    '''private fun TranscriptRowV5(transcript: TranscriptEntity, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember(transcript.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val title = transcript.modelName ?: "Transcripción NotCan"
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, title)
                        .putExtra(Intent.EXTRA_TEXT, transcript.body)
                    context.startActivity(Intent.createChooser(intent, "Compartir transcripción"))
                }) { Icon(Icons.Default.Share, "Compartir transcripción", tint = NotCanBlue) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar transcripción", tint = NotCanRed) }
            }
            Spacer(Modifier.height(5.dp))
            Text(transcript.body, color = NotCanOffWhite)
        }
    }
''',
    '''private fun TranscriptRowV5(transcript: TranscriptEntity, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember(transcript.id) { mutableStateOf(false) }
    val stateLabel = when {
        transcript.status.startsWith("LIVE", ignoreCase = true) -> "Provisional · Moonshine"
        transcript.status.startsWith("PROCESSING", ignoreCase = true) -> "Procesando final"
        transcript.status.startsWith("WAITING", ignoreCase = true) -> "En espera"
        transcript.status.startsWith("FINAL_GROQ", ignoreCase = true) -> "Final · Whisper Large V3 online"
        transcript.status.startsWith("FINAL_LOCAL", ignoreCase = true) -> "Final · Whisper local"
        transcript.status.startsWith("FAILED", ignoreCase = true) -> "No completada"
        else -> "Guardada"
    }
    val stateColor = when {
        transcript.status.startsWith("FAILED", ignoreCase = true) -> NotCanRed
        transcript.status.startsWith("FINAL", ignoreCase = true) -> NotCanBlue
        else -> NotCanGray
    }
    val canShare = !transcript.status.startsWith("PROCESSING", ignoreCase = true) &&
        !transcript.status.startsWith("WAITING", ignoreCase = true) &&
        !transcript.status.startsWith("FAILED", ignoreCase = true)
    Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium)
                    Text(stateLabel, color = stateColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                if (canShare) {
                    IconButton(onClick = {
                        val title = transcript.modelName ?: "Transcripción NotCan"
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, title)
                            .putExtra(Intent.EXTRA_TEXT, transcript.body)
                        context.startActivity(Intent.createChooser(intent, "Compartir transcripción"))
                    }) { Icon(Icons.Default.Share, "Compartir transcripción", tint = NotCanBlue) }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar transcripción", tint = NotCanRed) }
            }
            Spacer(Modifier.height(5.dp))
            Text(transcript.body, color = if (canShare) NotCanOffWhite else NotCanGray)
        }
    }
'''
)

# 6) TuNot and deletion: use final over provisional and clean private sidecars with audio.
p = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
replace_once(
    p,
    'import com.notcan.app.localai.WhisperModelState\n',
    'import com.notcan.app.localai.WhisperModelState\nimport com.notcan.app.localai.TranscriptionSelection\nimport com.notcan.app.localai.TranscriptionTraceStore\n'
)
replace_once(
    p,
    '''        viewModelScope.launch(Dispatchers.IO) {
            File(audio.localPath).delete()
            repository.deleteAudio(audio.id)
        }
''',
    '''        viewModelScope.launch(Dispatchers.IO) {
            val audioFile = File(audio.localPath)
            TranscriptionTraceStore.deleteForAudio(audioFile)
            audioFile.delete()
            repository.deleteAudio(audio.id)
        }
'''
)
replace_once(
    p,
    '                    append(transcripts.value.joinToString("\\n\\n") { it.body })\n',
    '                    append(TranscriptionSelection.preferredForAi(transcripts.value).joinToString("\\n\\n") { it.body })\n'
)

# 7) Quick assistant sources in MainActivity follow the same final-over-provisional policy.
p = "app/src/main/java/com/notcan/app/MainActivity.kt"
replace_once(
    p,
    'import com.notcan.app.localai.BackgroundTranscriptionManager\n',
    'import com.notcan.app.localai.BackgroundTranscriptionManager\nimport com.notcan.app.localai.TranscriptionSelection\n'
)
replace_once(
    p,
    '''                    transcripts.forEachIndexed { index, transcript ->
                        add(
                            TuNotOfflineEntry(
                                title = "Transcripción ${index + 1}",
                                subtitle = selectedClass?.title ?: "Transcripción guardada",
                                text = transcript.body
                            )
                        )
                    }
''',
    '''                    TranscriptionSelection.preferredForAi(transcripts).forEachIndexed { index, transcript ->
                        add(
                            TuNotOfflineEntry(
                                title = "Transcripción ${index + 1}",
                                subtitle = selectedClass?.title ?: "Transcripción guardada",
                                text = transcript.body
                            )
                        )
                    }
'''
)

# 8) Version bump.
p = "app/build.gradle.kts"
replace_once(p, '        versionCode = 44\n        versionName = "0.8.21"\n', '        versionCode = 45\n        versionName = "0.8.22"\n')

print("NotCan 0.8.22 transcription pipeline preparation applied")
