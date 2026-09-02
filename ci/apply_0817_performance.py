from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"anchor not found in {path}: {old[:100]!r}")
    s2 = s.replace(old, new, count)
    p.write_text(s2)

# --- Writer editor: formatting, safe annotation removal, lower autosave I/O ---
writer = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace(writer,
'''import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Share''',
'''import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Share''')
replace(writer,
'''    var annotationPickerOpen by remember(note.id) { mutableStateOf(false) }
    val darkEditor''',
'''    var annotationPickerOpen by remember(note.id) { mutableStateOf(false) }
    var fontSizeMenuOpen by remember(note.id) { mutableStateOf(false) }
    val darkEditor''')
replace(writer,
'''    LaunchedEffect(note.id, html) {
        val pending = html
        draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()
        delay(350)
        val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)
        if (pending != lastSavedHtml && safeToPersist) {
            onUpdateNote(note.id, title, pending)
            lastSavedHtml = pending
        }
    }''',
'''    LaunchedEffect(note.id, html) {
        val pending = html
        if (pending == lastSavedHtml) return@LaunchedEffect
        // Evita escribir SharedPreferences y Room por cada tecla. El borrador sigue
        // guardándose pronto y onDispose fuerza una copia inmediata al salir.
        delay(120)
        draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()
        delay(480)
        val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)
        if (pending != lastSavedHtml && safeToPersist) {
            onUpdateNote(note.id, title, pending)
            lastSavedHtml = pending
        }
    }''')
replace(writer,
'''                                    html = newHtml
                                    draftPreferences.edit()
                                        .putString(draftKey, newHtml)
                                        .putLong(draftTimeKey, System.currentTimeMillis())
                                        .apply()''',
'''                                    html = newHtml''')
replace(writer,
'''                WriterStructureButton("P") { command("formatBlock", "P") }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { command("bold") })''',
'''                WriterStructureButton("P") { command("formatBlock", "P") }
                Box {
                    WriterStructureButton("Aa") { fontSizeMenuOpen = true }
                    DropdownMenu(expanded = fontSizeMenuOpen, onDismissRequest = { fontSizeMenuOpen = false }) {
                        listOf(12, 14, 16, 18, 20, 24, 28, 32).forEach { px ->
                            DropdownMenuItem(
                                text = { Text("$px pt") },
                                onClick = { fontSizeMenuOpen = false; command("fontSizePx", px.toString()) }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { command("bold") })''')
replace(writer,
'''                IconButton(onClick = { command("insertOrderedList") }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }
                IconButton(onClick = { command("removeFormat") }) { Icon(Icons.Default.FormatClear, "Limpiar formato") }''',
'''                IconButton(onClick = { command("insertOrderedList") }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }
                IconButton(onClick = { command("justifyLeft") }) { Icon(Icons.Default.FormatAlignLeft, "Alinear a la izquierda") }
                IconButton(onClick = { command("justifyCenter") }) { Icon(Icons.Default.FormatAlignCenter, "Centrar") }
                IconButton(onClick = { command("justifyRight") }) { Icon(Icons.Default.FormatAlignRight, "Alinear a la derecha") }
                IconButton(onClick = { command("justifyFull") }) { Icon(Icons.Default.FormatAlignJustify, "Justificar") }
                IconButton(onClick = { command("removeFormat") }) { Icon(Icons.Default.FormatClear, "Limpiar formato") }''')
replace(writer,
'''        Choice("Subrayado rojo", "underline", "#D55460", Color(0xFFD55460)),
        Choice("Quitar formato", "clear", "", null)''',
'''        Choice("Subrayado rojo", "underline", "#D55460", Color(0xFFD55460)),
        Choice("Quitar subrayado", "clearAnnotation", "", null)''')
