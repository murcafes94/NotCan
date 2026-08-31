from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Pattern not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# -----------------------------------------------------------------------------
# Class workspace: discreet live transcript + recording controls above TuNot.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt"
replace_once(
    p,
    "    val liveTranscript by RecordingService.liveTranscript.collectAsState()\n    val liveStatus by RecordingService.aiStatus.collectAsState()\n",
    "    val liveTranscript by RecordingService.liveTranscript.collectAsState()\n    val liveStatus by RecordingService.aiStatus.collectAsState()\n    var livePanelExpanded by remember(classSession?.id) { mutableStateOf(false) }\n    val overlayBottomPadding = if (LocalConfiguration.current.screenWidthDp >= 840) 88.dp else 150.dp\n",
)
replace_once(
    p,
    "                if (recordingActive) {\n                    RecordingHeader(subject?.name, classSession.title, liveStatus)\n                    Spacer(Modifier.height(6.dp))\n                    FocusedRecordingDesk(\n",
    "                if (recordingActive) {\n                    FocusedRecordingDesk(\n",
)
replace_once(
    p,
    "        RecordingControlsV5(\n            state = recordingState,\n            selectedClassId = classSession?.id,\n            onStart = onStartRecording,\n            onPause = onPauseRecording,\n            onResume = onResumeRecording,\n            onStop = onStopRecording,\n            onMark = onMarkMoment,\n            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)\n        )\n",
    "        if (recordingActive && livePanelExpanded) {\n            LiveTranscriptPanel(\n                transcript = liveTranscript,\n                status = liveStatus,\n                modifier = Modifier\n                    .align(Alignment.BottomEnd)\n                    .padding(end = 18.dp, bottom = overlayBottomPadding + 246.dp)\n                    .fillMaxWidth(0.88f)\n                    .height(170.dp)\n            )\n        }\n\n        RecordingControlsV5(\n            state = recordingState,\n            selectedClassId = classSession?.id,\n            showTranscript = livePanelExpanded,\n            onToggleTranscript = { livePanelExpanded = !livePanelExpanded },\n            onStart = onStartRecording,\n            onPause = onPauseRecording,\n            onResume = onResumeRecording,\n            onStop = onStopRecording,\n            onMark = onMarkMoment,\n            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = overlayBottomPadding)\n        )\n",
)
replace_once(
    p,
    "        } else if (wide) {\n            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {\n                WriterNoteEditor(selectedNote, onUpdateNote, { onShareNote(selectedNote) }, { onDeleteNote(selectedNote.id) }, Modifier.weight(0.70f))\n                LiveTranscriptPanel(liveTranscript, liveStatus, Modifier.weight(0.30f))\n            }\n        } else {\n            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                LiveTranscriptPanel(liveTranscript, liveStatus, Modifier.height(150.dp).fillMaxWidth())\n                WriterNoteEditor(selectedNote, onUpdateNote, { onShareNote(selectedNote) }, { onDeleteNote(selectedNote.id) }, Modifier.weight(1f))\n            }\n        }\n",
    "        } else {\n            WriterNoteEditor(\n                selectedNote,\n                onUpdateNote,\n                { onShareNote(selectedNote) },\n                { onDeleteNote(selectedNote.id) },\n                Modifier.fillMaxSize()\n            )\n        }\n",
)
replace_once(
    p,
    "@Composable\nprivate fun LiveTranscriptPanel(transcript: String, status: String, modifier: Modifier = Modifier) {\n    val scroll = rememberScrollState()\n    LaunchedEffect(transcript.length) { if (scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue) }\n\n    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(16.dp)) {\n        Column(Modifier.fillMaxSize().padding(14.dp)) {\n            Row(verticalAlignment = Alignment.CenterVertically) {\n                Icon(Icons.Default.GraphicEq, null, tint = NotCanBlue)\n                Spacer(Modifier.width(7.dp))\n                Column {\n                    Text(\"Transcripción en vivo\", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)\n                    Text(\"Provisional · Moonshine\", color = NotCanBlue, style = MaterialTheme.typography.labelSmall)\n                }\n            }\n            Spacer(Modifier.height(7.dp))\n            Text(status, color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n            Spacer(Modifier.height(8.dp))\n            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {\n                Text(\n                    if (transcript.isBlank()) {\n                        if (status.contains(\"sin transcripción\", ignoreCase = true)) \"La grabación continúa. Para ver texto provisional instala Moonshine desde IA → Fuentes.\"\n                        else \"Escuchando… el texto provisional aparecerá aquí sin modificar tus apuntes.\"\n                    } else transcript.takeLast(6000),\n                    color = if (transcript.isBlank()) NotCanGray else NotCanOffWhite,\n                    style = MaterialTheme.typography.bodyMedium\n                )\n            }\n        }\n    }\n}\n",
    "@Composable\nprivate fun LiveTranscriptPanel(transcript: String, status: String, modifier: Modifier = Modifier) {\n    val scroll = rememberScrollState()\n    val paused = status.contains(\"paus\", ignoreCase = true)\n    LaunchedEffect(transcript.length, paused) {\n        if (!paused && scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue)\n    }\n\n    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(16.dp)) {\n        Column(Modifier.fillMaxSize().padding(12.dp)) {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))\n            }\n            Spacer(Modifier.height(5.dp))\n            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {\n                Text(\n                    if (transcript.isBlank()) \"…\" else transcript.takeLast(8000),\n                    color = if (transcript.isBlank()) NotCanGray else NotCanOffWhite,\n                    style = MaterialTheme.typography.bodyMedium\n                )\n            }\n        }\n    }\n}\n",
)
replace_once(
    p,
    "@Composable\nprivate fun RecordingControlsV5(\n    state: RecordingState,\n    selectedClassId: String?,\n    onStart: (String) -> Unit,\n    onPause: () -> Unit,\n    onResume: () -> Unit,\n    onStop: () -> Unit,\n    onMark: () -> Unit,\n    modifier: Modifier = Modifier\n) {\n    var expanded by remember { mutableStateOf(false) }\n    val active = state is RecordingState.Recording || state is RecordingState.Paused\n    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {\n        if (active) RoundControlV5(Icons.Default.Star, \"Marcar momento importante\", NotCanOffWhite, NotCanBlue, onClick = onMark)\n        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {\n            AnimatedVisibility(visible = active && expanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {\n                    when (state) {\n                        is RecordingState.Recording -> RoundControlV5(Icons.Default.Pause, \"Pausar grabación\", NotCanOffWhite, NotCanSurface, onClick = onPause)\n                        is RecordingState.Paused -> RoundControlV5(Icons.Default.PlayArrow, \"Reanudar grabación\", NotCanOffWhite, NotCanSurface, onClick = onResume)\n                        else -> Unit\n                    }\n                    RoundControlV5(Icons.Default.Stop, \"Detener grabación\", NotCanOffWhite, NotCanSurface, onClick = onStop)\n                }\n            }\n            if (!active) RoundControlV5(Icons.Default.RadioButtonChecked, if (selectedClassId == null) \"Selecciona una clase\" else \"Comenzar grabación\", if (selectedClassId == null) NotCanGray else NotCanRed, NotCanGraphite, selectedClassId != null) { selectedClassId?.let(onStart) }\n            else RoundControlV5(Icons.Default.Circle, \"Controles de grabación\", NotCanRed, NotCanGraphite) { expanded = !expanded }\n        }\n    }\n}\n",
    "@Composable\nprivate fun RecordingControlsV5(\n    state: RecordingState,\n    selectedClassId: String?,\n    showTranscript: Boolean,\n    onToggleTranscript: () -> Unit,\n    onStart: (String) -> Unit,\n    onPause: () -> Unit,\n    onResume: () -> Unit,\n    onStop: () -> Unit,\n    onMark: () -> Unit,\n    modifier: Modifier = Modifier\n) {\n    val active = state is RecordingState.Recording || state is RecordingState.Paused\n    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {\n        if (active) {\n            RoundControlV5(\n                Icons.Default.GraphicEq,\n                if (showTranscript) \"Ocultar texto\" else \"Mostrar texto\",\n                if (showTranscript) NotCanOffWhite else NotCanBlue,\n                NotCanGraphite,\n                onClick = onToggleTranscript\n            )\n            RoundControlV5(Icons.Default.Star, \"Marcar momento importante\", NotCanOffWhite, NotCanGraphite, onClick = onMark)\n            when (state) {\n                is RecordingState.Recording -> RoundControlV5(Icons.Default.Pause, \"Pausar\", NotCanOffWhite, NotCanGraphite, onClick = onPause)\n                is RecordingState.Paused -> RoundControlV5(Icons.Default.PlayArrow, \"Reanudar\", NotCanOffWhite, NotCanGraphite, onClick = onResume)\n                else -> Unit\n            }\n            RoundControlV5(Icons.Default.Stop, \"Detener\", NotCanRed, NotCanGraphite, onClick = onStop)\n        } else {\n            RoundControlV5(\n                Icons.Default.RadioButtonChecked,\n                if (selectedClassId == null) \"Selecciona una clase\" else \"Comenzar grabación\",\n                if (selectedClassId == null) NotCanGray else NotCanRed,\n                NotCanGraphite,\n                selectedClassId != null\n            ) { selectedClassId?.let(onStart) }\n        }\n    }\n}\n",
)

