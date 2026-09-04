from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
writer_path = ROOT / 'app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt'
export_path = ROOT / 'app/src/main/java/com/notcan/app/ui/home/NoteFileExport.kt'
settings_path = ROOT / 'app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt'
build_path = ROOT / 'app/build.gradle.kts'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f'Missing target: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# WriterNoteEditor: local fonts, weight/calibre and spacing controls.
# ---------------------------------------------------------------------------
s = writer_path.read_text()
s = replace_once(
    s,
    'import android.webkit.JavascriptInterface\nimport android.webkit.WebView\nimport android.webkit.WebViewClient\n',
    'import android.webkit.JavascriptInterface\nimport android.webkit.WebResourceRequest\nimport android.webkit.WebResourceResponse\nimport android.webkit.WebView\nimport android.webkit.WebViewClient\nimport android.widget.Toast\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n',
    'writer imports'
)

s = replace_once(
    s,
    '    var fontSizeMenuOpen by remember(note.id) { mutableStateOf(false) }\n    var fontFamilyMenuOpen by remember(note.id) { mutableStateOf(false) }\n    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f\n',
    '''    var fontSizeMenuOpen by remember(note.id) { mutableStateOf(false) }
    var fontFamilyMenuOpen by remember(note.id) { mutableStateOf(false) }
    var fontWeightMenuOpen by remember(note.id) { mutableStateOf(false) }
    var spacingMenuOpen by remember(note.id) { mutableStateOf(false) }
    var importedFonts by remember(context) { mutableStateOf(LocalFontStore.list(context)) }
    var fontRevision by remember(note.id) { mutableStateOf(0) }
    val darkEditor = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { LocalFontStore.importFont(context, uri) }
                .onSuccess { entry ->
                    importedFonts = LocalFontStore.list(context)
                    fontRevision += 1
                    Toast.makeText(context, "Fuente ${entry.displayName} añadida", Toast.LENGTH_SHORT).show()
                    webView?.loadDataWithBaseURL(
                        "https://notcan.local/",
                        writerDocument(html, darkEditor, LocalFontStore.fontFaceCss(context)),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "No se pudo importar la fuente", Toast.LENGTH_LONG).show()
                }
        }
    }
''',
    'writer typography state'
)

s = s.replace('writerDocument(externalHtml, darkEditor)', 'writerDocument(externalHtml, darkEditor, LocalFontStore.fontFaceCss(context))')
s = s.replace('writerDocument(html, darkEditor)', 'writerDocument(html, darkEditor, LocalFontStore.fontFaceCss(context))')
s = s.replace('loadDataWithBaseURL(null,', 'loadDataWithBaseURL("https://notcan.local/",')

