from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


ai = Path("app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt")

replace_once(
    ai,
    '''    val onlineConfigured = credentialStore.hasApiKey() && preferences.mistralAgentId.isNotBlank()\n    var section by remember { mutableIntStateOf(1) }\n    var openedMap by remember { mutableStateOf<ParsedStudyMapArtifact?>(null) }\n    var openedDeck by remember { mutableStateOf<ParsedFlashcardArtifact?>(null) }\n    var openedQuiz by remember { mutableStateOf<ParsedQuizArtifact?>(null) }\n''',
    '''    val onlineConfigured = credentialStore.hasApiKey() && preferences.mistralAgentId.isNotBlank()\n    val artifactStore = remember(context) { TuNotArtifactStore(context.applicationContext) }\n    val artifactScope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle.orEmpty()}" }\n    var artifactRevision by remember(artifactScope) { mutableIntStateOf(0) }\n    var autoSaveNextArtifact by remember(artifactScope) { mutableStateOf(false) }\n    var section by remember { mutableIntStateOf(1) }\n    var openedMap by remember { mutableStateOf<ParsedStudyMapArtifact?>(null) }\n    var openedDeck by remember { mutableStateOf<ParsedFlashcardArtifact?>(null) }\n    var openedQuiz by remember { mutableStateOf<ParsedQuizArtifact?>(null) }\n\n    LaunchedEffect(result, artifactScope, autoSaveNextArtifact) {\n        if (autoSaveNextArtifact && result.isNotBlank()) {\n            if (artifactStore.save(artifactScope, result) != null) {\n                artifactRevision += 1\n                autoSaveNextArtifact = false\n            }\n        }\n    }\n'''
)

replace_once(
    ai,
    '''                    onOpenMap = { openedMap = it },\n                    onOpenDeck = { openedDeck = it },\n                    onOpenQuiz = { openedQuiz = it }\n                )\n                else -> AiStudio(onlineConfigured, busy) { prompt ->\n                    onAsk(prompt)\n                    section = 1\n                }\n''',
    '''                    onOpenMap = { openedMap = it },\n                    onOpenDeck = { openedDeck = it },\n                    onOpenQuiz = { openedQuiz = it },\n                    onSaveArtifact = { raw ->\n                        if (artifactStore.save(artifactScope, raw) != null) artifactRevision += 1\n                    }\n                )\n                else -> AiStudio(\n                    subjectName = subjectName,\n                    classTitle = classTitle,\n                    configured = onlineConfigured,\n                    busy = busy,\n                    artifactRevision = artifactRevision,\n                    onAsk = { prompt, expectsArtifact ->\n                        autoSaveNextArtifact = expectsArtifact\n                        onAsk(prompt)\n                        section = 1\n                    },\n                    onOpenMap = { openedMap = it },\n                    onOpenDeck = { openedDeck = it },\n                    onOpenQuiz = { openedQuiz = it },\n                    onDeleteArtifact = { id ->\n                        artifactStore.delete(artifactScope, id)\n                        artifactRevision += 1\n                    }\n                )\n'''
)

replace_once(
    ai,
    '''    onOpenMap: (ParsedStudyMapArtifact) -> Unit,\n    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit\n) {\n''',
    '''    onOpenMap: (ParsedStudyMapArtifact) -> Unit,\n    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit,\n    onSaveArtifact: (String) -> Unit\n) {\n'''
)

replace_once(
    ai,
    '''                    ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, Modifier.weight(1f).fillMaxSize())\n''',
    '''                    ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxSize())\n'''
)
replace_once(
    ai,
    '''                ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, Modifier.weight(1f).fillMaxWidth())\n''',
    '''                ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxWidth())\n'''
)

replace_once(
    ai,
    '''    onOpenMap: (ParsedStudyMapArtifact) -> Unit,\n    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit,\n    modifier: Modifier = Modifier\n) {\n''',
    '''    onOpenMap: (ParsedStudyMapArtifact) -> Unit,\n    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit,\n    onSaveArtifact: (String) -> Unit,\n    modifier: Modifier = Modifier\n) {\n'''
)
replace_once(
    ai,
    '''                items(messages) { message -> ChatBubble(message, onOpenMap, onOpenDeck, onOpenQuiz) }\n''',
    '''                items(messages) { message -> ChatBubble(message, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact) }\n'''
)

replace_once(
    ai,
    '''    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit\n) {\n    val user = message.role == ChatRole.USER\n''',
    '''    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,\n    onOpenQuiz: (ParsedQuizArtifact) -> Unit,\n    onSaveArtifact: (String) -> Unit\n) {\n    val user = message.role == ChatRole.USER\n'''
)

