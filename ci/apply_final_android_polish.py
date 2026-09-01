from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def regex_once(path, pattern, replacement):
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"Regex expected once in {path}, got {count}: {pattern[:100]!r}")
    write(path, updated)


# MainActivity: persistent theme state, contextual class navigation and Android DND.
p = "app/src/main/java/com/notcan/app/MainActivity.kt"
replace_once(p, "import android.Manifest\n", "import android.Manifest\nimport android.app.NotificationManager\n")
replace_once(p, "import android.os.Bundle\n", "import android.os.Bundle\nimport android.provider.Settings\n")
replace_once(
    p,
    "import androidx.compose.runtime.LaunchedEffect\n",
    "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\n",
)
replace_once(
    p,
    "    private var pendingCalendarScheduleId: String? = null\n",
    "    private var pendingCalendarScheduleId: String? = null\n    private var previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL\n    private var notCanDndEnabled = false\n",
)
replace_once(
    p,
    "        setContent {\n            NotCanTheme {\n",
    "        setContent {\n            var darkTheme by remember { mutableStateOf(preferences.darkTheme) }\n            var classNavigationRequest by remember { mutableIntStateOf(0) }\n            NotCanTheme(darkTheme = darkTheme) {\n",
)
replace_once(
    p,
    "                    tasks = taskItems,\n                    recordingActive = recordingActive,\n                    autoFocusOnRecording = { preferences.autoFocusOnRecording },\n",
    "                    tasks = taskItems,\n                    recordingActive = recordingActive,\n                    subjectContextActive = selectedSubject != null,\n                    subjectsTitle = selectedClass?.title ?: selectedSubject?.name ?: \"Materias\",\n                    darkTheme = darkTheme,\n                    onToggleTheme = { darkTheme = !darkTheme; preferences.darkTheme = darkTheme },\n                    onToggleDoNotDisturb = ::toggleDoNotDisturb,\n                    onOpenClasses = { classNavigationRequest++ },\n",
)
replace_once(
    p,
    "                            selectedNoteId = selectedNoteId,\n",
    "                            selectedNoteId = selectedNoteId,\n                            classNavigationRequest = classNavigationRequest,\n",
)
replace_once(
    p,
    "    private fun requestPermissionsAndStart(classSessionId: String, plannedEndEpochMs: Long? = null, autoStopMode: String? = null, graceMinutes: Int? = null) {\n",
    "    private fun toggleDoNotDisturb() {\n        val manager = getSystemService(NotificationManager::class.java)\n        if (!manager.isNotificationPolicyAccessGranted) {\n            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }\n            Toast.makeText(this, \"Permite a NotCan controlar No molestar una sola vez\", Toast.LENGTH_LONG).show()\n            return\n        }\n        if (!notCanDndEnabled) {\n            previousInterruptionFilter = manager.currentInterruptionFilter\n            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)\n            notCanDndEnabled = true\n            Toast.makeText(this, \"No molestar activado 🤫\", Toast.LENGTH_SHORT).show()\n        } else {\n            manager.setInterruptionFilter(previousInterruptionFilter)\n            notCanDndEnabled = false\n            Toast.makeText(this, \"No molestar desactivado\", Toast.LENGTH_SHORT).show()\n        }\n    }\n\n    private fun requestPermissionsAndStart(classSessionId: String, plannedEndEpochMs: Long? = null, autoStopMode: String? = null, graceMinutes: Int? = null) {\n",
)