old_fonts = '''                Box {
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
new_fonts = '''                Box {
                    WriterStructureButton("Ab") { fontFamilyMenuOpen = true }
                    DropdownMenu(expanded = fontFamilyMenuOpen, onDismissRequest = { fontFamilyMenuOpen = false }) {
                        listOf(
                            "Sistema · Sans" to "sans-serif",
                            "Sistema · Serif" to "serif",
                            "Noto Sans" to "'Noto Sans', sans-serif",
                            "Noto Serif" to "'Noto Serif', serif",
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
                        importedFonts.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text("Local · ${entry.displayName}") },
                                onClick = {
                                    fontFamilyMenuOpen = false
                                    command("fontFamily", "'${entry.cssFamily}'")
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("＋ Añadir fuente local…") },
                            onClick = {
                                fontFamilyMenuOpen = false
                                fontImportLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
                Box {
                    WriterStructureButton("Gr") { fontWeightMenuOpen = true }
                    DropdownMenu(expanded = fontWeightMenuOpen, onDismissRequest = { fontWeightMenuOpen = false }) {
                        listOf(
                            "Ligera" to "300",
                            "Normal" to "400",
                            "Media" to "500",
                            "Seminegrita" to "600",
                            "Negrita" to "700",
                            "Extra negrita" to "800"
                        ).forEach { (label, weight) ->
                            DropdownMenuItem(
                                text = { Text("$label · $weight") },
                                onClick = { fontWeightMenuOpen = false; command("fontWeight", weight) }
                            )
                        }
                    }
                }
                Box {
                    WriterStructureButton("↕") { spacingMenuOpen = true }
                    DropdownMenu(expanded = spacingMenuOpen, onDismissRequest = { spacingMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Interlineado") }, onClick = {}, enabled = false)
                        listOf("1,0" to "1.0", "1,15" to "1.15", "1,5" to "1.5", "2,0" to "2.0").forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { spacingMenuOpen = false; command("lineHeight", value) }
                            )
                        }
                        DropdownMenuItem(text = { Text("Espacio después del párrafo") }, onClick = {}, enabled = false)
                        listOf(0, 6, 12, 18).forEach { pt ->
                            DropdownMenuItem(
                                text = { Text("$pt pt") },
                                onClick = { spacingMenuOpen = false; command("paragraphSpacing", pt.toString()) }
                            )
                        }
                        DropdownMenuItem(text = { Text("Espaciado entre letras") }, onClick = {}, enabled = false)
                        listOf("Normal" to "0", "Ligero" to "0.02", "Amplio" to "0.05").forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { spacingMenuOpen = false; command("letterSpacing", value) }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
'''
s = replace_once(s, old_fonts, new_fonts, 'writer typography toolbar')

s = replace_once(
    s,
    '                            webViewClient = WebViewClient()\n',
    '''                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    return LocalFontStore.intercept(context, request?.url)
                                        ?: super.shouldInterceptRequest(view, request)
                                }
                            }
''',
    'writer font web interception'
)

s = replace_once(
    s,
    'private fun writerDocument(initialBody: String, darkTheme: Boolean): String {\n',
    'private fun writerDocument(initialBody: String, darkTheme: Boolean, fontFaceCss: String): String {\n',
    'writer document signature'
)
s = replace_once(
    s,
    ':root{--notcan-text:$textColor;--notcan-selection:$selectionBg;--notcan-selection-text:$selectionText}\n',
    ':root{--notcan-text:$textColor;--notcan-selection:$selectionBg;--notcan-selection-text:$selectionText}\n$fontFaceCss\n',
    'writer font css injection'
)
s = replace_once(
    s,
    "function blockFor(node){let e=node&&node.nodeType===1?node:node&&node.parentElement;while(e&&e!==editor){if(/^(P|DIV|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE)$/.test(e.tagName))return e;e=e.parentElement}return null}\n",
    "function blockFor(node){let e=node&&node.nodeType===1?node:node&&node.parentElement;while(e&&e!==editor){if(/^(P|DIV|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE)$/.test(e.tagName))return e;e=e.parentElement}return null}\nfunction selectedBlocks(){if(!restore())return[];const s=window.getSelection();if(!s||!s.rangeCount)return[];const r=s.getRangeAt(0).cloneRange(),blocks=[];editor.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,li,blockquote').forEach(function(el){try{if(r.intersectsNode(el))blocks.push(el)}catch(e){}});if(!blocks.length){const b=blockFor(r.startContainer);if(b)blocks.push(b)}return Array.from(new Set(blocks))}\nfunction applyBlockMetric(prop,value){const blocks=selectedBlocks();if(!blocks.length)return;blocks.forEach(function(b){b.style[prop]=value});save();notify()}\n",
    'writer block metrics'
)
s = replace_once(
    s,
    "window.notcanCommand=function(c,v){if(c==='fontSizePt')applyFontSize(v);else if(c==='fontFamily')applyFontFamily(v);else if(c==='justifyFull')applyAlignment('full');else if(c==='justifyCenter')applyAlignment('center');else if(c==='justifyRight')applyAlignment('right');else if(c==='justifyLeft')applyAlignment('left');else withEditable(c,v)};",
    "window.notcanCommand=function(c,v){if(c==='fontSizePt')applyFontSize(v);else if(c==='fontFamily')applyFontFamily(v);else if(c==='fontWeight')applyInlineStyle('fontWeight',String(v||'400'),'data-notcan-font-weight');else if(c==='letterSpacing')applyInlineStyle('letterSpacing',String(v||'0')+'em','data-notcan-letter-spacing');else if(c==='lineHeight')applyBlockMetric('lineHeight',String(v||'1.15'));else if(c==='paragraphSpacing')applyBlockMetric('marginBottom',String(v||'0')+'pt');else if(c==='justifyFull')applyAlignment('full');else if(c==='justifyCenter')applyAlignment('center');else if(c==='justifyRight')applyAlignment('right');else if(c==='justifyLeft')applyAlignment('left');else withEditable(c,v)};",
    'writer command router'
)
writer_path.write_text(s)

# ---------------------------------------------------------------------------
# NoteFileExport: preserve local fonts, weight and spacing in PDF/DOCX.
# ---------------------------------------------------------------------------
e = export_path.read_text()
e = replace_once(e, 'import android.graphics.pdf.PdfDocument\n', 'import android.graphics.pdf.PdfDocument\nimport android.os.Build\n', 'export Build import')
e = replace_once(e, 'import android.text.style.ForegroundColorSpan\n', 'import android.text.style.ForegroundColorSpan\nimport android.text.style.MetricAffectingSpan\n', 'export span import')

e = replace_once(e, 'Format.DOCX -> writeDocx(file, title, html)\n            Format.PDF -> writePdf(file, title, html)', 'Format.DOCX -> writeDocx(context, file, title, html)\n            Format.PDF -> writePdf(context, file, title, html)', 'export share context')
e = replace_once(e, 'private fun writeDocx(file: File, title: String, html: String) {', 'private fun writeDocx(context: Context, file: File, title: String, html: String) {', 'writeDocx signature')
e = replace_once(e, 'append(paragraphXml(titleText, "Title", emptyList(), ParagraphAlignment.LEFT))', 'append(paragraphXml(context, titleText, "Title", emptyList(), ParagraphAlignment.LEFT, null, null))', 'docx title paragraph')
e = replace_once(e, 'append(paragraphXml(block.text, style, block.runs, block.alignment))', 'append(paragraphXml(context, block.text, style, block.runs, block.alignment, block.lineHeight, block.spacingAfterPt))', 'docx block paragraph')

e = replace_once(
    e,
    '''    private fun paragraphXml(
        text: String,
        style: String?,
        runs: List<RunStyle>,
        alignment: ParagraphAlignment
    ): String {
''',
    '''    private fun paragraphXml(
        context: Context,
        text: String,
        style: String?,
        runs: List<RunStyle>,
        alignment: ParagraphAlignment,
        lineHeight: Float?,
        spacingAfterPt: Float?
    ): String {
''',
    'paragraphXml signature'
)
e = replace_once(
    e,
    '''            when (alignment) {
                ParagraphAlignment.CENTER -> append("<w:jc w:val=\\"center\\"/>")
                ParagraphAlignment.RIGHT -> append("<w:jc w:val=\\"right\\"/>")
                ParagraphAlignment.JUSTIFY -> append("<w:jc w:val=\\"both\\"/>")
                ParagraphAlignment.LEFT -> Unit
            }
''',
    '''            when (alignment) {
                ParagraphAlignment.CENTER -> append("<w:jc w:val=\\"center\\"/>")
                ParagraphAlignment.RIGHT -> append("<w:jc w:val=\\"right\\"/>")
                ParagraphAlignment.JUSTIFY -> append("<w:jc w:val=\\"both\\"/>")
                ParagraphAlignment.LEFT -> Unit
            }
            if (lineHeight != null || spacingAfterPt != null) {
                append("<w:spacing")
                lineHeight?.let { append(" w:line=\\"${(it.coerceIn(0.8f, 3f) * 240f).roundToInt()}\\" w:lineRule=\\"auto\\"") }
                spacingAfterPt?.let { append(" w:after=\\"${(it.coerceIn(0f, 72f) * 20f).roundToInt()}\\"") }
                append("/>")
            }
''',
    'docx paragraph spacing'
)
e = replace_once(e, 'if (run.bold) append("<w:b/>")', 'if (run.bold || (run.fontWeight ?: 400) >= 600) append("<w:b/>")', 'docx font weight')
e = replace_once(e, 'val safe = xmlEscape(exportFontName(family))', 'val safe = xmlEscape(exportFontName(context, family))', 'docx local font name')
e = replace_once(
    e,
    '''                run.fontFamily?.takeIf { it.isNotBlank() }?.let { family ->
                    val safe = xmlEscape(exportFontName(context, family))
                    append("<w:rFonts w:ascii=\\"$safe\\" w:hAnsi=\\"$safe\\" w:cs=\\"$safe\\"/>")
                }
''',
    '''                run.fontFamily?.takeIf { it.isNotBlank() }?.let { family ->
                    val safe = xmlEscape(exportFontName(context, family))
                    append("<w:rFonts w:ascii=\\"$safe\\" w:hAnsi=\\"$safe\\" w:cs=\\"$safe\\"/>")
                }
                run.letterSpacingEm?.let { em ->
                    val pt = run.fontSizePt ?: 11f
                    append("<w:spacing w:val=\\"${(em.coerceIn(-0.2f, 0.5f) * pt * 20f).roundToInt()}\\"/>")
                }
''',
    'docx letter spacing'
)

e = replace_once(e, 'private fun writePdf(file: File, title: String, html: String) {', 'private fun writePdf(context: Context, file: File, title: String, html: String) {', 'writePdf signature')
e = replace_once(
    e,
    '''        fun drawFlowingText(
            source: CharSequence,
            paint: TextPaint,
            alignment: ParagraphAlignment,
            extraBottom: Float
        ) {
''',
    '''        fun drawFlowingText(
            source: CharSequence,
            paint: TextPaint,
            alignment: ParagraphAlignment,
            extraBottom: Float,
            lineHeight: Float = 1.08f
        ) {
''',
    'pdf flow signature'
)
e = e.replace('staticLayout(remaining, paint, contentWidth, alignment)', 'staticLayout(remaining, paint, contentWidth, alignment, lineHeight)')
e = e.replace('staticLayout(chunk, paint, contentWidth, alignment)', 'staticLayout(chunk, paint, contentWidth, alignment, lineHeight)')
e = replace_once(e, 'val richText = styledText(block)\n            drawFlowingText(richText, paint, block.alignment, if (block.tag.startsWith("h")) 10f else 7f)', 'val richText = styledText(context, block)\n            drawFlowingText(richText, paint, block.alignment, block.spacingAfterPt ?: if (block.tag.startsWith("h")) 10f else 7f, block.lineHeight ?: 1.08f)', 'pdf block styling')
e = replace_once(
    e,
    '''    private fun staticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: ParagraphAlignment
    ): StaticLayout {
''',
    '''    private fun staticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: ParagraphAlignment,
        lineHeight: Float
    ): StaticLayout {
''',
    'staticLayout signature'
)
e = replace_once(e, '.setLineSpacing(2.5f, 1.08f)', '.setLineSpacing(0f, lineHeight.coerceIn(0.8f, 3f))', 'pdf line height')
e = replace_once(e, 'private fun styledText(block: HtmlBlock): CharSequence {', 'private fun styledText(context: Context, block: HtmlBlock): CharSequence {', 'styledText signature')

old_style_block = '''            when {
                run.bold && run.italic -> text.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, flags)
                run.bold -> text.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
                run.italic -> text.setSpan(StyleSpan(Typeface.ITALIC), start, end, flags)
            }
'''
new_style_block = '''            val requestedWeight = (run.fontWeight ?: if (run.bold) 700 else 400).coerceIn(100, 900)
            val typeface = resolvePdfTypeface(context, run.fontFamily, requestedWeight, run.italic)
            text.setSpan(FontRunSpan(typeface, run.letterSpacingEm), start, end, flags)
'''
e = replace_once(e, old_style_block, new_style_block, 'pdf typeface run')
e = e.replace('            run.fontFamily?.let { text.setSpan(TypefaceSpan(pdfFontFamily(it)), start, end, flags) }\n', '')

e = replace_once(
    e,
    '''    private data class HtmlBlock(
        val tag: String,
        val text: String,
        val runs: List<RunStyle>,
        val alignment: ParagraphAlignment
    )
''',
    '''    private data class HtmlBlock(
        val tag: String,
        val text: String,
        val runs: List<RunStyle>,
        val alignment: ParagraphAlignment,
        val lineHeight: Float? = null,
        val spacingAfterPt: Float? = null
    )
''',
    'HtmlBlock metrics'
)
e = replace_once(
    e,
    '''        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null
    )
''',
    '''        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null,
        val fontWeight: Int? = null,
        val letterSpacingEm: Float? = null
    )
''',
    'RunStyle typography'
)
e = replace_once(
    e,
    '''        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null
    )

    private data class ParsedInline''',
    '''        val background: Int? = null,
        val fontSizePt: Float? = null,
        val fontFamily: String? = null,
        val fontWeight: Int? = null,
        val letterSpacingEm: Float? = null
    )

    private data class ParsedInline''',
    'InlineStyle typography'
)

e = replace_once(
    e,
    '''                alignment = parseAlignment(attrs)
            )
