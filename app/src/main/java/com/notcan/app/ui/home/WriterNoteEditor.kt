package com.notcan.app.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    var title by remember(note.id) { mutableStateOf(note.title.ifBlank { "Apuntes" }) }
    var html by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }
    var lastSavedTitle by remember(note.id) { mutableStateOf(note.title) }
    var lastSavedHtml by remember(note.id) { mutableStateOf(note.body) }
    var webView by remember(note.id) { mutableStateOf<WebView?>(null) }
    var confirmDelete by remember(note.id) { mutableStateOf(false) }
    var toolsOpen by remember(note.id) { mutableStateOf(false) }

    LaunchedEffect(note.id, title, html) {
        delay(500)
        if (title != lastSavedTitle || html != lastSavedHtml) {
            onUpdateNote(note.id, title.ifBlank { "Apuntes" }, html)
            lastSavedTitle = title.ifBlank { "Apuntes" }
            lastSavedHtml = html
        }
    }

    DisposableEffect(note.id) {
        onDispose {
            webView?.removeJavascriptInterface("NotCanBridge")
            webView?.destroy()
            webView = null
        }
    }

    fun command(name: String, value: String? = null) {
        val safeValue = value?.replace("\\", "\\\\")?.replace("'", "\\'")
        val js = if (safeValue == null) "window.notcanCommand('$name', null);" else "window.notcanCommand('$name', '$safeValue');"
        webView?.evaluateJavascript(js, null)
    }

    Card(modifier = modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { toolsOpen = true }) { Icon(Icons.Default.Menu, "Formato, estructura y bloques", tint = NotCanBlue) }
                DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                    DropdownMenuItem(text = { Text("Título 1") }, onClick = { command("formatBlock", "H1"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Título 2") }, onClick = { command("formatBlock", "H2"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Párrafo") }, onClick = { command("formatBlock", "P"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Lista con viñetas") }, onClick = { command("insertUnorderedList"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Lista numerada") }, onClick = { command("insertOrderedList"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Quitar formato") }, onClick = { command("removeFormat"); toolsOpen = false })
                    DropdownMenuItem(text = { Text("Compartir") }, onClick = { toolsOpen = false; runCatching { shareHtmlNote(context, title, html) }.onFailure { onShareFallback() } })
                    DropdownMenuItem(text = { Text("Eliminar apunte", color = NotCanRed) }, onClick = { toolsOpen = false; confirmDelete = true })
                }
            }

            Divider(color = NotCanGray.copy(alpha = 0.20f))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                IconButton(onClick = { command("bold") }) { Icon(Icons.Default.FormatBold, "Negrita") }
                IconButton(onClick = { command("italic") }) { Icon(Icons.Default.FormatItalic, "Cursiva") }
                IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayado") }
                IconButton(onClick = { command("strikeThrough") }) { Icon(Icons.Default.FormatStrikethrough, "Tachado") }
                IconButton(onClick = { command("insertUnorderedList") }) { Icon(Icons.Default.FormatListBulleted, "Viñetas") }
                IconButton(onClick = { command("insertOrderedList") }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }
                IconButton(onClick = { command("removeFormat") }) { Icon(Icons.Default.FormatClear, "Limpiar formato") }
                WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }
                WriterColorButton(Color(0xFF8EE39A)) { command("hiliteColor", "#8EE39A") }
                WriterColorButton(Color(0xFF7EC8FF)) { command("hiliteColor", "#7EC8FF") }
                WriterColorButton(Color(0xFFFF9BB8)) { command("hiliteColor", "#FF9BB8") }
            }
            Divider(color = NotCanGray.copy(alpha = 0.20f))

            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        settings.setSupportZoom(false)
                        isVerticalScrollBarEnabled = true
                        webViewClient = WebViewClient()
                        addJavascriptInterface(NoteBridge { newHtml -> html = newHtml }, "NotCanBridge")
                        loadDataWithBaseURL(null, writerDocument(html), "text/html", "UTF-8", null)
                        webView = this
                    }
                },
                update = { view -> if (webView !== view) webView = view }
            )
            Text("Guardado automático", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
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

@Composable private fun WriterColorButton(color: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.padding(horizontal = 4.dp).size(24.dp).clickable(onClick = onClick), color = color, shape = RoundedCornerShape(5.dp)) { }
}

private class NoteBridge(private val onChanged: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun onContentChanged(value: String) { main.post { onChanged(value) } }
}

private fun shareHtmlNote(context: Context, title: String, html: String) {
    val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, "$title\n\n$plain")
    context.startActivity(Intent.createChooser(intent, "Compartir apuntes"))
}

private fun normalizeStoredBody(value: String): String {
    val trimmed = value.trim()
    val looksLikeHtml = Regex("(?is)<(p|div|h[1-6]|ul|ol|li|span|br|strong|em|u|s)(\\s|>|/)").containsMatchIn(trimmed)
    return if (looksLikeHtml) sanitizeHtml(trimmed) else "<p>${TextUtils.htmlEncode(value).replace("\n", "<br>")}</p>"
}

private fun sanitizeHtml(value: String): String = value
    .replace(Regex("(?is)<script.*?>.*?</script>"), "")
    .replace(Regex("(?is)<iframe.*?>.*?</iframe>"), "")
    .replace(Regex("(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1"), "")

private fun writerDocument(initialBody: String): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>html,body{margin:0;padding:0;background:transparent;color:#F3F4F6;font-family:sans-serif;font-size:17px;height:100%}#editor{box-sizing:border-box;min-height:100%;padding:12px 8px 120px;outline:none;line-height:1.55;caret-color:#7EA2FF}#editor p{margin:0 0 .55em}#editor h1{font-size:1.55em;margin:.5em 0 .35em}#editor h2{font-size:1.3em;margin:.45em 0 .3em}#editor ul,#editor ol{padding-left:1.6em}::selection{background:#3159A7;color:white}</style></head>
<body><div id="editor" contenteditable="true" spellcheck="true">$initialBody</div><script>(function(){const editor=document.getElementById('editor');let savedRange=null;function inside(){const s=window.getSelection();if(!s||s.rangeCount===0)return false;const n=s.getRangeAt(0).commonAncestorContainer;return n===editor||editor.contains(n.nodeType===3?n.parentNode:n)}function save(){const s=window.getSelection();if(s&&s.rangeCount>0&&inside())savedRange=s.getRangeAt(0).cloneRange()}function restore(){if(!savedRange)return;const s=window.getSelection();s.removeAllRanges();s.addRange(savedRange)}function notify(){if(window.NotCanBridge)window.NotCanBridge.onContentChanged(editor.innerHTML)}window.notcanCommand=function(c,v){editor.focus();restore();document.execCommand(c,false,v||null);save();notify()};document.addEventListener('selectionchange',function(){if(inside())save()});editor.addEventListener('keyup',save);editor.addEventListener('mouseup',save);editor.addEventListener('touchend',function(){setTimeout(save,0)});editor.addEventListener('input',function(){save();notify()})})();</script></body></html>
""".trimIndent()
