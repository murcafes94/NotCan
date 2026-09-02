from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'web/src/App.tsx'
MAIN = ROOT / 'web/src/main.tsx'
text = APP.read_text()


def replace_once(old: str, new: str):
    global text
    if old not in text:
        raise SystemExit(f'anchor not found: {old[:120]!r}')
    text = text.replace(old, new, 1)

# Imports
replace_once(
    "import AcademicWorkspace, { type AcademicLevel } from './AcademicWorkspace'\n",
    "import AcademicWorkspace, { type AcademicLevel } from './AcademicWorkspace'\nimport TuNotChat from './TuNotChat'\nimport UnifiedSettings from './UnifiedSettings'\n",
)

# Sanitizer: preserve NotCan-owned semantic formatting only.
replace_once(
    "const allowed = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'H1', 'H2', 'UL', 'OL', 'LI', 'A', 'BLOCKQUOTE'])",
    "const allowed = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'H1', 'H2', 'UL', 'OL', 'LI', 'A', 'BLOCKQUOTE', 'MARK', 'SPAN'])",
)
replace_once(
    """        for (const attribute of Array.from(element.attributes)) {\n          const keepHref = element.tagName === 'A' && attribute.name.toLowerCase() === 'href'\n          if (!keepHref) element.removeAttribute(attribute.name)\n        }\n""",
    """        for (const attribute of Array.from(element.attributes)) {\n          const name = attribute.name.toLowerCase()\n          const keepHref = element.tagName === 'A' && name === 'href'\n          const keepHighlight = element.tagName === 'MARK' && name === 'data-notcan-highlight'\n          const keepSize = element.tagName === 'SPAN' && name === 'data-notcan-size'\n          const keepAlign = ['P', 'DIV', 'H1', 'H2', 'LI', 'BLOCKQUOTE'].includes(element.tagName) && name === 'data-notcan-align'\n          if (!keepHref && !keepHighlight && !keepSize && !keepAlign) element.removeAttribute(attribute.name)\n        }\n""",
)

# Editor state + exact return target.
replace_once(
    """  const [formatHint, setFormatHint] = useState('')\n  const editorRef = useRef<HTMLDivElement>(null)\n  const loadedEditorIdRef = useRef<string | null>(null)\n""",
    """  const [formatHint, setFormatHint] = useState('')\n  const [editorEditing, setEditorEditing] = useState(false)\n  const [editorRecoveryMessage, setEditorRecoveryMessage] = useState('')\n  const editorRef = useRef<HTMLDivElement>(null)\n  const loadedEditorIdRef = useRef<string | null>(null)\n  const savedSelectionRef = useRef<Range | null>(null)\n  const editorReturnRef = useRef<{ page: Page; level: AcademicLevel; subjectId: string | null; classId: string | null } | null>(null)\n""",
)

# Replace opening logic with recovery and return-state preservation.
pattern = re.compile(r"  function openNewNote\(classId\?: string\) \{.*?\n  useEffect\(\(\) => \{\n    if \(!editorOpen \|\| !editorNoteId \|\| !editorRef\.current\) return", re.S)
match = pattern.search(text)
if not match:
    raise SystemExit('open note block not found')