''',
    '''                alignment = parseAlignment(attrs),
                lineHeight = parseLineHeight(attrs),
                spacingAfterPt = parseSpacingAfterPt(attrs)
            )
''',
    'html block spacing parse'
)

e = replace_once(
    e,
    '''                    background = style.background,
                    fontSizePt = style.fontSizePt,
                    fontFamily = style.fontFamily
                )
''',
    '''                    background = style.background,
                    fontSizePt = style.fontSizePt,
                    fontFamily = style.fontFamily,
                    fontWeight = style.fontWeight,
                    letterSpacingEm = style.letterSpacingEm
                )
''',
    'run style parse'
)

e = replace_once(
    e,
    'runs += RunStyle(start, text.length, style.bold, style.italic, style.underline, style.strike, style.foreground, style.background, style.fontSizePt, style.fontFamily)',
    'runs += RunStyle(start, text.length, style.bold, style.italic, style.underline, style.strike, style.foreground, style.background, style.fontSizePt, style.fontFamily, style.fontWeight, style.letterSpacingEm)',
    'br run style'
)

e = replace_once(
    e,
    '''        css["font-weight"]?.lowercase(Locale.ROOT)?.let { weight ->
            if (weight == "bold" || weight.toIntOrNull()?.let { it >= 600 } == true) result = result.copy(bold = true)
        }