replace(writer,
'''function wrapUnderline(color){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.textDecoration='underline 2px '+(color||'#3478F6');span.style.textUnderlineOffset='3px';span.setAttribute('data-notcan-annotation','underline');try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){withEditable('underline',null)}}''',
'''function wrapAnnotation(kind,color){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.setAttribute('data-notcan-annotation',kind);if(kind==='underline'){span.style.textDecoration='underline 2px '+(color||'#3478F6');span.style.textUnderlineOffset='3px'}else{span.style.backgroundColor=color||'#FFE066'}try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){if(kind==='underline')withEditable('underline',null);else withEditable('hiliteColor',color||'#FFE066')}}
function unwrap(el){const p=el&&el.parentNode;if(!p)return;while(el.firstChild)p.insertBefore(el.firstChild,el);p.removeChild(el)}
function clearAnnotation(){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount)return;const r=s.getRangeAt(0).cloneRange(),hits=[];function ancestors(n){let e=n&&n.nodeType===1?n:n&&n.parentElement;while(e&&e!==editor){if(e.getAttribute&&e.getAttribute('data-notcan-annotation'))hits.push(e);e=e.parentElement}}ancestors(r.startContainer);ancestors(r.endContainer);editor.querySelectorAll('[data-notcan-annotation]').forEach(function(el){try{if(r.intersectsNode(el))hits.push(el)}catch(e){}});const unique=Array.from(new Set(hits));if(unique.length){unique.forEach(unwrap);notify();return}const old=editor.contentEditable;editor.contentEditable='true';restore();try{document.execCommand('hiliteColor',false,'transparent')}finally{editor.contentEditable=old==='true'?'true':'false'}save();notify()}
function applyFontSize(px){if(!restore())return;const n=Math.max(10,Math.min(48,parseInt(px||'17',10)||17));const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.fontSize=n+'px';try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){}}''')
replace(writer,
'''window.notcanCommand=function(c,v){withEditable(c,v)};
window.notcanApplyAnnotation=function(style,color){if(style==='highlight')withEditable('hiliteColor',color||'#FFE066');else if(style==='underline')wrapUnderline(color);else if(style==='clear')withEditable('removeFormat',null)};''',
'''window.notcanCommand=function(c,v){if(c==='fontSizePx')applyFontSize(v);else withEditable(c,v)};
window.notcanApplyAnnotation=function(style,color){if(style==='highlight')wrapAnnotation('highlight',color);else if(style==='underline')wrapAnnotation('underline',color);else if(style==='clearAnnotation')clearAnnotation()};''')

# --- Native Android selection: make Subrayar the first NotCan/native toolbar action ---
native = "app/src/main/java/com/notcan/app/ui/home/NativeSelectionWebView.kt"
replace(native,
'''        menu.add(Menu.NONE, ACTION_HIGHLIGHT, 90, "Subrayar").apply {''',
'''        menu.add(Menu.NONE, ACTION_HIGHLIGHT, 0, "Subrayar").apply {''')
replace(native,
'''    private fun onCreate(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        val created = original.onCreateActionMode(mode, menu)
        if (created) populate(menu)
        return created
    }''',
'''    private fun onCreate(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        // Insertar antes de delegar hace que Android coloque Subrayar al inicio del
        // floating toolbar; luego volvemos a asegurarla por si Chromium recreó el menú.
        populate(menu)
        val created = original.onCreateActionMode(mode, menu)
        if (created) populate(menu)
        return created
    }''')

# --- Long recording performance: avoid O(n²) transcript concatenation and huge UI strings ---
recording = "app/src/main/java/com/notcan/app/recording/RecordingService.kt"
replace(recording,
'''import android.os.IBinder''',
'''import android.os.IBinder
import android.os.SystemClock''')
replace(recording,
'''    private var liveTranscriber: LocalLiveTranscriber? = null
    private var liveRawTranscript: String = ""
    private var pcmChannel''',
'''    private var liveTranscriber: LocalLiveTranscriber? = null
    private val liveRawTranscript = StringBuilder()
    private val liveCorrectedTranscript = StringBuilder()
    private var lastLiveTranscriptPublishElapsedMs = 0L
    private var pcmChannel''')
replace(recording,
'''            _liveTranscript.value = ""
            liveRawTranscript = ""''',
'''            _liveTranscript.value = ""
            liveRawTranscript.setLength(0)
            liveCorrectedTranscript.setLength(0)
            lastLiveTranscriptPublishElapsedMs = 0L''')