# -----------------------------------------------------------------------------
# Root navigation: keep rail on landing; hide it in other sections and expose ☰.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt"
replace_once(p, "import androidx.activity.compose.BackHandler\n", "import androidx.activity.compose.BackHandler\nimport androidx.compose.animation.AnimatedVisibility\n")
replace_once(p, "import androidx.compose.material3.Button\n", "import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Menu\nimport androidx.compose.material3.Button\n")
replace_once(
    p,
    "    var menuExpanded by remember { mutableStateOf(false) }\n    var focusMode by remember { mutableStateOf(false) }\n",
    "    var menuExpanded by remember { mutableStateOf(false) }\n    var focusMode by remember { mutableStateOf(false) }\n    var navExpanded by remember { mutableStateOf(false) }\n",
)
replace_once(
    p,
    "        if (wide) {\n            Row(Modifier.fillMaxSize()) {\n                NavigationRail(\n",
    "        if (wide) {\n            Row(Modifier.fillMaxSize()) {\n                AnimatedVisibility(visible = page == 0 || navExpanded) {\n                    Row {\n                NavigationRail(\n",
)
replace_once(
    p,
    "                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())\n                Column(Modifier.weight(1f).fillMaxHeight()) {\n",
    "                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())\n                    }\n                }\n                Column(Modifier.weight(1f).fillMaxHeight()) {\n",
)
# Close the temporary rail whenever a destination is chosen.
text = Path(p).read_text(encoding="utf-8")
text = text.replace("                                page = index\n", "                                page = index\n                                navExpanded = false\n", 1)
text = text.replace("onClick = { previousPage = page.coerceIn(0, 2); page = 3 },", "onClick = { previousPage = page.coerceIn(0, 2); page = 3; navExpanded = false },", 1)
text = text.replace("onClick = { previousPage = page.coerceIn(0, 2); page = 4 },", "onClick = { previousPage = page.coerceIn(0, 2); page = 4; navExpanded = false },", 1)
Path(p).write_text(text, encoding="utf-8")
replace_once(
    p,
    "                        NotCanTopBar(\n                            page = page,\n                            menuExpanded = menuExpanded,\n",
    "                        NotCanTopBar(\n                            page = page,\n                            showNavigation = true,\n                            onNavigation = { navExpanded = !navExpanded },\n                            menuExpanded = menuExpanded,\n",
)
replace_once(
    p,
    "                    NotCanTopBar(\n                        page = page,\n                        menuExpanded = menuExpanded,\n",
    "                    NotCanTopBar(\n                        page = page,\n                        showNavigation = false,\n                        onNavigation = {},\n                        menuExpanded = menuExpanded,\n",
)
replace_once(
    p,
    "private fun NotCanTopBar(\n    page: Int,\n    menuExpanded: Boolean,\n",
    "private fun NotCanTopBar(\n    page: Int,\n    showNavigation: Boolean,\n    onNavigation: () -> Unit,\n    menuExpanded: Boolean,\n",
)
replace_once(
    p,
    "        ) {\n            Column(Modifier.weight(1f)) {\n                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)\n",
    "        ) {\n            if (showNavigation) {\n                IconButton(onClick = onNavigation) {\n                    Icon(Icons.Default.Menu, \"Navegación\", tint = NotCanOffWhite)\n                }\n            }\n            Column(Modifier.weight(1f)) {\n                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)\n",
)

