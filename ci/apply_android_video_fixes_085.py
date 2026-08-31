from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, content):
    (ROOT / path).write_text(content)

def once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing replacement: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Map: real clipping, readable virtual canvas, richer text
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt')
s = read(p)
if 'import androidx.compose.ui.draw.clipToBounds' not in s:
    s = s.replace('import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.draw.clipToBounds\n')
if 'import androidx.compose.ui.zIndex' not in s:
    s = s.replace('import androidx.compose.ui.unit.dp\n', 'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.zIndex\n')

old_toolbar = '''        // La barra vive fuera del lienzo transformable: mover/zoom nunca puede taparla.\n        StudyMapToolbar(\n            map = map,\n            style = layoutStyle,\n            zoom = zoom,\n            onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest++ },\n            onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },\n            onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },\n            onFit = { fitRequest++ },\n            onCenter = { centerRequest++ },\n            onExport = ::exportAndShare\n        )\n        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {\n'''
new_toolbar = '''        // La barra está fuera del lienzo y por encima de él en el árbol de dibujo.\n        Box(Modifier.fillMaxWidth().zIndex(3f)) {\n            StudyMapToolbar(\n                map = map,\n                style = layoutStyle,\n                zoom = zoom,\n                onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest++ },\n                onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },\n                onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },\n                onFit = { fitRequest++ },\n                onCenter = { centerRequest++ },\n                onExport = ::exportAndShare\n            )\n        }\n        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {\n'''
s = once(s, old_toolbar, new_toolbar, 'toolbar z-order and clipping')

old_nodes = '''            val nodes = remember(visibleMap, layoutStyle, widthPx, heightPx) {\n                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, widthPx, heightPx)\n            }\n            val positionedById = remember(nodes) { nodes.associateBy { it.node.id } }\n'''
new_nodes = '''            // El mapa usa un lienzo virtual grande. No se reduce el texto para intentar meterlo todo\n            // en la pantalla: el usuario puede desplazarse y hacer zoom libremente.\n            val textDemand = remember(visibleMap) {\n                visibleMap.nodes.sumOf { node ->\n                    val chars = node.title.length + (node.description?.length ?: 0)\n                    108f + (chars / 30f) * 14f\n                }\n            }\n            val virtualWidthPx = maxOf(\n                widthPx,\n                when (layoutStyle) {\n                    StudyMapLayoutStyle.HORIZONTAL_BRANCHES, StudyMapLayoutStyle.TREE -> 1750f\n                    else -> 1450f\n                }\n            )\n            val virtualHeightPx = maxOf(heightPx, textDemand * 0.72f, visibleMap.nodes.size * 145f)\n            val virtualWidthDp = with(density) { virtualWidthPx.toDp() }\n            val virtualHeightDp = with(density) { virtualHeightPx.toDp() }\n            val nodes = remember(visibleMap, layoutStyle, virtualWidthPx, virtualHeightPx) {\n                StudyMapLayoutEngine.layout(visibleMap, layoutStyle, virtualWidthPx, virtualHeightPx)\n            }\n            val positionedById = remember(nodes) { nodes.associateBy { it.node.id } }\n'''
s = once(s, old_nodes, new_nodes, 'virtual map canvas')
s = s.replace(').coerceIn(0.35f, 1.45f)', ').coerceIn(0.72f, 1.45f)', 1)
old_layer = '''                Box(\n                    Modifier\n                        .fillMaxSize()\n                        .graphicsLayer {\n'''
new_layer = '''                Box(\n                    Modifier\n                        .size(virtualWidthDp, virtualHeightDp)\n                        .graphicsLayer {\n'''
s = once(s, old_layer, new_layer, 'virtual transformed layer size')
write(p, s)

p = Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapArtifactParser.kt')
s = read(p)
s = s.replace('private const val MAX_NODES = 16', 'private const val MAX_NODES = 32')
s = s.replace('val title = compact(root.flexString("title").ifBlank { "Mapa de estudio" }, 54)', 'val title = compact(root.flexString("title").ifBlank { "Mapa de estudio" }, 96)')
s = s.replace('title = compact(item.flexString("title").ifBlank { "Concepto" }, if (level == 0) 48 else 34),', 'title = compact(item.flexString("title").ifBlank { "Concepto" }, if (level == 0) 140 else 120),')
s = s.replace('?.let { compact(it, 150) },', '?.let { compact(it, 1400) },')
s = s.replace('add(compact(refs.optString(j), 28))', 'add(compact(refs.optString(j), 80))')
s = s.replace('?.let { add(compact(it, 28)) }', '?.let { add(compact(it, 80)) }')
s = s.replace('?.let { compact(it, 22) }', '?.let { compact(it, 120) }')
# Repair common model JSON issues: raw line breaks inside strings and trailing commas.
if 'private fun repairModelJson' not in s:
    s = s.replace('''    private fun parseJson(root: JSONObject): ParsedStudyMapArtifact {\n''', '''    private fun repairModelJson(value: String): String {\n        val out = StringBuilder(value.length + 32)\n        var inString = false\n        var escaped = false\n        value.forEach { ch ->\n            if (inString) {\n                when {\n                    escaped -> { out.append(ch); escaped = false }\n                    ch == '\\\\' -> { out.append(ch); escaped = true }\n                    ch == '\"' -> { out.append(ch); inString = false }\n                    ch == '\\n' -> out.append("\\\\n")\n                    ch == '\\r' -> out.append("\\\\r")\n                    ch == '\\t' -> out.append("\\\\t")\n                    else -> out.append(ch)\n                }\n            } else {\n                if (ch == '\"') inString = true\n                out.append(ch)\n            }\n        }\n        return out.toString()\n            .replace('“', '\"').replace('”', '\"')\n            .replace(Regex(",\\\\s*([}\\\\]])"), "$1")\n    }\n\n    private fun parseJson(root: JSONObject): ParsedStudyMapArtifact {\n''')
    s = s.replace('return runCatching { parseJson(JSONObject(artifact.json)) }.getOrNull()', 'return runCatching { parseJson(JSONObject(repairModelJson(artifact.json))) }.getOrNull()')
write(p, s)

