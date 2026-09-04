from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
writer_path = ROOT / 'app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt'
build_path = ROOT / 'app/build.gradle.kts'

s = writer_path.read_text()

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f'Missing target: {label}')
    return text.replace(old, new, 1)

s = replace_once(
    s,
    '    var fontSizeMenuOpen by remember(note.id) { mutableStateOf(false) }\n',
    '    var fontSizeMenuOpen by remember(note.id) { mutableStateOf(false) }\n    var fontFamilyMenuOpen by remember(note.id) { mutableStateOf(false) }\n',
    'font family state'
)

old_toolbar = '''                Box {
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
'''
new_toolbar = '''                Box {
                    WriterStructureButton("Aa") { fontSizeMenuOpen = true }
                    DropdownMenu(expanded = fontSizeMenuOpen, onDismissRequest = { fontSizeMenuOpen = false }) {
                        listOf(10, 11, 12, 14, 16, 18, 20, 24, 28, 32).forEach { pt ->
                            DropdownMenuItem(
                                text = { Text("$pt pt") },
                                onClick = { fontSizeMenuOpen = false; command("fontSizePt", pt.toString()) }
                            )
                        }
                    }
                }
                Box {
                    WriterStructureButton("Ab") { fontFamilyMenuOpen = true }
                    DropdownMenu(expanded = fontFamilyMenuOpen, onDismissRequest = { fontFamilyMenuOpen = false }) {
                        listOf(
                            "Roboto" to "Roboto, Arial, sans-serif",
                            "Arial" to "Arial, sans-serif",
                            "Times New Roman" to "'Times New Roman', serif",
                            "Georgia" to "Georgia, serif",
                            "Courier New" to "'Courier New', monospace",
                            "Cursiva" to "cursive"
                        ).forEach { (label, family) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { fontFamilyMenuOpen = false; command("fontFamily", family) }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
'''
s = replace_once(s, old_toolbar, new_toolbar, 'font toolbar')

old_font_js = "function applyFontSize(px){if(!restore())return;const n=Math.max(10,Math.min(48,parseInt(px||'17',10)||17));const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style.fontSize=n+'px';try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){}}"
new_font_js = "function applyInlineStyle(prop,value,dataName){if(!restore())return;const s=window.getSelection();if(!s||!s.rangeCount||s.isCollapsed)return;const r=s.getRangeAt(0).cloneRange();const span=document.createElement('span');span.style[prop]=value;if(dataName)span.setAttribute(dataName,value);try{span.appendChild(r.extractContents());r.insertNode(span);s.removeAllRanges();const nr=document.createRange();nr.selectNodeContents(span);s.addRange(nr);savedRange=nr.cloneRange();notify()}catch(e){}}\nfunction applyFontSize(pt){const n=Math.max(6,Math.min(72,parseInt(pt||'11',10)||11));applyInlineStyle('fontSize',n+'pt','data-notcan-font-size')}\nfunction applyFontFamily(family){const safe=(family||'Roboto, Arial, sans-serif').replace(/[<>;]/g,'');applyInlineStyle('fontFamily',safe,'data-notcan-font-family')}"
s = replace_once(s, old_font_js, new_font_js, 'font javascript')

old_command = "window.notcanCommand=function(c,v){if(c==='fontSizePx')applyFontSize(v);else if(c==='justifyFull')applyAlignment('full');else if(c==='justifyCenter')applyAlignment('center');else if(c==='justifyRight')applyAlignment('right');else if(c==='justifyLeft')applyAlignment('left');else withEditable(c,v)};"
new_command = "window.notcanCommand=function(c,v){if(c==='fontSizePt')applyFontSize(v);else if(c==='fontFamily')applyFontFamily(v);else if(c==='justifyFull')applyAlignment('full');else if(c==='justifyCenter')applyAlignment('center');else if(c==='justifyRight')applyAlignment('right');else if(c==='justifyLeft')applyAlignment('left');else withEditable(c,v)};"
s = replace_once(s, old_command, new_command, 'command router')

writer_path.write_text(s)

b = build_path.read_text()
b = replace_once(b, '        versionCode = 48\n        versionName = "0.8.25"\n', '        versionCode = 49\n        versionName = "0.8.26"\n', 'version bump')
build_path.write_text(b)

print('Applied Writer font-family support and bumped to v0.8.26')