# Root: remove the old visual concentration mode; add discreet DND/theme actions and contextual Classes rail item.
p = "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt"
replace_once(p, "import androidx.compose.material.icons.filled.Menu\n", "import androidx.compose.material.icons.filled.DarkMode\nimport androidx.compose.material.icons.filled.LightMode\nimport androidx.compose.material.icons.filled.Menu\nimport androidx.compose.material.icons.filled.NotificationsOff\n")
replace_once(p, "import androidx.compose.ui.platform.LocalDensity\n", "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.res.painterResource\n")
replace_once(p, "import com.notcan.app.ui.theme.NotCanBlue\n", "import com.notcan.app.ui.theme.NotCanBlue\nimport com.notcan.app.ui.theme.NotCanDrawableIcons\n")
replace_once(
    p,
    "    recordingActive: Boolean = false,\n    autoFocusOnRecording: () -> Boolean = { true },\n",
    "    recordingActive: Boolean = false,\n    subjectContextActive: Boolean = false,\n    subjectsTitle: String = \"Materias\",\n    darkTheme: Boolean = true,\n    onToggleTheme: () -> Unit = {},\n    onToggleDoNotDisturb: () -> Unit = {},\n    onOpenClasses: () -> Unit = {},\n",
)
replace_once(p, "    var focusMode by rememberSaveable { mutableStateOf(false) }\n", "")
replace_once(
    p,
    "    BackHandler(enabled = focusMode || page != 0) {\n        if (focusMode) focusMode = false\n        else {\n            page = 0\n            navExpanded = false\n        }\n    }\n",
    "    BackHandler(enabled = page != 0) {\n        page = 0\n        navExpanded = false\n    }\n",
)
regex_once(
    p,
    r"\n    LaunchedEffect\(recordingActive\) \{.*?\n    \}\n\n    val zone",
    "\n    val zone",
)
regex_once(
    p,
    r"\n        if \(focusMode\) \{.*?return@BoxWithConstraints\n        \}\n",
    "\n",
)
replace_once(
    p,
    "                            railDestinations.forEach { d ->\n                                NavigationRailItem(\n                                    selected = page == d.page,\n                                    onClick = { page = d.page; navExpanded = false },\n                                    icon = { Icon(d.icon, d.label) },\n                                    label = { Text(d.label) }\n                                )\n                            }\n",
    "                            railDestinations.forEach { d ->\n                                NavigationRailItem(\n                                    selected = page == d.page,\n                                    onClick = { page = d.page; navExpanded = false },\n                                    icon = { Icon(d.icon, d.label) },\n                                    label = { Text(d.label) }\n                                )\n                                if (d.page == 1 && subjectContextActive) {\n                                    NavigationRailItem(\n                                        selected = false,\n                                        onClick = { page = 1; onOpenClasses(); navExpanded = false },\n                                        icon = { Icon(painterResource(NotCanDrawableIcons.Classes), \"Clases\") },\n                                        label = { Text(\"Clases\") }\n                                    )\n                                }\n                            }\n",
)
old_call = """                        NotCanTopBar(
                            page,
                            true,
                            { navExpanded = !navExpanded },
                            menuExpanded,
                            { menuExpanded = it },
                            { page = 4 },
                            { page = 1; focusMode = true },
                            { page = 6 }
                        )"""
new_call = """                        NotCanTopBar(
                            page = page,
                            subjectsTitle = subjectsTitle,
                            showNavigation = true,
                            onNavigation = { navExpanded = !navExpanded },
                            menuExpanded = menuExpanded,
                            onMenuExpanded = { menuExpanded = it },
                            subjectContextActive = subjectContextActive,
                            onOpenClasses = { page = 1; onOpenClasses() },
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                            onToggleDoNotDisturb = onToggleDoNotDisturb
                        )"""
replace_once(p, old_call, new_call)
old_call2 = """                    NotCanTopBar(
                        page,
                        false,
                        {},
                        menuExpanded,
                        { menuExpanded = it },
                        { page = 4 },
                        { page = 1; focusMode = true },
                        { page = 6 }
                    )"""
new_call2 = """                    NotCanTopBar(
                        page = page,
                        subjectsTitle = subjectsTitle,
                        showNavigation = false,
                        onNavigation = {},
                        menuExpanded = menuExpanded,
                        onMenuExpanded = { menuExpanded = it },
                        subjectContextActive = subjectContextActive,
                        onOpenClasses = { page = 1; onOpenClasses() },
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onToggleDoNotDisturb = onToggleDoNotDisturb
                    )"""