# ---------------------------------------------------------------------------
# 2) Quiz: keep Next/Result above Android navigation area
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/ai/StudyQuizScreen.kt')
s = read(p)
if 'import androidx.compose.foundation.layout.navigationBarsPadding' not in s:
    s = s.replace('import androidx.compose.foundation.layout.fillMaxWidth\n', 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.navigationBarsPadding\n')
s = s.replace('''                        Button(onClick = ::next, modifier = Modifier.fillMaxWidth().padding(16.dp)) {\n''', '''                        Button(\n                            onClick = ::next,\n                            modifier = Modifier\n                                .fillMaxWidth()\n                                .navigationBarsPadding()\n                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)\n                        ) {\n''')
s = s.replace('''        Modifier.fillMaxSize().padding(22.dp),\n''', '''        Modifier.fillMaxSize().navigationBarsPadding().padding(22.dp),\n''')
write(p, s)

# ---------------------------------------------------------------------------
# 3) Remove persistent transcript share overlay from V4
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV4.kt')
s = read(p)
s = s.replace('import android.content.Intent\n', '')
s = s.replace('import androidx.compose.material.icons.filled.Share\n', '')
s = s.replace('import androidx.compose.ui.platform.LocalContext\n', '')
s = s.replace('import com.notcan.app.ui.theme.NotCanBlue\n', '')
s = s.replace('''    val context = LocalContext.current\n    val latestTranscript = transcripts.firstOrNull()\n''', '')
start = s.find('''\n        if (latestTranscript != null && !recordingActive) {\n''')
if start >= 0:
    end = s.find('''\n        }\n    }\n}\n\ninternal const val NEW_CLASS_RECORDING_SENTINEL''', start)
    if end < 0:
        raise SystemExit('could not locate persistent share overlay end')
    # Keep the Box/function closing braces.
    s = s[:start] + '\n    }\n}\n\ninternal const val NEW_CLASS_RECORDING_SENTINEL' + s[end + len('\n        }\n    }\n}\n\ninternal const val NEW_CLASS_RECORDING_SENTINEL'):]
write(p, s)

# ---------------------------------------------------------------------------
# 4) Transcript share per item + open/delete saved Study artifacts in class
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt')
s = read(p)
if 'import android.content.Intent' not in s:
    s = s.replace('import android.media.MediaPlayer\n', 'import android.content.Intent\nimport android.media.MediaPlayer\n')
if 'import androidx.activity.compose.BackHandler' not in s:
    s = s.replace('import android.media.MediaPlayer\n', 'import android.media.MediaPlayer\nimport androidx.activity.compose.BackHandler\n')
if 'import androidx.compose.ui.window.Dialog' not in s:
    s = s.replace('import androidx.compose.ui.unit.dp\n', 'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n')
extra_ai = '''import com.notcan.app.ui.ai.ParsedFlashcardArtifact\nimport com.notcan.app.ui.ai.ParsedQuizArtifact\nimport com.notcan.app.ui.ai.StudyFlashcardArtifactParser\nimport com.notcan.app.ui.ai.StudyFlashcardsScreen\nimport com.notcan.app.ui.ai.StudyQuizArtifactParser\nimport com.notcan.app.ui.ai.StudyQuizScreen\n'''
if 'import com.notcan.app.ui.ai.ParsedFlashcardArtifact' not in s:
    s = s.replace('import com.notcan.app.ui.ai.StudyArtifactKind\n', extra_ai + 'import com.notcan.app.ui.ai.StudyArtifactKind\n')
extra_map = '''import com.notcan.app.ui.maps.ParsedStudyMapArtifact\nimport com.notcan.app.ui.maps.StudyMapArtifactParser\nimport com.notcan.app.ui.maps.StudyMapScreen\n'''
if 'import com.notcan.app.ui.maps.ParsedStudyMapArtifact' not in s:
    s = s.replace('import com.notcan.app.ui.theme.NotCanBlue\n', extra_map + 'import com.notcan.app.ui.theme.NotCanBlue\n')

old_row_sig = 'private fun TranscriptRowV5(transcript: TranscriptEntity, onDelete: () -> Unit) {\n    var confirmDelete by remember(transcript.id) { mutableStateOf(false) }'
new_row_sig = '''private fun TranscriptRowV5(transcript: TranscriptEntity, onDelete: () -> Unit) {\n    val context = LocalContext.current\n    var confirmDelete by remember(transcript.id) { mutableStateOf(false) }'''
s = once(s, old_row_sig, new_row_sig, 'transcript row context')
old_buttons = '''                Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))\n                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar transcripción", tint = NotCanRed) }\n'''
new_buttons = '''                Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))\n                IconButton(onClick = {\n                    val title = transcript.modelName ?: "Transcripción NotCan"\n                    val intent = Intent(Intent.ACTION_SEND)\n                        .setType("text/plain")\n                        .putExtra(Intent.EXTRA_SUBJECT, title)\n                        .putExtra(Intent.EXTRA_TEXT, transcript.body)\n                    context.startActivity(Intent.createChooser(intent, "Compartir transcripción"))\n                }) { Icon(Icons.Default.Share, "Compartir transcripción", tint = NotCanBlue) }\n                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar transcripción", tint = NotCanRed) }\n'''
s = once(s, old_buttons, new_buttons, 'per transcript share button')

start = s.index('@Composable\nprivate fun StudyContentV5(')
end = s.index('\n@Composable\nprivate fun EmptyClassWorkspaceV5', start)
new_study = r'''@Composable
private fun StudyContentV5(
    subjectName: String?,
    classTitle: String,
    transcripts: List<TranscriptEntity>,
    notes: List<NotePageEntity>,
    cues: List<DetectedCueEntity>
) {
    val context = LocalContext.current
    val store = remember(context) { TuNotArtifactStore(context.applicationContext) }
    val scope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle}" }
    var revision by remember(scope) { mutableIntStateOf(0) }
    val artifacts = remember(scope, revision) { store.load(scope) }
    var openedMap by remember(scope) { mutableStateOf<ParsedStudyMapArtifact?>(null) }
    var openedDeck by remember(scope) { mutableStateOf<ParsedFlashcardArtifact?>(null) }
    var openedQuiz by remember(scope) { mutableStateOf<ParsedQuizArtifact?>(null) }

    fun open(raw: String, kind: StudyArtifactKind) {
        when (kind) {
            StudyArtifactKind.MAP -> openedMap = StudyMapArtifactParser.parse(raw)
            StudyArtifactKind.FLASHCARDS -> openedDeck = StudyFlashcardArtifactParser.parse(raw)
            StudyArtifactKind.QUIZ -> openedQuiz = StudyQuizArtifactParser.parse(raw)
        }
    }

    openedMap?.let { artifact ->
        BackHandler { openedMap = null }
        Dialog(
            onDismissRequest = { openedMap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { openedMap = null }) { Text("Volver") }
                        Text(artifact.map.title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    StudyMapScreen(artifact.map, modifier = Modifier.weight(1f).fillMaxWidth(), initialLayout = artifact.preferredLayout)
                }
            }
        }
    }
    openedDeck?.let { deck -> StudyFlashcardsScreen(deck = deck, onBack = { openedDeck = null }) }
    openedQuiz?.let { quiz -> StudyQuizScreen(quiz = quiz, onBack = { openedQuiz = null }) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Estudio", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text("${transcripts.size} transcripción(es) · ${notes.size} página(s) · ${cues.size} señal(es) académicas", color = NotCanGray)
                Text(
                    if (artifacts.isEmpty()) "Genera mapas, tarjetas o cuestionarios desde IA → Estudio; quedarán guardados aquí por clase." else "${artifacts.size} material(es) de TuNot guardados para esta clase.",
                    color = if (artifacts.isEmpty()) NotCanGray else NotCanBlue
                )
            }
        }
        if (artifacts.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(artifacts, key = { it.id }) { artifact ->
                    val kind = when (artifact.kind) {
                        StudyArtifactKind.MAP -> "Mapa"
                        StudyArtifactKind.FLASHCARDS -> "Tarjetas"
                        StudyArtifactKind.QUIZ -> "Cuestionario"
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NotCanGraphite),
                        modifier = Modifier.fillMaxWidth().clickable { open(artifact.rawContent, artifact.kind) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(artifact.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                                Text("$kind · toca para abrir", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                store.delete(scope, artifact.id)
                                revision += 1
                            }) { Icon(Icons.Default.Delete, "Eliminar material", tint = NotCanRed) }
                        }
                    }
                }
            }
        }
    }
}
'''
s = s[:start] + new_study + s[end:]
write(p, s)

# ---------------------------------------------------------------------------
# 5) More tolerant flashcard/quiz JSON + never expose raw artifact JSON in chat
# ---------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/ai/TuNotStudyArtifacts.kt')
s = read(p)
if 'private fun repairStudyJson' not in s:
    insert_at = s.index('private fun extractStudyArtifact(')
    repair = r'''private fun repairStudyJson(value: String): String {
    val out = StringBuilder(value.length + 32)
    var inString = false
    var escaped = false
    value.forEach { ch ->
        if (inString) {
            when {
                escaped -> { out.append(ch); escaped = false }
                ch == '\\' -> { out.append(ch); escaped = true }
                ch == '"' -> { out.append(ch); inString = false }
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        } else {
            if (ch == '"') inString = true
            out.append(ch)
        }
    }
    return out.toString()
        .replace('“', '"').replace('”', '"')
        .replace(Regex(",\\s*([}\\]])"), "$1")
}

'''
    s = s[:insert_at] + repair + s[insert_at:]
s = s.replace('val root = JSONObject(artifact.json)', 'val root = JSONObject(repairStudyJson(artifact.json))')
s = s.replace('val root = runCatching { JSONObject(json) }.getOrNull() ?: return null', 'val root = runCatching { JSONObject(repairStudyJson(json)) }.getOrNull() ?: return null')
write(p, s)

p = Path('app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt')
s = read(p)
old_render = '''                if (message.content.isNotBlank()) TuNotRichText(message.content, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)\n'''
new_render = '''                val safeVisibleContent = if (user) message.content else sanitizeUnparsedArtifact(message.content)\n                if (safeVisibleContent.isNotBlank()) TuNotRichText(safeVisibleContent, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)\n'''
s = once(s, old_render, new_render, 'never render raw structured artifact')
write(p, s)

# ---------------------------------------------------------------------------
# 6) Version bump
# ---------------------------------------------------------------------------
p = Path('app/build.gradle.kts')
s = read(p)
s = once(s, 'versionCode = 21', 'versionCode = 22', 'versionCode 22')
s = once(s, 'versionName = "0.8.4"', 'versionName = "0.8.5"', 'versionName 0.8.5')
write(p, s)

print('NotCan Android 0.8.5 video fixes applied')