''',
    '''        css["font-weight"]?.lowercase(Locale.ROOT)?.let { weight ->
            val numeric = when (weight) {
                "normal" -> 400
                "bold" -> 700
                "lighter" -> 300
                "bolder" -> 700
                else -> weight.toIntOrNull()
            }?.coerceIn(100, 900)
            if (numeric != null) result = result.copy(fontWeight = numeric, bold = result.bold || numeric >= 600)
        }
        css["letter-spacing"]?.let { parseLetterSpacingEm(it)?.let { spacing -> result = result.copy(letterSpacingEm = spacing) } }
''',
    'css weight and letters'
)

insert_after_alignment = '''    private fun parseAlignment(attrs: String): ParagraphAlignment {
        val style = attributeValue(attrs, "style").orEmpty()
        val value = cssProperties(style)["text-align"]?.lowercase(Locale.ROOT)
        return when (value) {
            "center" -> ParagraphAlignment.CENTER
            "right", "end" -> ParagraphAlignment.RIGHT
            "justify" -> ParagraphAlignment.JUSTIFY
            else -> ParagraphAlignment.LEFT
        }
    }
'''
metrics_helpers = insert_after_alignment + '''
    private fun parseLineHeight(attrs: String): Float? {
        val raw = cssProperties(attributeValue(attrs, "style").orEmpty())["line-height"]?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when {
            raw.endsWith("%") -> raw.removeSuffix("%").toFloatOrNull()?.div(100f)
            raw.endsWith("pt") || raw.endsWith("px") -> null
            else -> raw.toFloatOrNull()
        }?.coerceIn(0.8f, 3f)
    }

    private fun parseSpacingAfterPt(attrs: String): Float? {
        val raw = cssProperties(attributeValue(attrs, "style").orEmpty())["margin-bottom"] ?: return null
        return parseCssLengthPt(raw)?.coerceIn(0f, 72f)
    }