replace(recording,
'''                                liveRawTranscript = if (liveRawTranscript.isBlank()) raw else "$liveRawTranscript $raw"
                                val corrected = AcademicTranscriptionContext.correctLiveText(raw, academicTerms).trim()
                                if (corrected.isNotBlank()) {
                                    _liveTranscript.update { current ->
                                        if (current.isBlank()) corrected else "$current $corrected"
                                    }
                                }''',
'''                                appendTranscript(liveRawTranscript, raw)
                                val corrected = AcademicTranscriptionContext.correctLiveText(raw, academicTerms).trim()
                                if (corrected.isNotBlank()) {
                                    appendTranscript(liveCorrectedTranscript, corrected)
                                    publishLiveTranscript()
                                }''')
replace(recording,
'''            try { liveTranscriber?.close() } catch (_: Throwable) { }
            liveTranscriber = null

            val file = File(path)''',
'''            try { liveTranscriber?.close() } catch (_: Throwable) { }
            liveTranscriber = null
            publishLiveTranscript(force = true)

            val file = File(path)''')
replace(recording,
'''                val liveText = _liveTranscript.value.trim()
                val rawLiveText = liveRawTranscript.trim()''',
'''                val liveText = liveCorrectedTranscript.toString().trim()
                val rawLiveText = liveRawTranscript.toString().trim()''')
replace(recording,
'''    private fun markMoment() {''',
'''    private fun appendTranscript(builder: StringBuilder, value: String) {
        if (value.isBlank()) return
        if (builder.isNotEmpty()) builder.append(' ')
        builder.append(value)
    }

    private fun publishLiveTranscript(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastLiveTranscriptPublishElapsedMs < LIVE_TRANSCRIPT_PUBLISH_INTERVAL_MS) return
        lastLiveTranscriptPublishElapsedMs = now
        val full = liveCorrectedTranscript.toString()
        _liveTranscript.value = if (full.length <= LIVE_TRANSCRIPT_UI_MAX_CHARS) full
        else "… " + full.takeLast(LIVE_TRANSCRIPT_UI_MAX_CHARS)
    }

    private fun markMoment() {''')
replace(recording,
'''        liveTranscriber = null
        liveRawTranscript = ""
        autoStopMode''',
'''        liveTranscriber = null
        liveRawTranscript.setLength(0)
        liveCorrectedTranscript.setLength(0)
        lastLiveTranscriptPublishElapsedMs = 0L
        autoStopMode''')
replace(recording,
'''        private const val NOTIFICATION_ID = 7001''',
'''        private const val LIVE_TRANSCRIPT_PUBLISH_INTERVAL_MS = 350L
        private const val LIVE_TRANSCRIPT_UI_MAX_CHARS = 16_000
        private const val NOTIFICATION_ID = 7001''')

# --- Avoid rebuilding all TuNot offline source text on unrelated recompositions ---
main = "app/src/main/java/com/notcan/app/MainActivity.kt"
replace(main,
'''                val assistantOfflineEntries = buildList {''',
'''                val assistantOfflineEntries = remember(selectedSubject?.id, selectedClass?.id, notePages, transcripts, documents) {
                    buildList {''')
replace(main,
'''                    documents.forEach { document ->
                        add(
                            TuNotOfflineEntry(
                                title = document.displayName,
                                subtitle = "Documento local · ${document.documentType}",
                                text = document.displayName
                            )
                        )
                    }
                }
                val assistantOnlineConfigured''',
'''                    documents.forEach { document ->
                        add(
                            TuNotOfflineEntry(
                                title = document.displayName,
                                subtitle = "Documento local · ${document.documentType}",
                                text = document.displayName
                            )
                        )
                    }
                    }
                }
                val assistantOnlineConfigured''')

# --- Build optimization: optional arm64 package for current Android devices, keep source universal ---
gradle = "app/build.gradle.kts"
replace(gradle,
'''val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-1.13.6.aar").asFile''',
'''val notcanArm64Only = providers.gradleProperty("notcanArm64Only").orNull == "true"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-1.13.6.aar").asFile''')
replace(gradle,
'''        versionCode = 35
        versionName = "0.8.16"''',
'''        versionCode = 36
        versionName = "0.8.17"
        if (notcanArm64Only) {
            ndk { abiFilters += listOf("arm64-v8a") }
        }''')

print("0.8.17 performance patch applied")