replacement = r'''  function rememberEditorReturn() {
    editorReturnRef.current = { page, level: academicLevel, subjectId: selectedSubjectId, classId: selectedClassId }
  }

  function closeEditor() {
    setEditorOpen(false)
    setEditorEditing(false)
    setEditorRecoveryMessage('')
    const target = editorReturnRef.current
    if (!target) return
    setPage(target.page)
    setAcademicLevel(target.level)
    setSelectedSubjectId(target.subjectId)
    setSelectedClassId(target.classId)
    editorReturnRef.current = null
  }

  function draftKey(noteId: string) { return `notcan-web-draft-${noteId}` }

  function openNewNote(classId?: string) {
    const targetClassId = classId ?? selectedClassId ?? activeClasses[0]?.id ?? null
    if (!targetClassId) {
      openSubjects()
      return
    }
    rememberEditorReturn()
    const now = Date.now()
    const id = crypto.randomUUID()
    loadedEditorIdRef.current = null
    setEditorNoteId(id)
    setEditorClassId(targetClassId)
    setEditorTitle('')
    setEditorBody('')
    setEditorCreatedAt(now)
    setEditorRevision(1)
    setSaveState('idle')
    setEditorEditing(true)
    setEditorRecoveryMessage('')
    setEditorOpen(true)
  }

  function openExistingNote(note: NotePageRecord) {
    rememberEditorReturn()
    loadedEditorIdRef.current = null
    let recovered: { title?: string; body?: string; classId?: string; updatedAt?: number } | null = null
    try { recovered = JSON.parse(localStorage.getItem(draftKey(note.id)) || 'null') } catch { recovered = null }
    const useDraft = Boolean(recovered?.updatedAt && recovered.updatedAt > note.updatedAtEpochMs)
    setEditorNoteId(note.id)
    setEditorClassId(useDraft && recovered?.classId ? recovered.classId : note.classSessionId)
    setEditorTitle(useDraft && typeof recovered?.title === 'string' ? recovered.title : note.title)
    setEditorBody(normalizeBodyForEditor(useDraft && typeof recovered?.body === 'string' ? recovered.body : note.body))
    setEditorCreatedAt(note.createdAtEpochMs)
    setEditorRevision(note.revision)
    setSaveState('saved')
    setEditorEditing(false)
    setEditorRecoveryMessage(useDraft ? 'Se recuperó un borrador local más reciente.' : '')
    setEditorOpen(true)
  }

  useEffect(() => {
    if (!editorOpen || !editorNoteId) return
    const timer = window.setTimeout(() => {
      localStorage.setItem(draftKey(editorNoteId), JSON.stringify({
        title: editorTitle,
        body: editorBody,
        classId: editorClassId,
        updatedAt: Date.now(),
      }))
    }, 220)
    return () => window.clearTimeout(timer)
  }, [editorOpen, editorNoteId, editorTitle, editorBody, editorClassId])

  useEffect(() => {
    if (!editorOpen || !editorNoteId || !editorRef.current) return'''
text = text[:match.start()] + replacement + text[match.end():]

# Remove recovery draft only after successful durable local save.
replace_once(
    """        await db.notePages.put(note)\n        await queueUpsert('note_pages', note.id, note)\n        setEditorRevision(revision)\n""",
    """        await db.notePages.put(note)\n        await queueUpsert('note_pages', note.id, note)\n        localStorage.removeItem(draftKey(note.id))\n        setEditorRecoveryMessage('')\n        setEditorRevision(revision)\n""",
)

