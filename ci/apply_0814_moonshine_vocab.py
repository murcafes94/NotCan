from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:160]!r}")
    write(path, text.replace(old, new, 1))


# 1) Academic vocabulary: add Christology/concilia terms used in real classes.
p = "app/src/main/java/com/notcan/app/localai/AcademicTranscriptionSupport.kt"
replace_once(
    p,
    '        "Padres apostólicos", "Patrística", "ontología", "metafísica",\n',
    '        "Padres apostólicos", "Patrística", "Cristología bíblica", "Cristología patrística",\n'
    '        "Nicea", "Constantinopla", "Éfeso", "Calcedonia", "Concilio de Nicea",\n'
    '        "Concilio de Constantinopla", "Concilio de Éfeso", "Concilio de Calcedonia",\n'
    '        "símbolo niceno", "Credo niceno-constantinopolitano", "naturaleza y persona",\n'
    '        "conciencia de Cristo", "ontología", "metafísica",\n'
)

# 2) Expose a stricter correction path for live Moonshine chunks.
replace_once(
    p,
    '    private fun correctText(text: String, terms: List<AcademicTranscriptionTerm>): String {\n',
    '''    /**\n     * Corrección conservadora para Moonshine en vivo. Solo usa vocabulario explícito\n     * o contexto de muy alta prioridad y limita la distancia de edición a 2.\n     * El texto acústico bruto se conserva aparte por RecordingService.\n     */\n    fun correctLiveText(text: String, terms: List<AcademicTranscriptionTerm>): String {\n        if (text.isBlank() || terms.isEmpty()) return text\n        val safeTerms = terms.filter { term ->\n            term.value.length >= 6 && (term.explicit || term.weight >= 2.5f)\n        }\n        if (safeTerms.isEmpty()) return text\n        return correctText(text, safeTerms, maxDistance = 2)\n    }\n\n    private fun correctText(\n        text: String,\n        terms: List<AcademicTranscriptionTerm>,\n        maxDistance: Int? = null\n    ): String {\n'''
)
replace_once(
    p,
    '                val allowed = allowedDistance(candidate.normalized.length, count, candidate.explicit)\n',
    '                val baseAllowed = allowedDistance(candidate.normalized.length, count, candidate.explicit)\n'
    '                val allowed = maxDistance?.let { cap -> minOf(baseAllowed, cap) } ?: baseAllowed\n'
)

