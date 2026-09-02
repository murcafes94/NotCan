from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")

def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:120]!r}")
    text = text.replace(old, new, 1)
    write(path, text)

# -----------------------------------------------------------------------------
# Native Android selection toolbar for notes.
# -----------------------------------------------------------------------------
native_webview = r'''package com.notcan.app.ui.home

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * WebView de apuntes que conserva la selección nativa de Android y añade la
 * acción Subrayar al floating ActionMode. Es el mismo enfoque usado por
 * Ministerium: tiradores y copiar/compartir siguen siendo responsabilidad del
 * sistema; NotCan solo agrega su acción académica.
 */
internal class NativeSelectionWebView(context: Context) : WebView(context) {
    private interface WrappedSelectionCallback

    private var onHighlightRequested: (() -> Unit)? = null
    private var lastSelectionX = -1f
    private var lastSelectionY = -1f

    fun setOnHighlightRequested(callback: (() -> Unit)?) {
        onHighlightRequested = callback
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_MOVE -> {
                    lastSelectionX = event.x
                    lastSelectionY = event.y
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(wrap(callback), ActionMode.TYPE_FLOATING)
        } else {
            super.startActionMode(wrap(callback))
        }
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(wrap(callback), ActionMode.TYPE_FLOATING)
        } else {
            super.startActionMode(wrap(callback), type)
        }
    }

    private fun wrap(original: ActionMode.Callback): ActionMode.Callback {
        if (original is WrappedSelectionCallback) return original
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SelectionCallback2(original)
        } else {
            SelectionCallback(original)
        }
    }

    private fun populate(menu: Menu?) {
        if (menu == null || menu.findItem(ACTION_HIGHLIGHT) != null) return
        menu.add(Menu.NONE, ACTION_HIGHLIGHT, 90, "Subrayar").apply {
            setIcon(android.R.drawable.ic_menu_edit)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun onCreate(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        val created = original.onCreateActionMode(mode, menu)
        if (created) populate(menu)
        return created
    }

    private fun onPrepare(original: ActionMode.Callback, mode: ActionMode, menu: Menu): Boolean {
        val changed = original.onPrepareActionMode(mode, menu)
        populate(menu)
        return changed || menu.findItem(ACTION_HIGHLIGHT) != null
    }

    private fun onClicked(original: ActionMode.Callback, mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == ACTION_HIGHLIGHT) {
            // La selección queda clonada dentro del documento JS; se puede cerrar el
            // ActionMode sin perder el rango que después estiliza el diálogo.
            onHighlightRequested?.invoke()
            mode.finish()
            return true
        }
        return original.onActionItemClicked(mode, item)
    }

    private fun fallbackRect(view: View, outRect: Rect) {
        val density = resources.displayMetrics.density
        val halfWidth = maxOf(20, (28f * density).toInt())
        val halfHeight = maxOf(14, (20f * density).toInt())
        val x = if (lastSelectionX >= 0f) lastSelectionX.toInt() else view.width / 2
        val y = if (lastSelectionY >= 0f) lastSelectionY.toInt() else view.height / 2
        val left = maxOf(0, x - halfWidth)
        val top = maxOf(0, y - halfHeight)
        val right = minOf(view.width, x + halfWidth).coerceAtLeast(left + 1)
        val bottom = minOf(view.height, y + halfHeight).coerceAtLeast(top + 1)
        outRect.set(left, top, right, bottom)
    }

    private fun unusableRect(view: View, rect: Rect): Boolean {
        if (rect.isEmpty) return true
        val viewArea = maxOf(1, view.width).toLong() * maxOf(1, view.height).toLong()
        val rectArea = maxOf(0, rect.width()).toLong() * maxOf(0, rect.height()).toLong()
        if (rectArea * 100L > viewArea * 55L) return true
        return rect.right < 0 || rect.bottom < 0 || rect.left > view.width || rect.top > view.height
    }

    private inner class SelectionCallback(
        private val original: ActionMode.Callback
    ) : ActionMode.Callback, WrappedSelectionCallback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(original, mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(original, mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = onClicked(original, mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)
    }

    private inner class SelectionCallback2(
        private val original: ActionMode.Callback
    ) : ActionMode.Callback2(), WrappedSelectionCallback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = onCreate(original, mode, menu)
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = onPrepare(original, mode, menu)
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = onClicked(original, mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = original.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (original is ActionMode.Callback2) original.onGetContentRect(mode, view, outRect)
            else outRect.setEmpty()
            if (unusableRect(view, outRect)) fallbackRect(view, outRect)
        }
    }

    companion object {
        private const val ACTION_HIGHLIGHT = 9301
    }
}
'''
write("app/src/main/java/com/notcan/app/ui/home/NativeSelectionWebView.kt", native_webview)

p = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace_once(
    p,
    '    var editing by remember(note.id) { mutableStateOf(false) }\n    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f\n',
    '    var editing by remember(note.id) { mutableStateOf(false) }\n    var annotationPickerOpen by remember(note.id) { mutableStateOf(false) }\n    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f\n'
)
replace_once(
    p,
    '''    LaunchedEffect(note.id, editing) {\n        webView?.evaluateJavascript("window.notcanSetEditing(${if (editing) "true" else "false"});", null)\n    }\n\n''',
    '''    LaunchedEffect(note.id, editing) {\n        webView?.evaluateJavascript("window.notcanSetEditing(${if (editing) "true" else "false"});", null)\n    }\n\n    LaunchedEffect(note.id, darkEditor) {\n        webView?.evaluateJavascript("window.notcanSetTheme(${if (darkEditor) "true" else "false"});", null)\n    }\n\n'''
)
replace_once(
    p,
    '''            bridge?.deactivate()\n            webView?.removeJavascriptInterface("NotCanBridge")\n            webView?.destroy()\n''',
    '''            bridge?.deactivate()\n            (webView as? NativeSelectionWebView)?.setOnHighlightRequested(null)\n            webView?.removeJavascriptInterface("NotCanBridge")\n            webView?.destroy()\n'''
)
replace_once(
    p,
    '''                if (!editing) {\n                    IconButton(onClick = { command("underline") }) { Icon(Icons.Default.FormatUnderlined, "Subrayar selección") }\n                    WriterColorButton(Color(0xFFFFE066)) { command("hiliteColor", "#FFE066") }\n                    Text("  Selecciona texto para anotar", color = NotCanGray, style = MaterialTheme.typography.labelSmall)\n                } else {\n''',
    '''                if (!editing) {\n                    Text(\n                        "Mantén pulsado para seleccionar · Subrayar funciona sin editar",\n                        color = NotCanGray,\n                        style = MaterialTheme.typography.labelSmall,\n                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)\n                    )\n                } else {\n'''
)
replace_once(
    p,
    '                        NotCanWriterWebView(context).apply {\n',
    '                        NativeSelectionWebView(context).apply {\n'
)
replace_once(
    p,
    '''                            webViewClient = WebViewClient()\n                            val activeBridge = NoteBridge(note.id) { bridgeNoteId, newHtml ->\n''',
    '''                            webViewClient = WebViewClient()\n                            setOnHighlightRequested { annotationPickerOpen = true }\n                            val activeBridge = NoteBridge(note.id) { bridgeNoteId, newHtml ->\n'''
)
replace_once(
    p,
    '''    if (confirmDelete) AlertDialog(\n''',
    '''    if (annotationPickerOpen) HighlightPickerDialog(\n        onDismiss = { annotationPickerOpen = false },\n        onApply = { style, color ->\n            val safeStyle = style.replace("'", "")\n            val safeColor = color.replace("'", "")\n            webView?.evaluateJavascript("window.notcanApplyAnnotation('$safeStyle','$safeColor');", null)\n            annotationPickerOpen = false\n        }\n    )\n\n    if (confirmDelete) AlertDialog(\n'''
)