# -----------------------------------------------------------------------------
# Writer: keep existing toolbar and add a tiny contextual highlighter on selection.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace_once(
    p,
    "#editor ul,#editor ol{padding-left:1.6em}::selection{background:#3159A7;color:white}</style></head>\n<body><div id=\"editor\"",
    "#editor ul,#editor ol{padding-left:1.6em}::selection{background:#3159A7;color:white}#selbar{position:fixed;display:none;z-index:50;background:#242830;border:1px solid #444b57;border-radius:14px;padding:3px;box-shadow:0 4px 16px rgba(0,0,0,.3)}#selbar button{width:34px;height:30px;border:0;border-radius:10px;background:#343a46;color:#FFE066;font-size:18px;line-height:1}</style></head>\n<body><div id=\"selbar\"><button id=\"markBtn\" aria-label=\"Subrayar\" title=\"Subrayar\">▰</button></div><div id=\"editor\"",
)
replace_once(
    p,
    "editor.addEventListener('input',function(){save();notify()})})();</script></body></html>\n",
    "editor.addEventListener('input',function(){save();notify()})})();</script><script>(function(){const editor=document.getElementById('editor'),bar=document.getElementById('selbar'),btn=document.getElementById('markBtn');function selected(){const s=window.getSelection();if(!s||s.rangeCount===0||s.isCollapsed)return null;const r=s.getRangeAt(0),n=r.commonAncestorContainer,p=n.nodeType===3?n.parentNode:n;if(!(p===editor||editor.contains(p)))return null;return r}function place(){const r=selected();if(!r){bar.style.display='none';return}const rect=r.getBoundingClientRect();bar.style.display='block';const left=Math.max(8,Math.min(window.innerWidth-46,rect.left+rect.width/2-20));const top=Math.max(8,rect.top-42);bar.style.left=left+'px';bar.style.top=top+'px'}document.addEventListener('selectionchange',function(){setTimeout(place,0)});editor.addEventListener('mouseup',place);editor.addEventListener('touchend',function(){setTimeout(place,30)});btn.addEventListener('pointerdown',function(e){e.preventDefault();if(!selected())return;document.execCommand('hiliteColor',false,'#FFE066');if(window.NotCanBridge)window.NotCanBridge.onContentChanged(editor.innerHTML);bar.style.display='none'})})();</script></body></html>\n",
)

