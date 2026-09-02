package com.notcan.app.ui.home

import android.content.res.Configuration

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WriterNoteEditor(
    note: NotePageEntity,
    onUpdateNote: (String, String, String) -> Unit,
    onShareFallback: () -> Unit,
    onDeleteNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val landscapeIme = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        WindowInsets.ime.getBottom(density) > 0
    val title = note.title.ifBlank { "Apuntes" }
    val draftPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("notcan_note_drafts", Context.MODE_PRIVATE)
    }
    val draftKey = "body_${note.id}"
    val draftTimeKey = "time_${note.id}"
    val initialHtml = remember(note.id) {
        val stored = normalizeStoredBody(note.body)
        val draft = draftPreferences.getString(draftKey, null)
        val draftTime = draftPreferences.getLong(draftTimeKey, 0L)
        if (!draft.isNullOrBlank() && draftTime > note.updatedAtEpochMs && !isEffectivelyEmptyHtml(draft)) draft else stored
    }
    var html by remember(note.id) { mutableStateOf(initialHtml) }
    var lastSavedHtml by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }
    var webView by remember(note.id) { mutableStateOf<WebView?>(null) }
    var bridge by remember(note.id) { mutableStateOf<NoteBridge?>(null) }
    var userEdited by remember(note.id) { mutableStateOf(false) }
    var confirmDelete by remember(note.id) { mutableStateOf(false) }
    var shareMenu by remember(note.id) { mutableStateOf(false) }
    var editing by remember(note.id) { mutableStateOf(false) }
    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(note.id, note.body) {
        val externalHtml = normalizeStoredBody(note.body)
        val localDirty = html != lastSavedHtml
        when {
            externalHtml == html -> lastSavedHtml = externalHtml
            !localDirty -> {
                html = externalHtml
                lastSavedHtml = externalHtml
                webView?.loadDataWithBaseURL(null, writerDocument(externalHtml, darkEditor), "text/html", "UTF-8", null)
            }
            externalHtml == lastSavedHtml -> Unit
            else -> Unit // Preserve the newer local draft until it is saved.
        }
    }

    LaunchedEffect(note.id, editing) {
        webView?.evaluateJavascript("window.notcanSetEditing(${if (editing) "true" else "false"});", null)
    }

    LaunchedEffect(note.id, html) {
        val pending = html
        draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()
        delay(350)
        val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)
        if (pending != lastSavedHtml && safeToPersist) {
            onUpdateNote(note.id, title, pending)
            lastSavedHtml = pending
        }
    }

    DisposableEffect(note.id) {
        onDispose {
            val pending = html
            draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()
            val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)
            if (pending != lastSavedHtml && safeToPersist) onUpdateNote(note.id, title, pending)
            bridge?.deactivate()
            webView?.removeJavascriptInterface("NotCanBridge")
            webView?.destroy()
            bridge = null
            webView = null
        }
    }

    fun command(name: String, value: String? = null) {
        val safeValue = value?.replace("\\", "\\\\")?.replace("'", "\\'")
        val js = if (safeValue == null) "window.notcanCommand('$name', null);" else "window.notcanCommand('$name', '$safeValue');"
        webView?.evaluateJavascript(js, null)
    }

    Card(modifier = modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (landscapeIme) 2.dp else 8.dp)) {
            if (!landscapeIme) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { editing = !editing }) {
                        Icon(Icons.Default.Edit, if (editing) "Terminar edición" else "Editar", tint = if (editing) NotCanBlue else NotCanGray)
                    }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir apunte", tint = NotCanBlue) }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(text = { Text("Compartir como DOCX") }, onClick = {
                            shareMenu = false
                            runCatching { NoteFileExport.share(context, title, html, NoteFileExport.Format.DOCX) }.onFailure { onShareFallback() }
                        })
                        DropdownMenuItem(text = { Text("Compartir como PDF") }, onClick = {
                            shareMenu = false
                            runCatching { NoteFileExport.share(context, title, html, NoteFileExport.Format.PDF) }.onFailure { onShareFallback() }
                        })
                        DropdownMenuItem(text = { Text("Compartir como texto") }, onClick = {
                            shareMenu = false
                            runCatching { NoteFileExport.share(context, title, html, NoteFileExport.Format.TEXT) }.onFailure { onShareFallback() }
                        })
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar apunte", tint = NotCanRed) }
                }
                Divider(color = NotCanGray.copy(alpha = 0.20f))
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (!editing) {
                    IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayar selección") }
                    WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }
                    Text("  Selecciona texto para anotar", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                } else {
                WriterStructureButton("T1") { command("formatBlock", "H1") }
                WriterStructureButton("T2") { command("formatBlock", "H2") }
                WriterStructureButton("P") { command("formatBlock", "P") }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { command("bold") }) { Icon(Icons.Default.FormatBold, "Negrita") }
                IconButton(onClick = { command("italic") }) { Icon(Icons.Default.FormatItalic, "Cursiva") }
                IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayado") }
                IconButton(onClick = { command("strikeThrough") }) { Icon(Icons.Default.FormatStrikethrough, "Tachado") }
                IconButton(onClick = { command("insertUnorderedList") }) { Icon(Icons.Default.FormatListBulleted, "Viñetas") }
                IconButton(onClick = { command("insertOrderedList") }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }
                IconButton(onClick = { command("removeFormat") }) { Icon(Icons.Default.FormatClear, "Limpiar formato") }
                Spacer(Modifier.width(6.dp))
                WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }
                WriterColorButton(Color(0xFF8EE39A)) { command("hiliteColor", "#8EE39A") }
                WriterColorButton(Color(0xFF7EC8FF)) { command("hiliteColor", "#7EC8FF") }
                WriterColorButton(Color(0xFFFF9BB8)) { command("hiliteColor", "#FF9BB8") }
                }
            }
            Divider(color = NotCanGray.copy(alpha = 0.20f))

            key(note.id) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = {
                        NotCanWriterWebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowContentAccess = false
                            settings.allowFileAccess = false
                            settings.setSupportZoom(false)
                            isVerticalScrollBarEnabled = true
                            webViewClient = WebViewClient()
                            val activeBridge = NoteBridge(note.id) { bridgeNoteId, newHtml ->
                                if (bridgeNoteId == note.id) {
                                    userEdited = true
                                    html = newHtml
                                    draftPreferences.edit()
                                        .putString(draftKey, newHtml)
                                        .putLong(draftTimeKey, System.currentTimeMillis())
                                        .apply()
                                }
                            }
                            bridge = activeBridge
                            addJavascriptInterface(activeBridge, "NotCanBridge")
                            loadDataWithBaseURL(null, writerDocument(html, darkEditor), "text/html", "UTF-8", null)
                            webView = this
                        }
                    },
                    update = { view -> if (webView !== view) webView = view }
                )
            }
            if (!landscapeIme) Text("Guardado automático", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminar apunte") },
        text = { Text("Se eliminará esta página de apuntes. El audio y la transcripción de la clase se conservarán.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteNote() }) { Text("Eliminar", color = NotCanRed) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
    )
}

private class NotCanWriterWebView(context: Context) : WebView(context)

@Composable
private fun WriterStructureButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp).size(width = 38.dp, height = 34.dp).clickable(onClick = onClick),
        color = NotCanGray.copy(alpha = 0.10f),
        shape = RoundedCornerShape(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WriterColorButton(color: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.padding(horizontal = 4.dp).size(24.dp).clickable(onClick = onClick), color = color, shape = RoundedCornerShape(5.dp)) { }
}