# Selection utilities and advanced semantic formatting.
anchor = """  function createLinkForSelection() {\n"""
advanced = r'''  function rememberSelection() {
    const editor = editorRef.current
    const selection = window.getSelection()
    if (!editor || !selection || selection.rangeCount === 0 || !editor.contains(selection.anchorNode)) return
    savedSelectionRef.current = selection.getRangeAt(0).cloneRange()
  }

  function restoreSelection() {
    const range = savedSelectionRef.current
    if (!range) return
    const selection = window.getSelection()
    selection?.removeAllRanges()
    selection?.addRange(range)
  }

  function selectedRange(): Range | null {
    const editor = editorRef.current
    const selection = window.getSelection()
    if (!editor || !selection || selection.rangeCount === 0 || selection.isCollapsed || !editor.contains(selection.anchorNode)) {
      setFormatHint('Selecciona primero el texto que quieres formatear.')
      window.setTimeout(() => setFormatHint(''), 1800)
      return null
    }
    return selection.getRangeAt(0)
  }

  function wrapSelectedText(tag: 'mark' | 'span', attribute: string, value: string) {
    const editor = editorRef.current
    const range = selectedRange()
    if (!editor || !range) return
    const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT)
    const textNodes: Text[] = []
    let current = walker.nextNode()
    while (current) {
      if ((current.textContent || '').length && range.intersectsNode(current)) textNodes.push(current as Text)
      current = walker.nextNode()
    }
    for (const node of textNodes.reverse()) {
      let start = node === range.startContainer ? range.startOffset : 0
      let end = node === range.endContainer ? range.endOffset : node.data.length
      start = Math.max(0, Math.min(start, node.data.length))
      end = Math.max(start, Math.min(end, node.data.length))
      if (end <= start) continue
      const selected = node.splitText(start)
      selected.splitText(end - start)
      const wrapper = document.createElement(tag)
      wrapper.setAttribute(attribute, value)
      selected.parentNode?.insertBefore(wrapper, selected)
      wrapper.appendChild(selected)
    }
    setEditorBody(editor.innerHTML)
  }

  function applyHighlight(color: 'yellow' | 'green' | 'blue' | 'pink') {
    wrapSelectedText('mark', 'data-notcan-highlight', color)
  }

  function removeHighlight() {
    const editor = editorRef.current
    const range = selectedRange()
    if (!editor || !range) return
    const marks = Array.from(editor.querySelectorAll('mark[data-notcan-highlight]')).filter((mark) => range.intersectsNode(mark))
    marks.forEach((mark) => mark.replaceWith(...Array.from(mark.childNodes)))
    setEditorBody(editor.innerHTML)
  }

  function applyFontSize(size: string) {
    wrapSelectedText('span', 'data-notcan-size', size)
  }

  function applyBlockAlignment(alignment: 'left' | 'center' | 'right' | 'justify') {
    const editor = editorRef.current
    const range = selectedRange()
    if (!editor || !range) return
    const blocks = Array.from(editor.querySelectorAll('p,div,h1,h2,li,blockquote')).filter((node) => range.intersectsNode(node)) as HTMLElement[]
    if (blocks.length === 0) {
      let node = range.commonAncestorContainer.nodeType === Node.ELEMENT_NODE ? range.commonAncestorContainer as HTMLElement : range.commonAncestorContainer.parentElement
      const block = node?.closest('p,div,h1,h2,li,blockquote') as HTMLElement | null
      if (block && editor.contains(block)) blocks.push(block)
    }
    blocks.forEach((block) => block.setAttribute('data-notcan-align', alignment))
    setEditorBody(editor.innerHTML)
  }

  function createLinkForSelection() {
'''
replace_once(anchor, advanced)

# Chat request wrapper.
insert_after = """  async function runAi(mode: AiMode = aiMode, promptOverride?: string) {\n"""
pos = text.find(insert_after)
if pos == -1:
    raise SystemExit('runAi anchor missing')
# Insert function before runAi.
chat_fn = r'''  async function askTuNot(prompt: string, mode: AiMode): Promise<{ answer: string; model?: string }> {
    if (!session) throw new Error('Inicia sesión para usar TuNot en la web.')
    const response = await askNotCanAi({ prompt, mode, context: aiUseNotes ? noteContext() : [] })
    return { answer: response.answer, model: response.model ?? undefined }
  }

'''
text = text[:pos] + chat_fn + text[pos:]

# Replace renderAi function with chat UI.
pattern = re.compile(r"  function renderAi\(\) \{.*?\n  \}\n\n  function renderPage\(\) \{", re.S)
match = pattern.search(text)
if not match:
    raise SystemExit('renderAi block missing')
new_ai = r'''  function renderAi() {
    return <TuNotChat
      connected={Boolean(session)}
      contextCount={activeNotes.length}
      useContext={aiUseNotes}
      onUseContextChange={setAiUseNotes}
      onAsk={askTuNot}
      onOpenAccount={() => setAccountOpen(true)}
    />
  }

  function renderPage() {'''
text = text[:match.start()] + new_ai + text[match.end():]