# Replace the old private WebView stub with the annotation picker. The native
# subclass now lives in NativeSelectionWebView.kt.
replace_once(
    p,
    'private class NotCanWriterWebView(context: Context) : WebView(context)\n\n',
    '''@Composable\nprivate fun HighlightPickerDialog(\n    onDismiss: () -> Unit,\n    onApply: (style: String, color: String) -> Unit\n) {\n    data class Choice(val label: String, val style: String, val color: String, val preview: Color?)\n    val choices = listOf(\n        Choice("Resaltado amarillo", "highlight", "#FFE066", Color(0xFFFFE066)),\n        Choice("Resaltado verde", "highlight", "#8EE39A", Color(0xFF8EE39A)),\n        Choice("Resaltado azul", "highlight", "#7EC8FF", Color(0xFF7EC8FF)),\n        Choice("Resaltado rosado", "highlight", "#FF9BB8", Color(0xFFFF9BB8)),\n        Choice("Subrayado azul", "underline", "#3478F6", Color(0xFF3478F6)),\n        Choice("Subrayado rojo", "underline", "#D55460", Color(0xFFD55460)),\n        Choice("Quitar formato", "clear", "", null)\n    )\n    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text("Subrayar o resaltar") },\n        text = {\n            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {\n                choices.forEach { choice ->\n                    TextButton(\n                        onClick = { onApply(choice.style, choice.color) },\n                        modifier = Modifier.fillMaxWidth()\n                    ) {\n                        Row(\n                            Modifier.fillMaxWidth(),\n                            verticalAlignment = Alignment.CenterVertically,\n                            horizontalArrangement = Arrangement.spacedBy(12.dp)\n                        ) {\n                            if (choice.preview != null) {\n                                Surface(\n                                    color = choice.preview,\n                                    shape = RoundedCornerShape(5.dp),\n                                    modifier = Modifier.size(width = 30.dp, height = 18.dp)\n                                ) {}\n                            } else {\n                                Surface(\n                                    color = MaterialTheme.colorScheme.surfaceVariant,\n                                    shape = RoundedCornerShape(5.dp),\n                                    modifier = Modifier.size(width = 30.dp, height = 18.dp)\n                                ) {}\n                            }\n                            Text(choice.label, color = MaterialTheme.colorScheme.onSurface)\n                        }\n                    }\n                }\n            }\n        },\n        confirmButton = {},\n        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }\n    )\n}\n\n'''
)

# Replace writerDocument completely: no custom floating pill. Native Android
# selection remains visible in read mode and annotations are applied using the
# saved selection while temporarily enabling document commands.
text = read(p)
pattern = re.compile(r'private fun writerDocument\(initialBody: String, darkTheme: Boolean\): String \{.*?\n\}', re.S)
new_writer = r'''private fun writerDocument(initialBody: String, darkTheme: Boolean): String {
    val textColor = if (darkTheme) "#F3F4F6" else "#20252C"
    val selectionText = if (darkTheme) "#FFFFFF" else "#172033"
    val selectionBg = if (darkTheme) "#355A8F" else "#B9D0FF"
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
:root{--notcan-text:$textColor;--notcan-selection:$selectionBg;--notcan-selection-text:$selectionText}
html,body{margin:0;padding:0;background:transparent;color:var(--notcan-text);font-family:sans-serif;font-size:17px;height:100%}
#editor{box-sizing:border-box;min-height:100%;padding:12px 8px 120px;outline:none;line-height:1.55;caret-color:#3478F6;color:var(--notcan-text)}
#editor p{margin:0 0 .55em}#editor h1{font-size:1.55em;margin:.5em 0 .35em}#editor h2{font-size:1.3em;margin:.45em 0 .3em}#editor ul,#editor ol{padding-left:1.6em}
::selection{background:var(--notcan-selection);color:var(--notcan-selection-text)}
</style></head>
<body><div id="editor" contenteditable="false" spellcheck="true">$initialBody</div>
<script>(function(){
const editor=document.getElementById('editor');let savedRange=null;
function inside(){const s=window.getSelection();if(!s||s.rangeCount===0)return false;const n=s.getRangeAt(0).commonAncestorContainer;return n===editor||editor.contains(n.nodeType===3?n.parentNode:n)}
function save(){const s=window.getSelection();if(s&&s.rangeCount>0&&!s.isCollapsed&&inside())savedRange=s.getRangeAt(0).cloneRange()}
function restore(){if(!savedRange)return false;const s=window.getSelection();s.removeAllRanges();s.addRange(savedRange.cloneRange());return true}
function notify(){if(window.NotCanBridge)window.NotCanBridge.onContentChanged(editor.innerHTML)}
function withEditable(command,value){if(!restore())return;const old=editor.contentEditable;editor.contentEditable='true';restore();try{document.execCommand(command,false,value||null)}finally{editor.contentEditable=old==='true'?'true':'false'}save();notify()}
function wrapUnderline(color){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.textDecoration='underline 2px '+(color||'#3478F6');span.style.textUnderlineOffset='3px';span.setAttribute('data-notcan-annotation','underline');try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){withEditable('underline',null)}}
window.notcanSetEditing=function(v){editor.contentEditable=v?'true':'false';if(v)editor.focus()};
window.notcanSetTheme=function(dark){document.documentElement.style.setProperty('--notcan-text',dark?'#F3F4F6':'#20252C');document.documentElement.style.setProperty('--notcan-selection',dark?'#355A8F':'#B9D0FF');document.documentElement.style.setProperty('--notcan-selection-text',dark?'#FFFFFF':'#172033')};
window.notcanCommand=function(c,v){withEditable(c,v)};
window.notcanApplyAnnotation=function(style,color){if(style==='highlight')withEditable('hiliteColor',color||'#FFE066');else if(style==='underline')wrapUnderline(color);else if(style==='clear')withEditable('removeFormat',null)};
document.addEventListener('selectionchange',function(){if(inside())save()});editor.addEventListener('keyup',save);editor.addEventListener('mouseup',save);editor.addEventListener('touchend',function(){setTimeout(save,0)});editor.addEventListener('input',function(){save();notify()});
})();</script></body></html>
""".trimIndent()
}'''
text2, n = pattern.subn(new_writer, text, count=1)
if n != 1:
    raise RuntimeError(f"writerDocument replacement count={n}")