# -----------------------------------------------------------------------------
# Grades: weighting/max score become optional instead of mandatory.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/grades/GradesScreen.kt"
replace_once(
    p,
    "    val totalWeight = items.sumOf { it.weightPercent }\n    val weightedEarned = items.sumOf { it.weightedContribution }\n    val completedAverage = if (totalWeight > 0.0) weightedEarned / totalWeight * 100.0 else 0.0\n",
    "    val totalWeight = items.sumOf { it.weightPercent.coerceAtLeast(0.0) }\n    val weightedEarned = items.sumOf { it.weightedContribution }\n    val hasWeights = totalWeight > 0.0\n    val simpleAverage = if (items.isNotEmpty()) items.map { it.normalized * 100.0 }.average() else 0.0\n    val completedAverage = if (hasWeights) weightedEarned / totalWeight * 100.0 else simpleAverage\n",
)
replace_once(
    p,
    "                    Text(\"Promedio sobre actividades registradas: ${fmt(completedAverage)}%\", color = NotCanOffWhite)\n                    Text(\"Aporte acumulado al total de la materia: ${fmt(weightedEarned)} / 100\", color = NotCanGray)\n                    Text(\"Porcentaje ya evaluado: ${fmt(totalWeight)}%\", color = if (totalWeight > 100.0) MaterialTheme.colorScheme.error else NotCanGray)\n                    if (totalWeight < 100.0) Text(\"Falta por evaluar: ${fmt(100.0 - totalWeight)}%\", color = NotCanGray)\n",
    "                    Text(\"Promedio sobre actividades registradas: ${fmt(completedAverage)}%\", color = NotCanOffWhite)\n                    if (hasWeights) {\n                        Text(\"Aporte acumulado al total de la materia: ${fmt(weightedEarned)} / 100\", color = NotCanGray)\n                        Text(\"Porcentaje ya evaluado: ${fmt(totalWeight)}%\", color = if (totalWeight > 100.0) MaterialTheme.colorScheme.error else NotCanGray)\n                        if (totalWeight < 100.0) Text(\"Falta por evaluar: ${fmt(100.0 - totalWeight)}%\", color = NotCanGray)\n                    } else {\n                        Text(\"Sin ponderación configurada · se usa promedio simple.\", color = NotCanGray)\n                    }\n",
)
replace_once(p, "label = { Text(\"Máximo\") }", "label = { Text(\"Máximo (opcional)\") }")
replace_once(p, "label = { Text(\"Peso %\") }", "label = { Text(\"Peso % (opcional)\") }")
replace_once(
    p,
    "                            val m = maxScore.replace(',', '.').toDoubleOrNull()\n                            val w = weight.replace(',', '.').toDoubleOrNull()\n                            if (s == null || m == null || w == null || m <= 0.0 || w !in 0.0..100.0) {\n                                error = \"Revisa nota, máximo y porcentaje.\"\n",
    "                            val m = maxScore.replace(',', '.').toDoubleOrNull() ?: 100.0\n                            val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0\n                            if (s == null || m <= 0.0 || w !in 0.0..100.0) {\n                                error = \"Revisa la nota y, si los usas, el máximo o porcentaje.\"\n",
)
replace_once(
    p,
    "                            Text(\"${fmt(item.score)} / ${fmt(item.maxScore)} · ${fmt(item.normalized * 100.0)}% · peso ${fmt(item.weightPercent)}%\", color = NotCanGray)\n                        }\n                        Text(\"+${fmt(item.weightedContribution)}\", color = NotCanBlue, fontWeight = FontWeight.SemiBold)\n",
    "                            Text(\n                                if (item.weightPercent > 0.0) \"${fmt(item.score)} / ${fmt(item.maxScore)} · ${fmt(item.normalized * 100.0)}% · peso ${fmt(item.weightPercent)}%\"\n                                else \"${fmt(item.score)} / ${fmt(item.maxScore)} · ${fmt(item.normalized * 100.0)}%\",\n                                color = NotCanGray\n                            )\n                        }\n                        if (item.weightPercent > 0.0) Text(\"+${fmt(item.weightedContribution)}\", color = NotCanBlue, fontWeight = FontWeight.SemiBold)\n",
)

# Version bump for the test APK.
p = "app/build.gradle.kts"
replace_once(p, "        versionCode = 18\n        versionName = \"0.8.1\"\n", "        versionCode = 19\n        versionName = \"0.8.2\"\n")

print("Discreet class UI patch applied successfully.")