# Settings is now a dedicated unified page before the generic page shell.
replace_once(
    """    if (page === 'home') return renderHome()\n    if (page === 'ai') return renderAi()\n\n""",
    """    if (page === 'home') return renderHome()\n    if (page === 'ai') return renderAi()\n    if (page === 'settings') return <main className=\"main-area feature-page\"><UnifiedSettings\n      theme={theme}\n      onThemeChange={setTheme}\n      session={session}\n      pending={pending}\n      syncText={syncLabel.text}\n      syncKind={syncLabel.kind}\n      onSync={session ? handleSync : () => setAccountOpen(true)}\n      onAccount={() => setAccountOpen(true)}\n      cycles={cycles}\n      onCyclesChanged={refresh}\n    /></main>\n\n""",
)

# Naming: TuNot everywhere visible in active navigation/home.
text = text.replace("{ page: 'ai', icon: '✦', label: 'NotCan AI' }", "{ page: 'ai', icon: '✦', label: 'TuNot' }")
text = text.replace('Preguntar a NotCan AI', 'Preguntar a TuNot')
text = text.replace('Generar con IA', 'Generar con TuNot')

# One settings destination: remove separate sync/account sidebar rows; settings carries sync dot.
old_sidebar = """      <div className=\"sidebar-bottom\">\n        <button className={`nav-item ${page === 'sync' ? 'active' : ''}`} onClick={() => navigate('sync')}><span>↻</span>Sincronización <i className={`dot ${syncLabel.kind}`} /></button>\n        <button className={`nav-item ${page === 'settings' ? 'active' : ''}`} onClick={() => navigate('settings')}><span>⚙</span>Ajustes</button>\n        <button className={`nav-item ${page === 'account' ? 'active' : ''}`} onClick={() => navigate('account')}><span>○</span>Cuenta</button>\n      </div>\n"""
new_sidebar = """      <div className=\"sidebar-bottom\">\n        <button className={`nav-item ${page === 'settings' ? 'active' : ''}`} onClick={() => navigate('settings')}><span>⚙</span>Ajustes <i className={`dot ${session ? syncLabel.kind : 'muted'}`} /></button>\n      </div>\n"""
replace_once(old_sidebar, new_sidebar)
text = text.replace("<button className=\"avatar-button\" onClick={() => navigate('account')}", "<button className=\"avatar-button\" onClick={() => navigate('settings')}")

# Editor toolbar: read/edit mode, exact back and recovery.
replace_once(
    """          <button className=\"back-editor\" onClick={() => setEditorOpen(false)}>← Volver</button>\n          <div className=\"editor-save-state\"><span className={`dot ${saveState === 'saved' ? 'good' : saveState === 'saving' ? 'warn' : 'muted'}`} />{saveText}</div>\n          <div className=\"editor-toolbar-actions\"><button className=\"editor-ai-button\" onClick={askAiAboutCurrentNote}>✦ IA</button><button className=\"editor-delete-button\" onClick={() => void deleteCurrentNote()}>⌫ Eliminar</button></div>\n""",
    """          <button className=\"back-editor\" onClick={closeEditor}>← Volver</button>\n          <div className=\"editor-save-state\"><span className={`dot ${saveState === 'saved' ? 'good' : saveState === 'saving' ? 'warn' : 'muted'}`} />{saveText}</div>\n          <div className=\"editor-toolbar-actions\"><div className=\"editor-mode-toggle\"><button className={!editorEditing ? 'active' : ''} onClick={() => setEditorEditing(false)}>Lectura</button><button className={editorEditing ? 'active' : ''} onClick={() => setEditorEditing(true)}>Editar</button></div><button className=\"editor-ai-button\" onClick={askAiAboutCurrentNote}>✦ TuNot</button><button className=\"editor-delete-button\" onClick={() => void deleteCurrentNote()}>⌫ Eliminar</button></div>\n""",
)
text = text.replace("<select value={editorClassId ?? ''} onChange={(event) => setEditorClassId(event.target.value)}>", "<select disabled={!editorEditing} value={editorClassId ?? ''} onChange={(event) => setEditorClassId(event.target.value)}>")
text = text.replace("<input className=\"editor-title-input\" value={editorTitle}", "<input className=\"editor-title-input\" readOnly={!editorEditing} value={editorTitle}")

