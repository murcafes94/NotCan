from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str, count: int = 1):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, count))

writer = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace(
    writer,
    "function applyFontSize(px){if(!restore())return;const n=Math.max(10,Math.min(48,parseInt(px||'17',10)||17));const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.fontSize=n+'px';try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){}}",
    "function applyFontSize(px){if(!restore())return;const n=Math.max(10,Math.min(48,parseInt(px||'17',10)||17));const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.fontSize=n+'px';try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){}}\nfunction blockFor(node){let e=node&&node.nodeType===1?node:node&&node.parentElement;while(e&&e!==editor){if(/^(P|DIV|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE)$/.test(e.tagName))return e;e=e.parentElement}return null}\nfunction applyAlignment(mode){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount)return;const r=s.getRangeAt(0).cloneRange();const blocks=[];editor.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,li,blockquote').forEach(function(el){try{if(r.intersectsNode(el))blocks.push(el)}catch(e){}});if(!blocks.length){const b=blockFor(r.startContainer);if(b)blocks.push(b)}const align=mode==='full'?'justify':mode==='center'?'center':mode==='right'?'right':'left';Array.from(new Set(blocks)).forEach(function(b){b.style.textAlign=align;if(align==='justify')b.style.textJustify='inter-word';else b.style.removeProperty('text-justify')});save();notify()}"
)
replace(
    writer,
    "window.notcanCommand=function(c,v){if(c==='fontSizePx')applyFontSize(v);else withEditable(c,v)};",
    "window.notcanCommand=function(c,v){if(c==='fontSizePx')applyFontSize(v);else if(c==='justifyFull')applyAlignment('full');else if(c==='justifyCenter')applyAlignment('center');else if(c==='justifyRight')applyAlignment('right');else if(c==='justifyLeft')applyAlignment('left');else withEditable(c,v)};"
)

quick = "app/src/main/java/com/notcan/app/ui/ai/TuNotQuickAssistant.kt"
replace(
    quick,
    '''                OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Pregunta o pide un mapa…") }, minLines = 1, maxLines = 4, trailingIcon = { IconButton(enabled = question.isNotBlank() && !onlineBusy, onClick = ::submit) { Icon(NotCanIcons.Next, "Enviar", tint = NotCanBlue) } })\n\n                when {''',
    '''                OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Pregunta o pide un mapa…") }, minLines = 1, maxLines = 4, trailingIcon = { IconButton(enabled = question.isNotBlank() && !onlineBusy, onClick = ::submit) { Icon(NotCanIcons.Next, "Enviar", tint = NotCanBlue) } })\n                // Acción persistente: una respuesta larga nunca puede empujarla fuera del panel.\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                    TextButton(onClick = onOpenFullChat) { Text("Abrir TuNot completo") }\n                }\n\n                when {'''
)
replace(
    quick,
    '''                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onOpenFullChat) { Text("Abrir TuNot completo") } }\n''',
    ''''''
)

gradle = "app/build.gradle.kts"
replace(gradle, 'versionCode = 36\n        versionName = "0.8.17"', 'versionCode = 37\n        versionName = "0.8.17.1"')

print("0.8.17.1 bugfix applied")