'''
e = replace_once(e, insert_after_alignment, metrics_helpers, 'paragraph metric helpers')

insert_after_font_size = '''    private fun parseFontSizePt(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("pt") -> number
            raw.endsWith("px") -> number * 0.75f
            raw.endsWith("em") -> number * 11f
            raw.endsWith("rem") -> number * 11f
            else -> number
        }.coerceIn(6f, 72f)
    }
'''
font_helpers = insert_after_font_size + '''
    private fun parseCssLengthPt(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("px") -> number * 0.75f
            raw.endsWith("em") || raw.endsWith("rem") -> number * 11f
            else -> number
        }
    }

    private fun parseLetterSpacingEm(value: String): Float? {
        val raw = value.trim().lowercase(Locale.ROOT)
        if (raw == "normal") return 0f
        val number = Regex("[-+]?[0-9]*\\.?[0-9]+").find(raw)?.value?.toFloatOrNull() ?: return null
        return when {
            raw.endsWith("em") -> number
            raw.endsWith("rem") -> number
            raw.endsWith("pt") -> number / 11f
            raw.endsWith("px") -> (number * 0.75f) / 11f
            else -> number
        }.coerceIn(-0.2f, 0.5f)
    }
'''
e = replace_once(e, insert_after_font_size, font_helpers, 'font metric helpers')

e = replace_once(
    e,
    '''    private fun exportFontName(value: String): String = when {
        value.contains("mono", true) || value.contains("courier", true) -> "Courier New"
        value.contains("georgia", true) -> "Georgia"
        value.contains("times", true) || value.equals("serif", true) -> "Times New Roman"
        value.contains("arial", true) -> "Arial"
        value.contains("roboto", true) || value.contains("sans", true) -> "Arial"
        else -> value
    }

    private fun pdfFontFamily(value: String): String = when {
        value.contains("mono", true) || value.contains("courier", true) -> "monospace"
        value.contains("georgia", true) || value.contains("times", true) || value.equals("serif", true) -> "serif"
        value.contains("cursive", true) -> "cursive"
        else -> "sans-serif"
    }