replace_once(p, old_call2, new_call2)
regex_once(
    p,
    r"@Composable\nprivate fun NotCanTopBar\(.*?\n\}\n\n@Composable\nprivate fun PlannedClassBanner",
    '''@Composable
private fun NotCanTopBar(
    page: Int,
    subjectsTitle: String,
    showNavigation: Boolean,
    onNavigation: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    subjectContextActive: Boolean,
    onOpenClasses: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDoNotDisturb: () -> Unit
) {
    val title = when (page) {
        1 -> subjectsTitle
        2 -> "Tareas"
        3 -> "Calendario académico"
        4 -> "Calificaciones"
        5 -> "TuNot"
        else -> "Configuración"
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showNavigation) {
                IconButton(onClick = onNavigation) {
                    Icon(Icons.Default.Menu, "Navegación", tint = NotCanOffWhite)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (page == 5) Text("Tutor académico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleDoNotDisturb) {
                Icon(Icons.Default.NotificationsOff, "No molestar", tint = NotCanGray)
            }
            IconButton(onClick = onToggleTheme) {
                Icon(if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, if (darkTheme) "Modo claro" else "Modo oscuro", tint = NotCanBlue)
            }
            if (page == 1 && subjectContextActive) {
                Box {
                    IconButton(onClick = { onMenuExpanded(true) }) {
                        Icon(NotCanIcons.More, "Más opciones", tint = NotCanOffWhite)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpanded(false) }) {
                        DropdownMenuItem(
                            text = { Text("Clases de esta materia") },
                            leadingIcon = { Icon(painterResource(NotCanDrawableIcons.Classes), null) },
                            onClick = { onOpenClasses(); onMenuExpanded(false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedClassBanner''',
)

# Home: contextual Classes request and remove the redundant inner class header.
p = "app/src/main/java/com/notcan/app/ui/home/NotCanHomeScreen.kt"
replace_once(p, "import androidx.compose.runtime.getValue\n", "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.getValue\n")
replace_once(
    p,
    "    selectedNoteId: String?,\n",
    "    selectedNoteId: String?,\n    classNavigationRequest: Int = 0,\n",
)
replace_once(
    p,
    "    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }\n",
    "    LaunchedEffect(classNavigationRequest, selectedSubjectId) {\n        if (classNavigationRequest > 0 && selectedSubjectId != null) level = HomeLevel.CLASSES\n    }\n\n    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }\n",
)
regex_once(
    p,
    r"\n                    if \(!landscapeIme\) \{\n                        CompactWorkspaceHeader\(.*?\n                    \}\n\n                    NotCanClassWorkspaceV4",
    "\n                    NotCanClassWorkspaceV4",
)