# 3) RecordingService: build vocabulary before Moonshine starts and apply only safe live correction.
p = "app/src/main/java/com/notcan/app/recording/RecordingService.kt"
replace_once(
    p,
    'import com.notcan.app.localai.BackgroundTranscriptionManager\n',
    'import com.notcan.app.localai.AcademicTranscriptionContext\nimport com.notcan.app.localai.BackgroundTranscriptionManager\n'
)
replace_once(
    p,
    'import kotlinx.coroutines.flow.asStateFlow\n',
    'import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.flow.first\n'
)
replace_once(
    p,
    '    private var liveTranscriber: LocalLiveTranscriber? = null\n',
    '    private var liveTranscriber: LocalLiveTranscriber? = null\n    private var liveRawTranscript: String = ""\n'
)
replace_once(
    p,
    '            _liveTranscript.value = ""\n\n            val requestedLive',
    '            _liveTranscript.value = ""\n            liveRawTranscript = ""\n\n            val requestedLive'
)
old_block = '''            if (liveEnabled && channel != null) {\n                val transcriber = LocalLiveTranscriber(\n                    modelManager = liveManager,\n                    onTranscriptChunk = { chunk ->\n                        _liveTranscript.update { current -> if (current.isBlank()) chunk.trim() else "$current ${chunk.trim()}" }\n                    },\n                    onStatus = { _aiStatus.value = it }\n                )\n                liveTranscriber = transcriber\n                serviceScope.launch {\n                    val active = transcriber.start()\n                    if (!active) {\n                        channel.close()\n                        return@launch\n                    }\n                    liveSenderJob = launch {\n                        for (pcm in channel) {\n                            if (!isActive) break\n                            transcriber.acceptPcm16k(pcm)\n                        }\n                    }\n                }\n            }\n'''
new_block = '''            if (liveEnabled && channel != null) {\n                serviceScope.launch {\n                    val academicTerms = runCatching {\n                        val classSession = dao.getClassSession(classSessionId)\n                        val subject = classSession?.let { dao.getSubject(it.subjectId) }\n                        val storedVocabulary = subject?.let { selectedSubject ->\n                            dao.observeVocabularyForCycle(selectedSubject.cycleId)\n                                .first()\n                                .filter { term -> term.subjectId == null || term.subjectId == selectedSubject.id }\n                        }.orEmpty()\n                        val noteContext = dao.getNotesForClass(classSessionId)\n                            .flatMap { note -> listOf(note.title, note.body) }\n                        AcademicTranscriptionContext.buildTerms(\n                            subjectName = subject?.name,\n                            classTitle = currentClassTitle,\n                            stored = storedVocabulary,\n                            contextTexts = noteContext\n                        )\n                    }.getOrDefault(emptyList())\n\n                    val transcriber = LocalLiveTranscriber(\n                        modelManager = liveManager,\n                        onTranscriptChunk = { chunk ->\n                            val raw = chunk.trim()\n                            if (raw.isNotBlank()) {\n                                liveRawTranscript = if (liveRawTranscript.isBlank()) raw else "$liveRawTranscript $raw"\n                                val corrected = AcademicTranscriptionContext.correctLiveText(raw, academicTerms).trim()\n                                if (corrected.isNotBlank()) {\n                                    _liveTranscript.update { current ->\n                                        if (current.isBlank()) corrected else "$current $corrected"\n                                    }\n                                }\n                            }\n                        },\n                        onStatus = { status ->\n                            _aiStatus.value = if (academicTerms.isNotEmpty() && status.startsWith("Transcripción en vivo")) {\n                                "$status · vocabulario académico"\n                            } else status\n                        }\n                    )\n                    liveTranscriber = transcriber\n                    val active = transcriber.start()\n                    if (!active) {\n                        channel.close()\n                        return@launch\n                    }\n                    liveSenderJob = launch {\n                        for (pcm in channel) {\n                            if (!isActive) break\n                            transcriber.acceptPcm16k(pcm)\n                        }\n                    }\n                }\n            }\n'''
replace_once(p, old_block, new_block)

# Preserve raw Moonshine output in a sidecar, while the UI/database receives the conservative revision.
replace_once(
    p,
    '''                val liveText = _liveTranscript.value.trim()\n                if (liveText.isNotBlank()) {\n                    val now = System.currentTimeMillis()\n                    dao.insertTranscript(\n                        TranscriptEntity(UUID.randomUUID().toString(), classSessionId, audioId, liveText, "LIVE_LOCAL_PROVISIONAL", "moonshine-base-es", createdAt, now)\n                    )\n                }\n''',
    '''                val liveText = _liveTranscript.value.trim()\n                val rawLiveText = liveRawTranscript.trim()\n                if (rawLiveText.isNotBlank() && rawLiveText != liveText) {\n                    runCatching { File("$path.moonshine.raw.txt").writeText(rawLiveText) }\n                }\n                if (liveText.isNotBlank()) {\n                    val now = System.currentTimeMillis()\n                    val liveModelName = if (rawLiveText.isNotBlank() && rawLiveText != liveText) {\n                        "moonshine-base-es · vocabulario académico"\n                    } else {\n                        "moonshine-base-es"\n                    }\n                    dao.insertTranscript(\n                        TranscriptEntity(UUID.randomUUID().toString(), classSessionId, audioId, liveText, "LIVE_LOCAL_PROVISIONAL", liveModelName, createdAt, now)\n                    )\n                }\n'''
)
replace_once(
    p,
    '        liveTranscriber = null\n        autoStopMode = AUTO_STOP_ASK\n',
    '        liveTranscriber = null\n        liveRawTranscript = ""\n        autoStopMode = AUTO_STOP_ASK\n'
)

# 4) Make this installable over 0.8.14.1.
p = "app/build.gradle.kts"
replace_once(p, '        versionCode = 32\n        versionName = "0.8.14.1"\n', '        versionCode = 33\n        versionName = "0.8.14.2"\n')

print("Moonshine academic vocabulary patch applied")