write(p, text2)

# -----------------------------------------------------------------------------
# Light mode: give every root page an actual light canvas (previously many
# screens were transparent over the old dark window), then tune the semantic
# palette for clear white/cream surfaces and readable contrast.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt"
replace_once(p, 'import androidx.compose.foundation.horizontalScroll\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.horizontalScroll\n')
replace_once(
    p,
    '    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {\n',
    '    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {\n'
)

p = "app/src/main/java/com/notcan/app/ui/theme/Color.kt"
text = read(p)
text = text.replace('''        NotCanBlack = Color(0xFFFFFCF7)\n        NotCanGraphite = Color(0xFFF6EFE6)\n        NotCanSurface = Color(0xFFFFFFFF)\n        NotCanSurfaceHigh = Color(0xFFF0E7DC)\n        NotCanSurfaceSoft = Color(0xFFFAF5EF)\n        NotCanBorder = Color(0xFFD8C9B9)\n        NotCanOffWhite = Color(0xFF20252C)\n        NotCanGray = Color(0xFF5D6672)\n        NotCanGrayMuted = Color(0xFF7A746D)\n''', '''        NotCanBlack = Color(0xFFFFFDF9)\n        NotCanGraphite = Color(0xFFF5F2EE)\n        NotCanSurface = Color(0xFFFFFFFF)\n        NotCanSurfaceHigh = Color(0xFFF7F3ED)\n        NotCanSurfaceSoft = Color(0xFFFBF9F6)\n        NotCanBorder = Color(0xFFD8D3CC)\n        NotCanOffWhite = Color(0xFF1F252C)\n        NotCanGray = Color(0xFF626B76)\n        NotCanGrayMuted = Color(0xFF7A838D)\n''')
write(p, text)

p = "app/src/main/java/com/notcan/app/ui/theme/Theme.kt"
text = read(p)
text = text.replace('''    background = Color(0xFFFFFCF7),\n    onBackground = Color(0xFF20252C),\n    surface = Color(0xFFF6EFE6),\n    onSurface = Color(0xFF20252C),\n    surfaceVariant = Color(0xFFFFFFFF),\n    onSurfaceVariant = Color(0xFF5D6672),\n    outline = Color(0xFFD8C9B9),\n''', '''    background = Color(0xFFFFFDF9),\n    onBackground = Color(0xFF1F252C),\n    surface = Color(0xFFFFFFFF),\n    onSurface = Color(0xFF1F252C),\n    surfaceVariant = Color(0xFFF5F2EE),\n    onSurfaceVariant = Color(0xFF626B76),\n    outline = Color(0xFFD8D3CC),\n''')
write(p, text)

# Version bump.
p = "app/build.gradle.kts"
text = read(p)
text = text.replace('versionCode = 34', 'versionCode = 35')
text = text.replace('versionName = "0.8.15"', 'versionName = "0.8.16"')
write(p, text)

print("0.8.16 selection/light patch applied successfully")
