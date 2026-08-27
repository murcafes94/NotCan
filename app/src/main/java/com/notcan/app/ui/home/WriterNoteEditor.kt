package com.notcan.app.ui.home

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.delay

/**
 * Lightweight Writer-style local editor.
 *
 * NotCan deliberately does not embed the whole LibreOffice runtime here. Instead this editor keeps
 * rich notes as HTML and uses Android WebView's local contentEditable engine, preserving selection
 * while toolbar commands are applied. This fixes underline/highlight/color persistence that was
 * lossy when notes were converted to Markdown.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WriterNoteEditor(
    note: NotePageEntity,
    onUpdateNote: (String, String, String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var html by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }
    var lastSavedTitle by remember(note.id) { mutableStateOf(note.title) }
    var lastSavedHtml by remember(note.id) { mutableStateOf(note.body) }
    var webView by remember(note.id) { mutableStateOf<WebView?>(null) }

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
        val js = if (safeValue == null) {
            "window.notcanCommand('$name', null);"
        } else {
            "window.notcanCommand('$name', '$safeValue');"
        }
        webView?.evaluateJavascript(js, null)
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, "Compartir apuntes", tint = NotCanBlue)
                }
            }

            Spacer(Modifier.height(6.dp))
            Divider(color = NotCanGray.copy(alpha = 0.25f))

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                IconButton(onClick = { command("bold") }) { Icon(Icons.Default.FormatBold, "Negrita") }
                IconButton(onClick = { command("italic") }) { Icon(Icons.Default.FormatItalic, "Cursiva") }
                IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayado") }
                IconButton(onClick = { command("strikeThrough") }) { Icon(Icons.Default.FormatStrikethrough, "Tachado") }
                TextButton(onClick = { command("formatBlock", "H1") }) { Text("H1") }
                TextButton(onClick = { command("formatBlock", "H2") }) { Text("H2") }
                TextButton(onClick = { command("formatBlock", "P") }) { Text("P") }
                IconButton(onClick = { command("insertUnorderedList") }) { Icon(Icons.Default.FormatListBulleted, "Viñetas") }
                IconButton(onClick = { command("insertOrderedList") }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }

                Text("  Resaltado:", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }
                WriterColorButton(Color(0xFF8EE39A)) { command("hiliteColor", "#8EE39A") }
                WriterColorButton(Color(0xFF7EC8FF)) { command("hiliteColor", "#7EC8FF") }
                WriterColorButton(Color(0xFFFF9BB8)) { command("hiliteColor", "#FF9BB8") }

                Text("  Texto:", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                WriterTextColorButton("A", NotCanOffWhite) { command("foreColor", "#F3F4F6") }
                WriterTextColorButton("A", NotCanBlue) { command("foreColor", "#4D7CFF") }
                WriterTextColorButton("A", NotCanRed) { command("foreColor", "#E4485F") }
                WriterTextColorButton("A", Color(0xFF65C76F)) { command("foreColor", "#65C76F") }
            }

            Divider(color = NotCanGray.copy(alpha = 0.25f))
            Spacer(Modifier.height(5.dp))

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
                update = { view ->
                    if (webView !== view) webView = view
                }
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "Editor tipo Writer · HTML local · formato persistente · guardado automático",
                color = NotCanGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun WriterColorButton(color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(28.dp).clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(5.dp)
    ) { }
}

@Composable
private fun WriterTextColorButton(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = color) }
}

private class NoteBridge(private val onChanged: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onContentChanged(value: String) {
        main.post { onChanged(value) }
    }
}

private fun normalizeStoredBody(value: String): String {
    val trimmed = value.trim()
    val looksLikeHtml = Regex("(?is)<(p|div|h[1-6]|ul|ol|li|span|br|strong|em|u|s)(\\s|>|/)").containsMatchIn(trimmed)
    return if (looksLikeHtml) sanitizeHtml(trimmed) else {
        val escaped = TextUtils.htmlEncode(value).replace("\n", "<br>")
        "<p>$escaped</p>"
    }
}

private fun sanitizeHtml(value: String): String = value
    .replace(Regex("(?is)<script.*?>.*?</script>"), "")
    .replace(Regex("(?is)<iframe.*?>.*?</iframe>"), "")
    .replace(Regex("(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1"), "")

private fun writerDocument(initialBody: String): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
html, body { margin:0; padding:0; background:transparent; color:#F3F4F6; font-family:sans-serif; font-size:17px; height:100%; }
#editor { box-sizing:border-box; min-height:100%; padding:10px 8px 140px 8px; outline:none; line-height:1.5; caret-color:#7EA2FF; }
#editor p { margin:0 0 0.55em 0; }
#editor h1 { font-size:1.55em; margin:0.5em 0 0.35em; }
#editor h2 { font-size:1.3em; margin:0.45em 0 0.3em; }
#editor ul, #editor ol { padding-left:1.6em; }
::selection { background:#3159A7; color:white; }
</style>
</head>
<body>
<div id="editor" contenteditable="true" spellcheck="true">$initialBody</div>
<script>
(function() {
  const editor = document.getElementById('editor');
  let savedRange = null;

  function selectionInsideEditor() {
    const s = window.getSelection();
    if (!s || s.rangeCount === 0) return false;
    const node = s.getRangeAt(0).commonAncestorContainer;
    return node === editor || editor.contains(node.nodeType === 3 ? node.parentNode : node);
  }

  function saveSelection() {
    const s = window.getSelection();
    if (s && s.rangeCount > 0 && selectionInsideEditor()) {
      savedRange = s.getRangeAt(0).cloneRange();
    }
  }

  function restoreSelection() {
    if (!savedRange) return;
    const s = window.getSelection();
    s.removeAllRanges();
    s.addRange(savedRange);
  }

  function notifyAndroid() {
    if (window.NotCanBridge) {
      window.NotCanBridge.onContentChanged(editor.innerHTML);
    }
  }

  window.notcanCommand = function(command, value) {
    editor.focus();
    restoreSelection();
    document.execCommand(command, false, value || null);
    saveSelection();
    notifyAndroid();
  };

  document.addEventListener('selectionchange', function() {
    if (selectionInsideEditor()) saveSelection();
  });
  editor.addEventListener('keyup', saveSelection);
  editor.addEventListener('mouseup', saveSelection);
  editor.addEventListener('touchend', function() { setTimeout(saveSelection, 0); });
  editor.addEventListener('input', function() { saveSelection(); notifyAndroid(); });
})();
</script>
</body>
</html>
""".trimIndent()