''',
    '''    private fun exportFontName(context: Context, value: String): String = LocalFontStore.exportName(context, value) ?: when {
        value.contains("mono", true) || value.contains("courier", true) -> "Courier New"
        value.contains("georgia", true) -> "Georgia"
        value.contains("times", true) || value.equals("serif", true) -> "Times New Roman"
        value.contains("arial", true) -> "Arial"
        value.contains("noto serif", true) -> "Noto Serif"
        value.contains("noto sans", true) -> "Noto Sans"
        value.contains("roboto", true) || value.contains("sans", true) -> "Roboto"
        else -> value
    }

    private fun pdfSystemFamily(value: String?): String = when {
        value?.contains("mono", true) == true || value?.contains("courier", true) == true -> "monospace"
        value?.contains("georgia", true) == true || value?.contains("times", true) == true || value.equals("serif", true) -> "serif"
        value?.contains("cursive", true) == true -> "cursive"
        else -> "sans-serif"
    }

    private fun resolvePdfTypeface(context: Context, family: String?, weight: Int, italic: Boolean): Typeface {
        val base = LocalFontStore.resolveTypeface(context, family) ?: Typeface.create(pdfSystemFamily(family), Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, weight.coerceIn(100, 900), italic)
        } else {
            val style = when {
                weight >= 600 && italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private class FontRunSpan(
        private val typeface: Typeface,
        private val letterSpacingEm: Float?
    ) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
        override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
        private fun apply(paint: TextPaint) {
            paint.typeface = typeface
            letterSpacingEm?.let { paint.letterSpacing = it.coerceIn(-0.2f, 0.5f) }
        }
    }
''',
    'export local font resolution'
)
export_path.write_text(e)

# ---------------------------------------------------------------------------
# Settings: manage imported local fonts and reclaim space.
# ---------------------------------------------------------------------------
t = settings_path.read_text()
t = replace_once(
    t,
    'import android.content.pm.PackageManager\n',
    'import android.content.pm.PackageManager\nimport android.widget.Toast\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n',
    'settings launcher imports'
)
t = replace_once(t, 'import com.notcan.app.ui.theme.NotCanBlue\n', 'import com.notcan.app.ui.home.LocalFontStore\nimport com.notcan.app.ui.theme.NotCanBlue\n', 'settings local font import')
t = replace_once(
    t,
    '    var refreshTick by remember { mutableIntStateOf(0) }\n',
    '''    var refreshTick by remember { mutableIntStateOf(0) }
    var localFonts by remember(context) { mutableStateOf(LocalFontStore.list(context)) }
    val localFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { LocalFontStore.importFont(context, uri) }
                .onSuccess { entry ->
                    localFonts = LocalFontStore.list(context)
                    Toast.makeText(context, "Fuente ${entry.displayName} añadida", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error -> Toast.makeText(context, error.message ?: "No se pudo importar la fuente", Toast.LENGTH_LONG).show() }
        }
    }