# Workspace: Apuntes first, ultra-compact gesture navigation, persistent background audio and clickable markers.
p = "app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt"
replace_once(p, "import android.media.MediaPlayer\n", "")
replace_once(p, "import androidx.compose.foundation.clickable\n", "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n")
replace_once(p, "import androidx.compose.ui.platform.LocalContext\n", "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.input.pointer.pointerInput\n")
replace_once(p, "import com.notcan.app.recording.RecordingService\n", "import com.notcan.app.recording.AudioPlaybackService\nimport com.notcan.app.recording.RecordingService\n")
replace_once(p, "import com.notcan.app.ui.theme.NotCanSurface\n", "import com.notcan.app.ui.theme.NotCanSurface\nimport androidx.core.content.ContextCompat\n")
regex_once(
    p,
    r"@Composable\nprivate fun NormalClassTabs\(.*?\n\}\n\n@Composable\nprivate fun NotesContentV5",
    '''@Composable
private fun NormalClassTabs(
    subjectName: String?,
    classTitle: String,
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity>,
    whisperModelState: WhisperModelState,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable(classSessionId) { mutableIntStateOf(0) }
    val views = listOf("Apuntes", "Audio", "Transcripción", "Estudio")
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        WindowInsets.ime.getBottom(density) > 0 && selected == 0
    var dragDistance = 0f

    Column(modifier.fillMaxSize()) {
        if (!landscapeIme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(classSessionId, selected) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onHorizontalDrag = { change, amount -> dragDistance += amount; change.consume() },
                            onDragEnd = {
                                when {
                                    dragDistance <= -55f && selected < views.lastIndex -> selected++
                                    dragDistance >= 55f && selected > 0 -> selected--
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (selected > 0) "‹" else "", color = NotCanGray, modifier = Modifier.width(22.dp).clickable(enabled = selected > 0) { selected-- })
                Spacer(Modifier.weight(1f))
                Text(views[selected], color = NotCanGray.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(7.dp))
                views.indices.forEach { index ->
                    Text("•", color = if (index == selected) NotCanBlue else NotCanGray.copy(alpha = 0.28f), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                Text(if (selected < views.lastIndex) "›" else "", color = NotCanGray, modifier = Modifier.width(22.dp).clickable(enabled = selected < views.lastIndex) { selected++ })
            }
            Spacer(Modifier.height(2.dp))
        }
        Box(Modifier.fillMaxSize()) {
            when (selected) {
                0 -> NotesContentV5(classSessionId, notePages, selectedNoteId, onSelectNote, onCreateNote, onUpdateNote, onDeleteNote, onImportNote, onShareNote)
                1 -> AudioContentV5(classSessionId, audioRecordings, importantMoments, onShareAudio, onDeleteAudio)
                2 -> TranscriptContentV5(audioRecordings, transcripts, detectedCues, whisperModelState, localWhisperBusy, localWhisperError, onTranscribeLocal, onDeleteTranscript)
                else -> StudyContentV5(subjectName, classTitle, transcripts, notePages, detectedCues)
            }
        }
    }
}

@Composable
private fun NotesContentV5''',
)
regex_once(
    p,
    r"@Composable\nprivate fun AudioContentV5\(.*?\n\}\n\n@Composable\nprivate fun AudioRowV5",
    '''@Composable
private fun AudioContentV5(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit
) {
    val context = LocalContext.current
    val playback by AudioPlaybackService.state.collectAsState()

    fun toggleAudio(audio: AudioRecordingEntity) {
        val intent = Intent(context, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_TOGGLE
            putExtra(AudioPlaybackService.EXTRA_AUDIO_ID, audio.id)
            putExtra(AudioPlaybackService.EXTRA_AUDIO_PATH, audio.localPath)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun jumpToMoment(moment: ImportantMomentEntity) {
        val audio = audioRecordings.firstOrNull { it.id == moment.audioId } ?: return
        if (playback.audioId != audio.id) toggleAudio(audio)
        context.startService(Intent(context, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_SEEK
            putExtra(AudioPlaybackService.EXTRA_OFFSET_MS, moment.offsetMs)
        })
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Grabaciones de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("El audio continúa al cambiar de vista o dejar NotCan en segundo plano.", color = NotCanGray)
                    Spacer(Modifier.height(9.dp))
                    if (audioRecordings.isEmpty()) Text("Todavía no hay grabaciones.", color = NotCanGray)
                    else audioRecordings.forEach { audio ->
                        AudioRowV5(audio, playback.audioId == audio.id && playback.isPlaying, { toggleAudio(audio) }, { onShareAudio(audio) }, { onDeleteAudio(audio.id) })
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Momentos importantes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (importantMoments.isEmpty()) Text("Pulsa ✴ durante la clase para guardar un instante importante.", color = NotCanGray)
                    else importantMoments.take(30).forEach { moment ->
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth().clickable { jumpToMoment(moment) }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = NotCanBlue, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(formatDurationV5(moment.offsetMs), color = NotCanOffWhite)
                                    moment.note?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = NotCanGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRowV5''',
)

# Notes: open in read mode, keep highlight/underline annotations, restore native touch selection.
p = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace_once(p, "import android.view.ActionMode\n", "")
replace_once(p, "import androidx.compose.material.icons.filled.FormatUnderlined\n", "import androidx.compose.material.icons.filled.Edit\nimport androidx.compose.material.icons.filled.FormatUnderlined\n")
replace_once(p, "import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.luminance\n")
replace_once(
    p,
    "    var shareMenu by remember(note.id) { mutableStateOf(false) }\n",
    "    var shareMenu by remember(note.id) { mutableStateOf(false) }\n    var editing by remember(note.id) { mutableStateOf(false) }\n    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f\n",
)
replace_once(p, "            webView?.loadDataWithBaseURL(null, writerDocument(externalHtml), \"text/html\", \"UTF-8\", null)\n", "            webView?.loadDataWithBaseURL(null, writerDocument(externalHtml, darkEditor), \"text/html\", \"UTF-8\", null)\n")
replace_once(
    p,
    "    LaunchedEffect(note.id, html) {\n",
    "    LaunchedEffect(note.id, editing) {\n        webView?.evaluateJavascript(\"window.notcanSetEditing(${if (editing) \"true\" else \"false\"});\", null)\n    }\n\n    LaunchedEffect(note.id, html) {\n",
)
replace_once(
    p,
    "                    Box {\n                        IconButton(onClick = { shareMenu = true })",
    "                    IconButton(onClick = { editing = !editing }) {\n                        Icon(Icons.Default.Edit, if (editing) \"Terminar edición\" else \"Editar\", tint = if (editing) NotCanBlue else NotCanGray)\n                    }\n                    Box {\n                        IconButton(onClick = { shareMenu = true })",
)
old_toolbar_start = """            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                WriterStructureButton("T1") { command("formatBlock", "H1") }"""
new_toolbar_start = """            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (!editing) {
                    IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayar selección") }
                    WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }
                    Text("  Selecciona texto para anotar", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                } else {
                WriterStructureButton("T1") { command("formatBlock", "H1") }"""