# Add save action to all three artifact cards.
for action_line in [
    '                        onClick = { onOpenMap(artifact) }\n',
    '                        onClick = { onOpenDeck(deck) }\n',
    '                        onClick = { onOpenQuiz(quiz) }\n',
]:
    replace_once(
        ai,
        action_line,
        action_line.rstrip('\n') + ',\n                        onSave = { onSaveArtifact(message.rawContent) }\n'
    )

replace_once(
    ai,
    '''private fun ArtifactCard(icon: ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit) {\n''',
    '''private fun ArtifactCard(\n    icon: ImageVector,\n    title: String,\n    subtitle: String,\n    action: String,\n    onClick: () -> Unit,\n    onSave: (() -> Unit)? = null\n) {\n'''
)
replace_once(
    ai,
    '''            TextButton(onClick = onClick) { Text(action) }\n''',
    '''            onSave?.let { save -> TextButton(onClick = save) { Text("Guardar") } }\n            TextButton(onClick = onClick) { Text(action) }\n'''
)

# Replace AiStudio with a version that includes the saved-artifact library.
start = ai.read_text().index('@Composable\nprivate fun AiStudio(')
text = ai.read_text()
prefix = text[:start]
new_studio = r'''@Composable
private fun AiStudio(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    artifactRevision: Int,
    onAsk: (String, Boolean) -> Unit,
    onOpenMap: (ParsedStudyMapArtifact) -> Unit,
    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,
    onOpenQuiz: (ParsedQuizArtifact) -> Unit,
    onDeleteArtifact: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { TuNotArtifactStore(context.applicationContext) }
    val scope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle.orEmpty()}" }
    val savedArtifacts = remember(scope, artifactRevision) { store.load(scope) }
    val options = listOf(
        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),
        StudyTool("Tarjetas didácticas", "Repaso activo, una pregunta por tarjeta", Icons.Default.Style, "Crea entre 12 y 20 tarjetas didácticas de esta clase.", NotCanAiService.FLASHCARDS_MARKER),
        StudyTool("Cuestionario", "Respóndelo aquí y repite los errores", Icons.Default.Quiz, "Crea un cuestionario mixto basado exclusivamente en esta clase. Combina opción múltiple, verdadero/falso y algunas preguntas breves de desarrollo.", NotCanAiService.QUIZ_MARKER),
        StudyTool("Mapa mental", "Ramas y subramas interactivas", Icons.Default.AutoAwesome, "Hazme un mapa mental de esta clase. Organiza el tema central, ramas principales y subramas. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa conceptual", "Conceptos y relaciones etiquetadas", Icons.Default.Source, "Hazme un mapa conceptual de esta clase con conceptos, jerarquías y frases de enlace. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa de ideas", "Presentación visual en tarjetas", Icons.Default.Description, "Hazme un mapa de ideas de esta clase, más visual y sencillo, usando tarjetas radiales. Usa solamente las fuentes disponibles.")
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Herramientas de estudio", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("TuNot genera el material y NotCan lo guarda dentro de esta clase para reutilizarlo sin otra llamada al modelo.", color = NotCanGray)
        }
        items(options) { tool ->
            val expectsArtifact = tool.marker != null || tool.title.startsWith("Mapa")
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) {
                    onAsk(
                        buildString {
                            appendLine(NotCanAiService.SOURCE_ONLY_MARKER)
                            tool.marker?.let { appendLine(it) }
                            append(tool.prompt)
                        },
                        expectsArtifact
                    )
                },
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
                        Icon(tool.icon, null, tint = NotCanBlue, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, color = if (configured) NotCanOffWhite else NotCanGray, fontWeight = FontWeight.Medium)
                        Text(tool.subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (!configured) item { Text("Configura Mistral desde Configuración para activar estas herramientas.", color = NotCanGray) }

        item {
            Spacer(Modifier.padding(top = 4.dp))
            Text("Guardado en esta clase", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (savedArtifacts.isEmpty()) "Los mapas, tarjetas y cuestionarios que generes desde aquí aparecerán en esta sección." else "${savedArtifacts.size} material(es) disponibles sin volver a generar.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(savedArtifacts, key = { it.id }) { artifact ->
            val icon = when (artifact.kind) {
                StudyArtifactKind.MAP -> Icons.Default.AutoAwesome
                StudyArtifactKind.FLASHCARDS -> Icons.Default.Style
                StudyArtifactKind.QUIZ -> Icons.Default.Quiz
            }
            val kindLabel = when (artifact.kind) {
                StudyArtifactKind.MAP -> "Mapa"
                StudyArtifactKind.FLASHCARDS -> "Tarjetas"
                StudyArtifactKind.QUIZ -> "Cuestionario"
            }
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, tint = NotCanBlue)
                    Column(Modifier.weight(1f)) {
                        Text(artifact.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        Text(kindLabel, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        when (artifact.kind) {
                            StudyArtifactKind.MAP -> StudyMapArtifactParser.parse(artifact.rawContent)?.let(onOpenMap)
                            StudyArtifactKind.FLASHCARDS -> StudyFlashcardArtifactParser.parse(artifact.rawContent)?.let(onOpenDeck)
                            StudyArtifactKind.QUIZ -> StudyQuizArtifactParser.parse(artifact.rawContent)?.let(onOpenQuiz)
                        }
                    }) { Text("Abrir") }
                    TextButton(onClick = { onDeleteArtifact(artifact.id) }) { Text("Eliminar") }
                }
            }
        }
    }
}
'''
ai.write_text(prefix + new_studio)

