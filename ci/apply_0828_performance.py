from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f"Missing target: {label} in {path}")
    path.write_text(text.replace(old, new, 1))


main = ROOT / "app/src/main/java/com/notcan/app/MainActivity.kt"
replace_once(
    main,
    '        lifecycleScope.launch(Dispatchers.IO) { StorageMaintenance.cleanupObsoleteAi(this@MainActivity) }',
    '        lifecycleScope.launch(Dispatchers.IO) { StorageMaintenance.runStartupMaintenance(this@MainActivity) }',
    'startup maintenance',
)

settings = ROOT / "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
replace_once(
    settings,
    'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch',
    'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext',
    'settings coroutine imports',
)
replace_once(
    settings,
    '''    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            refreshTick++
        }
    }
''',
    '''    LaunchedEffect(Unit) {
        while (true) {
            val downloading = withContext(Dispatchers.IO) {
                runCatching { whisperManager.state() == WhisperModelState.DOWNLOADING }.getOrDefault(false) ||
                    runCatching { liveManager.state() == LiveTranscriptionModelState.DOWNLOADING }.getOrDefault(false) ||
                    runCatching { gemmaManager.state() == GemmaLiteRtModelState.DOWNLOADING }.getOrDefault(false)
            }
            delay(if (downloading) 1_500 else 30_000)
            refreshTick++
        }
    }
''',
    'adaptive settings polling',
)
replace_once(
    settings,
    '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fuentes de apuntes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
''',
    '''        StoragePerformanceSection()

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fuentes de apuntes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
''',
    'storage performance section',
)

build = ROOT / "app/build.gradle.kts"
replace_once(build, 'versionCode = 50\n        versionName = "0.8.27"', 'versionCode = 51\n        versionName = "0.8.28"', 'version bump')
replace_once(
    build,
    '''tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(prepareTestKeystore)
}
''',
    '''tasks.matching { it.name == "preDebugBuild" || it.name == "preReleaseBuild" }.configureEach {
    dependsOn(prepareTestKeystore)
}
''',
    'release keystore preparation',
)
replace_once(
    build,
    '''        release {
            isMinifyEnabled = false
            proguardFiles(
''',
    '''        release {
            signingConfig = signingConfigs.getByName("notcanTest")
            isMinifyEnabled = false
            proguardFiles(
''',
    'release signing',
)

print("Applied v0.8.28 performance patch")