# Replace formatting toolbar with selection highlight + full edit toolbar.
pattern = re.compile(r"          <div className=\"format-bar\" role=\"toolbar\" aria-label=\"Formato del apunte\">.*?          \{formatHint && <div className=\"format-hint\">\{formatHint\}</div>\}", re.S)
match = pattern.search(text)
if not match:
    raise SystemExit('format toolbar block missing')
new_toolbar = r'''          {editorRecoveryMessage && <div className="editor-recovery-note">↻ {editorRecoveryMessage}</div>}
          <div className="editor-selection-bar" role="toolbar" aria-label="Resaltado por selección">
            <strong>Resaltar</strong>
            <button onMouseDown={preserveSelection} onClick={() => applyHighlight('yellow')} title="Amarillo"><span className="highlight-dot highlight-yellow" /></button>
            <button onMouseDown={preserveSelection} onClick={() => applyHighlight('green')} title="Verde"><span className="highlight-dot highlight-green" /></button>
            <button onMouseDown={preserveSelection} onClick={() => applyHighlight('blue')} title="Azul"><span className="highlight-dot highlight-blue" /></button>
            <button onMouseDown={preserveSelection} onClick={() => applyHighlight('pink')} title="Rosado"><span className="highlight-dot highlight-pink" /></button>
            <button onMouseDown={preserveSelection} onClick={removeHighlight}>Quitar resaltado</button>
          </div>
          {editorEditing && <div className="format-bar phase3" role="toolbar" aria-label="Formato del apunte">
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('bold')} title="Negrita"><strong>B</strong></button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('italic')} title="Cursiva"><em>I</em></button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('underline')} title="Subrayado"><u>U</u></button>
            <span className="format-divider" />
            <select defaultValue="16" onMouseDown={rememberSelection} onChange={(event) => { restoreSelection(); applyFontSize(event.target.value) }} aria-label="Tamaño de fuente">
              {[12,14,16,18,20,24,28,32].map((size) => <option key={size} value={size}>{size}px</option>)}
            </select>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('formatBlock', 'H1')}>T1</button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('formatBlock', 'H2')}>T2</button>
            <span className="format-divider" />
            <button onMouseDown={preserveSelection} onClick={() => applyBlockAlignment('left')} title="Izquierda">≡←</button>
            <button onMouseDown={preserveSelection} onClick={() => applyBlockAlignment('center')} title="Centrar">≡</button>
            <button onMouseDown={preserveSelection} onClick={() => applyBlockAlignment('right')} title="Derecha">→≡</button>
            <button onMouseDown={preserveSelection} onClick={() => applyBlockAlignment('justify')} title="Justificar">☰</button>
            <span className="format-divider" />
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('insertUnorderedList')}>• Lista</button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('insertOrderedList')}>1. Lista</button>
            <button onMouseDown={preserveSelection} onClick={createLinkForSelection}>↗ Enlace</button>
            <button onClick={askAiAboutCurrentNote}>✦ TuNot</button>
          </div>}
          {formatHint && <div className="format-hint">{formatHint}</div>}'''
text = text[:match.start()] + new_toolbar + text[match.end():]

# Editor body respects read/edit mode but selection highlighting remains available.
text = text.replace('className="editor-body-input rich-editor"\n            contentEditable', 'className={`editor-body-input rich-editor ${editorEditing ? \'edit-mode\' : \'read-mode\'}`}\n            contentEditable={editorEditing}')
text = text.replace('onInput={(event) => setEditorBody(event.currentTarget.innerHTML)}', 'onInput={(event) => { if (editorEditing) setEditorBody(event.currentTarget.innerHTML) }}')

# Ask current note opens chat with prompt and keeps contextual origin restorable.
text = text.replace("    setEditorOpen(false)\n    setPage('ai')\n  }\n\n  function addMapNode", "    setEditorOpen(false)\n    setPage('ai')\n  }\n\n  function addMapNode", 1)

APP.write_text(text)

main = MAIN.read_text()
if "import './web-phase3.css'" not in main:
    main = main.replace("import './academic-workspace.css'", "import './academic-workspace.css'\nimport './web-phase3.css'")
MAIN.write_text(main)