private class NoteBridge(
    private val noteId: String,
    private val onChanged: (String, String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = true

    @JavascriptInterface
    fun onContentChanged(value: String) {
        main.post { if (active) onChanged(noteId, value) }
    }

    fun deactivate() { active = false }
}

private fun isEffectivelyEmptyHtml(value: String): Boolean = value
    .replace(Regex("(?is)<br\\s*/?>"), "")
    .replace(Regex("(?is)<[^>]+>"), "")
    .replace("&nbsp;", "")
    .replace("&#160;", "")
    .trim()
    .isEmpty()

private fun normalizeStoredBody(value: String): String {
    val trimmed = value.trim()
    val looksLikeHtml = Regex("(?is)<(p|div|h[1-6]|ul|ol|li|span|br|strong|em|u|s)(\\s|>|/)").containsMatchIn(trimmed)
    return if (looksLikeHtml) sanitizeHtml(trimmed) else "<p>${TextUtils.htmlEncode(value).replace("\n", "<br>")}</p>"
}

private fun sanitizeHtml(value: String): String = value
    .replace(Regex("(?is)<script.*?>.*?</script>"), "")
    .replace(Regex("(?is)<iframe.*?>.*?</iframe>"), "")
    .replace(Regex("(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1"), "")

private fun writerDocument(initialBody: String, darkTheme: Boolean): String {
    val textColor = if (darkTheme) "#F3F4F6" else "#20252C"
    val selectionText = if (darkTheme) "white" else "#172033"
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>html,body{margin:0;padding:0;background:transparent;color:$textColor;font-family:sans-serif;font-size:17px;height:100%}#editor{box-sizing:border-box;min-height:100%;padding:12px 8px 120px;outline:none;line-height:1.55;caret-color:#7EA2FF}#editor p{margin:0 0 .55em}#editor h1{font-size:1.55em;margin:.5em 0 .35em}#editor h2{font-size:1.3em;margin:.45em 0 .3em}#editor ul,#editor ol{padding-left:1.6em}::selection{background:#B9D0FF;color:$selectionText}#selbar{position:fixed;display:none;z-index:50;background:#242830;border:1px solid #444b57;border-radius:14px;padding:3px;box-shadow:0 4px 16px rgba(0,0,0,.3)}#selbar button{width:34px;height:30px;border:0;border-radius:10px;background:#343a46;color:#FFE066;font-size:18px;line-height:1}</style></head>
<body><div id="selbar"><button id="markBtn" aria-label="Subrayar" title="Subrayar">▰</button></div><div id="editor" contenteditable="false" spellcheck="true">$initialBody</div><script>(function(){const editor=document.getElementById('editor');let savedRange=null;function inside(){const s=window.getSelection();if(!s||s.rangeCount===0)return false;const n=s.getRangeAt(0).commonAncestorContainer;return n===editor||editor.contains(n.nodeType===3?n.parentNode:n)}function save(){const s=window.getSelection();if(s&&s.rangeCount>0&&inside())savedRange=s.getRangeAt(0).cloneRange()}function restore(){if(!savedRange)return;const s=window.getSelection();s.removeAllRanges();s.addRange(savedRange)}function notify(){if(window.NotCanBridge)window.NotCanBridge.onContentChanged(editor.innerHTML)}window.notcanSetEditing=function(v){editor.contentEditable=v?'true':'false';if(v)editor.focus()};window.notcanCommand=function(c,v){restore();document.execCommand(c,false,v||null);save();notify()};document.addEventListener('selectionchange',function(){if(inside())save()});editor.addEventListener('keyup',save);editor.addEventListener('mouseup',save);editor.addEventListener('touchend',function(){setTimeout(save,0)});editor.addEventListener('input',function(){save();notify()})})();</script><script>(function(){const editor=document.getElementById('editor'),bar=document.getElementById('selbar'),btn=document.getElementById('markBtn');function selected(){const s=window.getSelection();if(!s||s.rangeCount===0||s.isCollapsed)return null;const r=s.getRangeAt(0),n=r.commonAncestorContainer,p=n.nodeType===3?n.parentNode:n;if(!(p===editor||editor.contains(p)))return null;return r}function place(){const r=selected();if(!r){bar.style.display='none';return}const rect=r.getBoundingClientRect();bar.style.display='block';const left=Math.max(8,Math.min(window.innerWidth-46,rect.left+rect.width/2-20));const top=Math.max(8,rect.top-42);bar.style.left=left+'px';bar.style.top=top+'px'}document.addEventListener('selectionchange',function(){setTimeout(place,0)});editor.addEventListener('mouseup',place);editor.addEventListener('touchend',function(){setTimeout(place,30)});btn.addEventListener('pointerdown',function(e){e.preventDefault();if(!selected())return;document.execCommand('hiliteColor',false,'#FFE066');if(window.NotCanBridge)window.NotCanBridge.onContentChanged(editor.innerHTML);bar.style.display='none'})})();</script></body></html>
""".trimIndent()
}
