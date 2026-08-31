from pathlib import Path


def replace(path, old, new, count=1):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'Pattern not found in {path}: {old[:160]!r}')
    p.write_text(text.replace(old, new, count))

# -----------------------------------------------------------------------------
# v0.8.12
# - Preserve navigation across orientation changes.
# - Compact TuNot source-mode popup.
# - Landscape + IME focused writing mode.
# - Quiz button gets a device-independent bottom safety reserve.
# -----------------------------------------------------------------------------

# Root navigation survives Activity recreation and hides its top chrome while
# writing in landscape with the IME visible.
path = 'app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt'
replace(path, 'package com.notcan.app.ui.home\n', 'package com.notcan.app.ui.home\n\nimport android.content.res.Configuration\n')
replace(path, 'import androidx.compose.foundation.layout.width\n', 'import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.ime\n')
replace(path, 'import androidx.compose.runtime.remember\n', 'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\n')
replace(path, 'import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalDensity\n')
replace(path, '    var page by remember { mutableIntStateOf(0) }\n', '    var page by rememberSaveable { mutableIntStateOf(0) }\n')
replace(path, '    var focusMode by remember { mutableStateOf(false) }\n', '    var focusMode by rememberSaveable { mutableStateOf(false) }\n')
replace(
    path,
    '    var navExpanded by remember { mutableStateOf(false) }\n\n    BackHandler',
    '    var navExpanded by remember { mutableStateOf(false) }\n    val configuration = LocalConfiguration.current\n    val density = LocalDensity.current\n    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n        WindowInsets.ime.getBottom(density) > 0\n\n    BackHandler'
)
replace(path, '                    if (page != 0) {\n                        NotCanTopBar(', '                    if (page != 0 && !(page == 1 && landscapeIme)) {\n                        NotCanTopBar(', 1)
replace(path, '                if (page != 0) {\n                    NotCanTopBar(', '                if (page != 0 && !(page == 1 && landscapeIme)) {\n                    NotCanTopBar(', 1)

# Materias workspace level survives rotation and the class header disappears
# temporarily while landscape keyboard editing is active.
path = 'app/src/main/java/com/notcan/app/ui/home/NotCanHomeScreen.kt'
replace(path, 'package com.notcan.app.ui.home\n', 'package com.notcan.app.ui.home\n\nimport android.content.res.Configuration\n')
replace(path, 'import androidx.compose.foundation.layout.padding\n', 'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.ime\n')
replace(path, 'import androidx.compose.runtime.remember\n', 'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\n')
replace(path, 'import androidx.compose.ui.Alignment\n', 'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalDensity\n')
replace(
    path,
    '''    var level by remember(selectedSubjectId) {\n        mutableStateOf(if (selectedSubjectId == null) HomeLevel.SUBJECTS else HomeLevel.CLASSES)\n    }\n''',
    '''    var level by rememberSaveable(selectedSubjectId, selectedClassId) {\n        mutableStateOf(\n            when {\n                selectedSubjectId == null -> HomeLevel.SUBJECTS\n                selectedClassId == null -> HomeLevel.CLASSES\n                else -> HomeLevel.WORKSPACE\n            }\n        )\n    }\n    val configuration = LocalConfiguration.current\n    val density = LocalDensity.current\n    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n        WindowInsets.ime.getBottom(density) > 0\n'''
)
replace(
    path,
    '''                    CompactWorkspaceHeader(\n                        classTitle = selectedClass.title,\n                        onBackToClasses = { level = HomeLevel.CLASSES },\n                        onAddClass = { createDialog = CreateDialog.Class }\n                    )\n\n                    NotCanClassWorkspaceV4(''',
    '''                    if (!landscapeIme) {\n                        CompactWorkspaceHeader(\n                            classTitle = selectedClass.title,\n                            onBackToClasses = { level = HomeLevel.CLASSES },\n                            onAddClass = { createDialog = CreateDialog.Class }\n                        )\n                    }\n\n                    NotCanClassWorkspaceV4('''
)