workspace = Path("app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt")
replace_once(
    workspace,
    '''import androidx.compose.ui.platform.LocalConfiguration\n''',
    '''import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n'''
)
replace_once(
    workspace,
    '''import com.notcan.app.recording.RecordingState\n''',
    '''import com.notcan.app.recording.RecordingState\nimport com.notcan.app.ui.ai.StudyArtifactKind\nimport com.notcan.app.ui.ai.TuNotArtifactStore\n'''
)
replace_once(
    workspace,
    '''                    NormalClassTabs(\n                        classSessionId = classSession.id,\n''',
    '''                    NormalClassTabs(\n                        subjectName = subject?.name,\n                        classTitle = classSession.title,\n                        classSessionId = classSession.id,\n'''
)
replace_once(
    workspace,
    '''private fun NormalClassTabs(\n    classSessionId: String,\n''',
    '''private fun NormalClassTabs(\n    subjectName: String?,\n    classTitle: String,\n    classSessionId: String,\n'''
)
replace_once(
    workspace,
    '''                else -> StudyContentV5(transcripts, notePages, detectedCues)\n''',
    '''                else -> StudyContentV5(subjectName, classTitle, transcripts, notePages, detectedCues)\n'''
)
replace_once(
    workspace,
    '''@Composable\nprivate fun StudyContentV5(transcripts: List<TranscriptEntity>, notes: List<NotePageEntity>, cues: List<DetectedCueEntity>) {\n    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {\n        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {\n            Text("Estudio", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)\n            Text("${transcripts.size} transcripción(es) · ${notes.size} página(s) · ${cues.size} señal(es) académicas", color = NotCanGray)\n            Text("Las herramientas completas de resumen, cuestionario, oral, mapa mental y repaso están en IA → Estudio.", color = NotCanGray)\n        }\n    }\n}\n''',
    '''@Composable\nprivate fun StudyContentV5(\n    subjectName: String?,\n    classTitle: String,\n    transcripts: List<TranscriptEntity>,\n    notes: List<NotePageEntity>,\n    cues: List<DetectedCueEntity>\n) {\n    val context = LocalContext.current\n    val store = remember(context) { TuNotArtifactStore(context.applicationContext) }\n    val scope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle}" }\n    val artifacts = remember(scope) { store.load(scope) }\n\n    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {\n        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {\n            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {\n                Text("Estudio", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)\n                Text("${transcripts.size} transcripción(es) · ${notes.size} página(s) · ${cues.size} señal(es) académicas", color = NotCanGray)\n                Text(\n                    if (artifacts.isEmpty()) "Genera mapas, tarjetas o cuestionarios desde IA → Estudio; quedarán guardados aquí por clase." else "${artifacts.size} material(es) de TuNot guardados para esta clase.",\n                    color = if (artifacts.isEmpty()) NotCanGray else NotCanBlue\n                )\n            }\n        }\n        if (artifacts.isNotEmpty()) {\n            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                items(artifacts, key = { it.id }) { artifact ->\n                    val kind = when (artifact.kind) {\n                        StudyArtifactKind.MAP -> "Mapa"\n                        StudyArtifactKind.FLASHCARDS -> "Tarjetas"\n                        StudyArtifactKind.QUIZ -> "Cuestionario"\n                    }\n                    Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {\n                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {\n                            Column(Modifier.weight(1f)) {\n                                Text(artifact.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n                                Text("$kind · abrir desde IA → Estudio", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                            }\n                        }\n                    }\n                }\n            }\n        }\n    }\n}\n'''
)