''',
    'settings local font state'
)

calendar_card_end = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
'''
font_card = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fuentes de apuntes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text(
                    "Las fuentes importadas quedan disponibles sin conexión y solo ocupan su propio archivo TTF/OTF.",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { localFontLauncher.launch(arrayOf("*/*")) }) { Text("Añadir fuente local") }
                    if (localFonts.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            LocalFontStore.removeAll(context)
                            localFonts = emptyList()
                        }) { Text("Quitar todas") }
                    }
                }
                if (localFonts.isEmpty()) {
                    Text("Sin fuentes importadas · las familias base siguen disponibles.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${localFonts.size} fuente(s) local(es)", color = NotCanBlue, style = MaterialTheme.typography.bodySmall)
                    localFonts.forEach { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.displayName, color = NotCanOffWhite, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = {
                                LocalFontStore.remove(context, entry.id)
                                localFonts = LocalFontStore.list(context)
                            }) { Text("Quitar") }
                        }
                    }
                }
            }
        }

'''
t = replace_once(t, calendar_card_end, font_card + calendar_card_end, 'settings font card insertion')
settings_path.write_text(t)

# Version bump.
b = build_path.read_text()
b = replace_once(b, '        versionCode = 49\n        versionName = "0.8.26"\n', '        versionCode = 50\n        versionName = "0.8.27"\n', 'version 0.8.27')
build_path.write_text(b)

print('Applied v0.8.27 local fonts, calibre and spacing')