# Class workspace tabs/pages chrome collapses while editing with landscape IME.
path = 'app/src/main/java/com/notcan/app/ui/home/ClassWorkspaceV5.kt'
replace(path, 'package com.notcan.app.ui.home\n', 'package com.notcan.app.ui.home\n\nimport android.content.res.Configuration\n')
replace(path, 'import androidx.compose.foundation.layout.width\n', 'import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.ime\n')
replace(path, 'import androidx.compose.runtime.remember\n', 'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\n')
replace(path, 'import androidx.compose.ui.platform.LocalConfiguration\n', 'import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalDensity\n')
replace(path, '    var selected by remember(classSessionId) { mutableIntStateOf(0) }\n', '    var selected by rememberSaveable(classSessionId) { mutableIntStateOf(0) }\n')
replace(
    path,
    '''    val tabs = listOf("Audio", "Transcripción", "Apuntes", "Estudio")\n\n    Column(modifier.fillMaxSize()) {\n        TabRow(selectedTabIndex = selected, containerColor = Color.Transparent, contentColor = NotCanBlue, divider = { }) {\n            tabs.forEachIndexed { index, title -> Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) }) }\n        }\n        Spacer(Modifier.height(8.dp))\n''',
    '''    val tabs = listOf("Audio", "Transcripción", "Apuntes", "Estudio")\n    val configuration = LocalConfiguration.current\n    val density = LocalDensity.current\n    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n        WindowInsets.ime.getBottom(density) > 0 && selected == 2\n\n    Column(modifier.fillMaxSize()) {\n        if (!landscapeIme) {\n            TabRow(selectedTabIndex = selected, containerColor = Color.Transparent, contentColor = NotCanBlue, divider = { }) {\n                tabs.forEachIndexed { index, title -> Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) }) }\n            }\n            Spacer(Modifier.height(8.dp))\n        }\n'''
)
replace(
    path,
    '''    val wide = LocalConfiguration.current.screenWidthDp >= 650\n    var showPages by remember(classSessionId) { mutableStateOf(false) }\n''',
    '''    val configuration = LocalConfiguration.current\n    val wide = configuration.screenWidthDp >= 650\n    val density = LocalDensity.current\n    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n        WindowInsets.ime.getBottom(density) > 0\n    var showPages by remember(classSessionId) { mutableStateOf(false) }\n'''
)
replace(
    path,
    '''    Column(Modifier.fillMaxSize()) {\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {\n            IconButton(onClick = { showPages = !showPages }) {\n                Icon(Icons.Default.Menu, if (showPages) "Ocultar páginas" else "Páginas e importar", tint = NotCanBlue)\n            }\n        }\n''',
    '''    Column(Modifier.fillMaxSize()) {\n        if (!landscapeIme) {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {\n                IconButton(onClick = { showPages = !showPages }) {\n                    Icon(Icons.Default.Menu, if (showPages) "Ocultar páginas" else "Páginas e importar", tint = NotCanBlue)\n                }\n            }\n        }\n'''
)
replace(path, '                AnimatedVisibility(visible = showPages) {\n                    NotePagesRail(', '                AnimatedVisibility(visible = showPages && !landscapeIme) {\n                    NotePagesRail(', 1)
replace(path, '                AnimatedVisibility(visible = showPages) {\n                    Column(Modifier.fillMaxWidth()) {', '                AnimatedVisibility(visible = showPages && !landscapeIme) {\n                    Column(Modifier.fillMaxWidth()) {', 1)

# Writer keeps toolbar + document visible when keyboard opens in landscape.
path = 'app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt'
replace(path, 'package com.notcan.app.ui.home\n', 'package com.notcan.app.ui.home\n\nimport android.content.res.Configuration\n')
replace(path, 'import androidx.compose.foundation.layout.width\n', 'import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.ime\n')
replace(path, 'import androidx.compose.ui.platform.LocalContext\n', 'import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity\n')
replace(
    path,
    '''    val context = LocalContext.current\n    val title = note.title.ifBlank { "Apuntes" }\n''',
    '''    val context = LocalContext.current\n    val configuration = LocalConfiguration.current\n    val density = LocalDensity.current\n    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&\n        WindowInsets.ime.getBottom(density) > 0\n    val title = note.title.ifBlank { "Apuntes" }\n'''
)
replace(
    path,
    '''        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {\n            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)\n                Box {\n                    IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir apunte", tint = NotCanBlue) }\n                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {\n''',
    '''        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (landscapeIme) 2.dp else 8.dp)) {\n            if (!landscapeIme) {\n                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                    Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)\n                    Box {\n                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir apunte", tint = NotCanBlue) }\n                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {\n'''
)
replace(
    path,
    '''                    }\n                }\n                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar apunte", tint = NotCanRed) }\n            }\n\n            Divider(color = NotCanGray.copy(alpha = 0.20f))\n            Row(''',
    '''                        }\n                    }\n                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar apunte", tint = NotCanRed) }\n                }\n                Divider(color = NotCanGray.copy(alpha = 0.20f))\n            }\n            Row('''
)
replace(path, '            Text("Guardado automático", color = NotCanGray, style = MaterialTheme.typography.labelSmall)\n', '            if (!landscapeIme) Text("Guardado automático", color = NotCanGray, style = MaterialTheme.typography.labelSmall)\n')

# TuNot section and source-mode survive rotation; popup has its own compact width.
path = 'app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt'
replace(path, 'import androidx.compose.runtime.remember\n', 'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable\n')
replace(path, '    var section by remember { mutableIntStateOf(1) }\n', '    var section by rememberSaveable { mutableIntStateOf(1) }\n')
replace(path, '    var sourceMode by remember(scopeKey) { mutableIntStateOf(1) } // 0 Mis fuentes · 1 Auto · 2 Web\n', '    var sourceMode by rememberSaveable(scopeKey) { mutableIntStateOf(1) } // 0 Mis fuentes · 1 Auto · 2 Web\n')
replace(path, '    var socraticMode by remember(scopeKey) { mutableStateOf(false) }\n', '    var socraticMode by rememberSaveable(scopeKey) { mutableStateOf(false) }\n')
replace(path, '                    modifier = Modifier.fillMaxWidth()\n                ) {\n                    DropdownMenuItem(', '                    modifier = Modifier.width(232.dp)\n                ) {\n                    DropdownMenuItem(', 1)

# Quiz: raise bottom action above system navigation even on devices/dialogs that
# report consumed/zero navigation insets. Gestures remain as a fallback.
path = 'app/src/main/java/com/notcan/app/ui/ai/StudyQuizScreen.kt'
replace(
    path,
    '''                                modifier = Modifier\n                                    .fillMaxWidth()\n                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)\n''',
    '''                                modifier = Modifier\n                                    .fillMaxWidth()\n                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 76.dp)\n'''
)

# Version bump.
path = 'app/build.gradle.kts'
replace(path, '        versionCode = 28\n        versionName = "0.8.11"\n', '        versionCode = 29\n        versionName = "0.8.12"\n')

print('Applied NotCan Android 0.8.12 final UI patch')
