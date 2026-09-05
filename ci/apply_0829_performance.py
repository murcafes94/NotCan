from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Pattern not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Version + ProfileInstaller support.
replace(
    "app/build.gradle.kts",
    'versionCode = 51\n        versionName = "0.8.28"',
    'versionCode = 52\n        versionName = "0.8.29"'
)
replace(
    "app/build.gradle.kts",
    'implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")',
    'implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")\n    implementation("androidx.profileinstaller:profileinstaller:1.4.1")'
)

# MainActivity: subscribe to the heavy class flows only while Materias/TuNot need them.
replace(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    'var classNavigationRequest by remember { mutableIntStateOf(0) }\n            NotCanTheme(darkTheme = darkTheme) {',
    'var classNavigationRequest by remember { mutableIntStateOf(0) }\n            var rootPage by remember { mutableIntStateOf(0) }\n            NotCanTheme(darkTheme = darkTheme) {'
)
replace(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    '''                val classes = studyViewModel.classes.collectAsStateWithLifecycle().value\n                val audioRecordings = studyViewModel.audioRecordings.collectAsStateWithLifecycle().value\n                val importantMoments = studyViewModel.importantMoments.collectAsStateWithLifecycle().value\n                val notePages = studyViewModel.notePages.collectAsStateWithLifecycle().value\n                val documents = studyViewModel.documents.collectAsStateWithLifecycle().value\n                val pdfInkStrokes = studyViewModel.pdfInkStrokes.collectAsStateWithLifecycle().value\n                val transcripts = studyViewModel.transcripts.collectAsStateWithLifecycle().value''',
    '''                val classes = studyViewModel.classes.collectAsStateWithLifecycle().value\n                val workspaceDataActive = rootPage == 1 || rootPage == 5\n                val audioRecordings = if (workspaceDataActive) studyViewModel.audioRecordings.collectAsStateWithLifecycle().value else emptyList()\n                val importantMoments = if (rootPage == 1) studyViewModel.importantMoments.collectAsStateWithLifecycle().value else emptyList()\n                val notePages = if (workspaceDataActive) studyViewModel.notePages.collectAsStateWithLifecycle().value else emptyList()\n                val documents = if (workspaceDataActive) studyViewModel.documents.collectAsStateWithLifecycle().value else emptyList()\n                val pdfInkStrokes = if (rootPage == 1) studyViewModel.pdfInkStrokes.collectAsStateWithLifecycle().value else emptyList()\n                val transcripts = if (workspaceDataActive) studyViewModel.transcripts.collectAsStateWithLifecycle().value else emptyList()'''
)
replace(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    '''                val gradeItems = extrasViewModel.gradeItems.collectAsStateWithLifecycle().value\n                val detectedCues = extrasViewModel.detectedCues.collectAsStateWithLifecycle().value\n                val taskItems = extrasViewModel.taskItems.collectAsStateWithLifecycle().value''',
    '''                val gradeItems = if (rootPage == 4) extrasViewModel.gradeItems.collectAsStateWithLifecycle().value else emptyList()\n                val detectedCues = if (workspaceDataActive) extrasViewModel.detectedCues.collectAsStateWithLifecycle().value else emptyList()\n                val taskItems = extrasViewModel.taskItems.collectAsStateWithLifecycle().value'''
)
replace(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    'onToggleDoNotDisturb = ::toggleDoNotDisturb,\n                    onOpenSubjects',
    'onToggleDoNotDisturb = ::toggleDoNotDisturb,\n                    onPageChanged = { rootPage = it },\n                    onOpenSubjects'
)

# Root navigation exposes the active destination to the Activity so DB observers can sleep.
replace(
    "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt",
    'onToggleDoNotDisturb: () -> Unit = {},\n    onOpenSubjects',
    'onToggleDoNotDisturb: () -> Unit = {},\n    onPageChanged: (Int) -> Unit = {},\n    onOpenSubjects'
)
replace(
    "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt",
    '''    BackHandler(enabled = page != 0) {\n        page = 0\n        navExpanded = false\n    }\n\n    LaunchedEffect(Unit) {''',
    '''    BackHandler(enabled = page != 0) {\n        page = 0\n        navExpanded = false\n    }\n\n    LaunchedEffect(page) { onPageChanged(page) }\n\n    LaunchedEffect(Unit) {'''
)