replace_once(p, old_toolbar_start, new_toolbar_start)
replace_once(
    p,
    "                WriterColorButton(Color(0xFFFF9BB8)) { command(\"hiliteColor\", \"#FF9BB8\") }\n            }\n            Divider",
    "                WriterColorButton(Color(0xFFFF9BB8)) { command(\"hiliteColor\", \"#FF9BB8\") }\n                }\n            }\n            Divider",
)
replace_once(p, "                        loadDataWithBaseURL(null, writerDocument(html), \"text/html\", \"UTF-8\", null)\n", "                        loadDataWithBaseURL(null, writerDocument(html, darkEditor), \"text/html\", \"UTF-8\", null)\n")
regex_once(
    p,
    r"private class NotCanWriterWebView\(context: Context\) : WebView\(context\) \{.*?\n\}",
    "private class NotCanWriterWebView(context: Context) : WebView(context)",
)
replace_once(
    p,
    "private fun writerDocument(initialBody: String): String = \"\"\"\n",
    "private fun writerDocument(initialBody: String, darkTheme: Boolean): String {\n    val textColor = if (darkTheme) \"#F3F4F6\" else \"#20252C\"\n    val selectionText = if (darkTheme) \"white\" else \"#172033\"\n    return \"\"\"\n",
)
replace_once(p, "background:transparent;color:#F3F4F6;", "background:transparent;color:$textColor;")
replace_once(p, "::selection{background:#3159A7;color:white}", "::selection{background:#B9D0FF;color:$selectionText}")
replace_once(p, "<body><div id=\"selbar\"", "<body><div id=\"selbar\"")
replace_once(p, "<div id=\"editor\" contenteditable=\"true\"", "<div id=\"editor\" contenteditable=\"false\"")
replace_once(
    p,
    "window.notcanCommand=function(c,v){editor.focus();restore();document.execCommand(c,false,v||null);save();notify()};",
    "window.notcanSetEditing=function(v){editor.contentEditable=v?'true':'false';if(v)editor.focus()};window.notcanCommand=function(c,v){restore();document.execCommand(c,false,v||null);save();notify()};",
)
replace_once(p, "\"\"\".trimIndent()\n", "\"\"\".trimIndent()\n}\n")

# Quiz: move navigation controls further up for comfortable tablet reach.
p = "app/src/main/java/com/notcan/app/ui/ai/StudyQuizScreen.kt"
replace_once(p, ".padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 64.dp),", ".padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 104.dp),")

# Version and rolling release metadata.
p = "app/build.gradle.kts"
replace_once(p, "        versionCode = 29\n        versionName = \"0.8.12\"\n", "        versionCode = 30\n        versionName = \"0.8.13\"\n")

p = ".github/workflows/android-debug.yml"
replace_once(p, "          TAG=\"v0.8.2-test\"\n", "          TAG=\"v0.8.13-test\"\n")
replace_once(p, "            \"$APK#NotCan-v0.8.2-test.apk\" \\\n", "            \"$APK#NotCan-v0.8.13-test.apk\" \\\n")
replace_once(p, "            --title \"NotCan v0.8.2 · prueba\" \\\n", "            --title \"NotCan v0.8.13 · prueba\" \\\n")
replace_once(
    p,
    "            --notes \"APK de prueba de NotCan v0.8.2. Añade controles de clase discretos sobre TuNot, texto en vivo minimizable y navegable al pausar, marcador de momentos importantes, navegación lateral ocultable, subrayado contextual en apuntes y calificaciones con ponderación opcional. Mantiene además las correcciones de TuNot y la sincronización Supabase del núcleo académico.\"\n",
    "            --notes \"NotCan v0.8.13: tema claro/oscuro, No molestar de Android, Apuntes como vista principal con navegación discreta, Clases contextual, audio persistente en segundo plano, marcadores navegables, modo lectura/edición de apuntes, selección táctil restaurada y controles del cuestionario más accesibles. Incluye además las mejoras recientes del transcriptor y TuNot.\"\n",
)

print("Final Android polish patch applied successfully")