# Settings: migrate a stale calendar id left by the old per-calendar list and explain grouping.
replace(
    "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt",
    '''    val automaticCalendarId = remember(calendarTargets) {\n        calendarTargets.firstOrNull { it.isGoogle && it.isPrimary }?.id\n            ?: calendarTargets.firstOrNull { it.isGoogle }?.id\n            ?: calendarTargets.firstOrNull { it.isPrimary }?.id\n            ?: calendarTargets.firstOrNull()?.id\n    }\n\n    Column(''',
    '''    val automaticCalendarId = remember(calendarTargets) {\n        calendarTargets.firstOrNull { it.isGoogle && it.isPrimary }?.id\n            ?: calendarTargets.firstOrNull { it.isGoogle }?.id\n            ?: calendarTargets.firstOrNull { it.isPrimary }?.id\n            ?: calendarTargets.firstOrNull()?.id\n    }\n    LaunchedEffect(calendarTargets, selectedCalendarId) {\n        if (calendarTargets.isNotEmpty() && calendarTargets.none { it.id == selectedCalendarId }) {\n            val fallback = automaticCalendarId ?: return@LaunchedEffect\n            selectedCalendarId = fallback\n            preferences.calendarId = fallback\n        }\n    }\n\n    Column('''
)
replace(
    "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt",
    'Text("Los eventos se guardan en el proveedor elegido y usan sus propias notificaciones. NotCan mantiene además su recordatorio académico.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)',
    'Text("Se muestra una entrada por cuenta. NotCan usa su calendario principal editable para evitar duplicados y mantiene además su recordatorio académico.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)'
)

# Ship conservative baseline/startup profile rules. Class rules are stable across Kotlin compiler changes.
(ROOT / "app/src/main").mkdir(parents=True, exist_ok=True)
profile = """Lcom/notcan/app/MainActivity;\nLcom/notcan/app/ui/home/NotCanRootV5Kt;\nLcom/notcan/app/ui/home/NotCanHomeScreenKt;\nLcom/notcan/app/ui/home/ClassWorkspaceV5Kt;\nLcom/notcan/app/ui/home/WriterNoteEditorKt;\nLcom/notcan/app/ui/calendar/AcademicCalendarScreenKt;\nLcom/notcan/app/ui/settings/SettingsScreenKt;\nLcom/notcan/app/ui/ai/NotCanAiScreenKt;\nLcom/notcan/app/ui/tasks/TasksScreenKt;\nLcom/notcan/app/ui/grades/GradesScreenKt;\nLcom/notcan/app/data/local/NotCanDatabase;\nLcom/notcan/app/ui/NotCanViewModel;\n"""
(ROOT / "app/src/main/baseline-prof.txt").write_text(profile, encoding="utf-8")
(ROOT / "app/src/main/startup-prof.txt").write_text(
    "Lcom/notcan/app/MainActivity;\nLcom/notcan/app/ui/home/NotCanRootV5Kt;\nLcom/notcan/app/ui/home/NotCanHomeScreenKt;\nLcom/notcan/app/data/local/NotCanDatabase;\nLcom/notcan/app/ui/NotCanViewModel;\n",
    encoding="utf-8"
)

# Rolling release metadata.
workflow = ROOT / ".github/workflows/android-debug.yml"
w = workflow.read_text(encoding="utf-8")
w = w.replace("notcan-v0.8.28-optimized-apk", "notcan-v0.8.29-performance-apk")
w = w.replace("v0.8.28-test", "v0.8.29-test")
w = w.replace("NotCan-v0.8.28.apk", "NotCan-v0.8.29.apk")
w = w.replace("NotCan v0.8.28 · rendimiento y caché controlada", "NotCan v0.8.29 · arranque, navegación y calendarios")
w = w.replace(
    "Build optimizada v0.8.28. Cambia la distribución de prueba a variante release no depurable con la misma firma persistente, limita automáticamente la caché temporal sin tocar modelos, audios ni documentos, añade un panel de rendimiento/almacenamiento con limpieza segura y reduce el sondeo de Configuración a 30 s cuando no hay descargas (1.5 s durante descargas). Mantiene Gemma 4 sobre LiteRT-LM 0.11.0 y todas las funciones de v0.8.27.",
    "Build v0.8.29. Agrupa calendarios por cuenta y prioriza el calendario principal editable, elimina nombres placeholder de proveedores locales, incorpora Baseline/Startup Profiles y reduce observadores de datos pesados cuando Materias/TuNot no están visibles. Mantiene la caché controlada de v0.8.28 y Gemma 4 sobre LiteRT-LM 0.11.0."
)
workflow.write_text(w, encoding="utf-8")
