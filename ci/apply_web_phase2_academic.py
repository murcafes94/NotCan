from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str):
    (ROOT / path).write_text(text)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"anchor not found: {label}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, repl: str, label: str) -> str:
    updated, count = re.subn(pattern, repl, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"regex anchor not found or ambiguous: {label} ({count})")
    return updated


app_path = "web/src/App.tsx"
app = read(app_path)

app = replace_once(
    app,
    "import CycleManagementPanel from './CycleManagementPanel'\n",
    "import CycleManagementPanel from './CycleManagementPanel'\nimport AcademicWorkspace, { type AcademicLevel } from './AcademicWorkspace'\n",
    "academic import",
)

app = replace_once(
    app,
    "  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)\n  const [theme, setTheme] = useState<'dark' | 'light'>(() => (localStorage.getItem('notcan-theme') === 'light' ? 'light' : 'dark'))\n",
    "  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)\n  const [selectedClassId, setSelectedClassId] = useState<string | null>(null)\n  const [academicLevel, setAcademicLevel] = useState<AcademicLevel>('subjects')\n  const [theme, setTheme] = useState<'dark' | 'light'>(() => (localStorage.getItem('notcan-theme') === 'light' ? 'light' : 'dark'))\n",
    "academic state",
)

app = app.replace("    if (!editorClassId && classRows[0]) setEditorClassId(classRows[0].id)\n", "", 1)

app = regex_once(
    app,
    r"  const recentNotes = notes\.slice\(0, 4\).*?\n  const gradeStats = useMemo",
    """  const activeCycle = cycles.find((cycle) => cycle.isActive) ?? cycles[0] ?? null
  const activeSubjects = useMemo(
    () => activeCycle ? subjects.filter((subject) => subject.cycleId === activeCycle.id) : [],
    [subjects, activeCycle?.id],
  )
  const activeSubjectIds = useMemo(() => new Set(activeSubjects.map((subject) => subject.id)), [activeSubjects])
  const activeClasses = useMemo(
    () => classes.filter((classSession) => activeSubjectIds.has(classSession.subjectId)),
    [classes, activeSubjectIds],
  )
  const activeClassIds = useMemo(() => new Set(activeClasses.map((classSession) => classSession.id)), [activeClasses])
  const activeNotes = useMemo(
    () => notes.filter((note) => activeClassIds.has(note.classSessionId)),
    [notes, activeClassIds],
  )

  const recentNotes = activeNotes.slice(0, 4)
  const latestNote = activeNotes[0] ?? null
  const latestClass = latestNote
    ? activeClasses.find((item) => item.id === latestNote.classSessionId) ?? activeClasses[0] ?? null
    : activeClasses[0] ?? null
  const featuredSubject = latestClass
    ? activeSubjects.find((item) => item.id === latestClass.subjectId) ?? activeSubjects[0] ?? null
    : activeSubjects[0] ?? null

  const subjectCards = useMemo(() => activeSubjects.slice(0, 4).map((subject, index) => ({
    subject,
    index,
    classCount: activeClasses.filter((item) => item.subjectId === subject.id).length,
    noteCount: activeNotes.filter((note) => activeClasses.find((c) => c.id === note.classSessionId)?.subjectId === subject.id).length,
  })), [activeSubjects, activeClasses, activeNotes])

  const selectedSubject = activeSubjects.find((s) => s.id === selectedSubjectId) ?? null
  const selectedSubjectClasses = activeClasses.filter((c) => c.subjectId === selectedSubject?.id)
  const selectedGrades = grades.filter((grade) => grade.subjectId === selectedSubject?.id)
  const todayKey = new Date().toDateString()
  const todayClasses = activeClasses.filter((classSession) => new Date(classSession.startedAtEpochMs).toDateString() === todayKey)
  const upcomingClasses = activeClasses
    .filter((classSession) => classSession.startedAtEpochMs >= Date.now())
    .sort((a, b) => a.startedAtEpochMs - b.startedAtEpochMs)

  useEffect(() => {
    if (selectedSubjectId && !activeSubjectIds.has(selectedSubjectId)) {
      setSelectedSubjectId(null)
      setSelectedClassId(null)
      setAcademicLevel('subjects')
      return
    }
    if (selectedClassId && !activeClassIds.has(selectedClassId)) {
      setSelectedClassId(null)
      setAcademicLevel(selectedSubjectId ? 'classes' : 'subjects')
    }
  }, [activeCycle?.id, selectedSubjectId, selectedClassId, activeSubjectIds, activeClassIds])

  const gradeStats = useMemo""",
    "active cycle data",
)

app = regex_once(
    app,
    r"  const searchResults = search\.trim\(\) \? \[.*?\n  function openNewNote\(classId\?: string\) \{",
    """  const searchResults = search.trim() ? [
    ...activeSubjects
      .filter((s) => s.name.toLowerCase().includes(search.toLowerCase()))
      .map((s) => ({ type: 'Materia', title: s.name, action: () => openSubject(s.id) })),
    ...activeNotes
      .filter((n) => `${n.title} ${stripHtml(n.body)}`.toLowerCase().includes(search.toLowerCase()))
      .slice(0, 6)
      .map((n) => ({ type: 'Apunte', title: n.title, action: () => openExistingNote(n) })),
  ] : []

  function resetAcademicNavigation() {
    setSelectedSubjectId(null)
    setSelectedClassId(null)
    setAcademicLevel('subjects')
  }

  function navigate(next: Page) {
    setEditorOpen(false)
    if (next === 'subjects') resetAcademicNavigation()
    setPage(next)
    setSidebarOpen(false)
    setSearch('')
  }

  function openSubjects() {
    resetAcademicNavigation()
    setEditorOpen(false)
    setPage('subjects')
    setSidebarOpen(false)
    setSearch('')
  }

  function openSubject(subjectId: string) {
    if (!activeSubjectIds.has(subjectId)) return
    setSelectedSubjectId(subjectId)
    setSelectedClassId(null)
    setAcademicLevel('classes')
    setEditorOpen(false)
    setPage('subjects')
    setSidebarOpen(false)
    setSearch('')
  }

  function openClass(classId: string) {
    const classSession = activeClasses.find((item) => item.id === classId)
    if (!classSession) return
    setSelectedSubjectId(classSession.subjectId)
    setSelectedClassId(classId)
    setAcademicLevel('class')
    setEditorOpen(false)
    setPage('subjects')
    setSidebarOpen(false)
    setSearch('')
  }

  async function createSubjectFromWeb(name: string): Promise<string | null> {
    if (!activeCycle) return null
    const now = Date.now()
    const palette = ['#5b82d8', '#7c6ccf', '#4f9a82', '#a77b45', '#b86771', '#6d79a8']
    const subject: SubjectRecord = {
      id: crypto.randomUUID(),
      cycleId: activeCycle.id,
      name: name.trim(),
      colorHex: palette[activeSubjects.length % palette.length],
      createdAtEpochMs: now,
      updatedAtEpochMs: now,
      revision: 1,
      deviceId: getDeviceId(),
    }
    await db.subjects.add(subject)
    await queueUpsert('subjects', subject.id, subject)
    await refresh()
    return subject.id
  }

  async function createClassFromWeb(subjectId: string, title: string): Promise<string | null> {
    if (!activeSubjectIds.has(subjectId)) return null
    const now = Date.now()
    const classSession: ClassSessionRecord = {
      id: crypto.randomUUID(),
      subjectId,
      title: title.trim(),
      startedAtEpochMs: now,
      createdAtEpochMs: now,
      updatedAtEpochMs: now,
      revision: 1,
      deviceId: getDeviceId(),
    }
    await db.classSessions.add(classSession)
    await queueUpsert('class_sessions', classSession.id, classSession)
    await refresh()
    return classSession.id
  }

  function openNewNote(classId?: string) {""",
    "search and academic navigation",
)

app = regex_once(
    app,
    r"  function openNewNote\(classId\?: string\) \{.*?\n  \}\n\n  function openExistingNote",
    """  function openNewNote(classId?: string) {
    const targetClassId = classId ?? selectedClassId ?? activeClasses[0]?.id ?? null
    if (!targetClassId) {
      openSubjects()
      return
    }
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
    setEditorOpen(true)
  }

  function openExistingNote""",
    "new note target class",
)

app = replace_once(
    app,
    "    return notes.slice(0, limit).map((note) => {\n      const classSession = classes.find((item) => item.id === note.classSessionId)\n      const subject = subjects.find((item) => item.id === classSession?.subjectId)\n",
    "    return activeNotes.slice(0, limit).map((note) => {\n      const classSession = activeClasses.find((item) => item.id === note.classSessionId)\n      const subject = activeSubjects.find((item) => item.id === classSession?.subjectId)\n",
    "AI note context",
)

app = replace_once(
    app,
    "                onClick={() => { setSelectedSubjectId(subject.id); navigate('subjects') }}\n",
    "                onClick={() => openSubject(subject.id)}\n",
    "dashboard subject navigation",
)

old_subject_view = """      {page === 'subjects' && <div className=\"feature-grid subjects-view\">\n        <aside className=\"section-card list-panel\">\n          {activeSubjects.map((subject, index) => <button key={subject.id} className={selectedSubject?.id === subject.id ? 'selected' : ''} onClick={() => setSelectedSubjectId(subject.id)}>\n            <span className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>{subjectIcons[index % subjectIcons.length]}</span>\n            <span><strong>{subject.name}</strong><small>{classes.filter((c) => c.subjectId === subject.id).length} clases</small></span>\n          </button>)}\n        </aside>\n        <section className=\"section-card detail-panel\">\n          <div className=\"detail-heading\"><div><p className=\"eyebrow\">MATERIA</p><h2>{selectedSubject?.name ?? 'Selecciona una materia'}</h2></div><button className=\"primary\" disabled={!selectedSubjectClasses[0]} onClick={() => selectedSubjectClasses[0] && openNewNote(selectedSubjectClasses[0].id)}>＋ Apunte</button></div>\n          <div className=\"class-note-list\">\n            {selectedSubjectClasses.map((classSession) => <article key={classSession.id}>\n              <div><strong>{classSession.title}</strong><small>{fullDate(classSession.startedAtEpochMs)}</small></div>\n              <div>{notes.filter((n) => n.classSessionId === classSession.id).map((note) => <div className=\"class-note-row\" key={note.id}>\n                <button onClick={() => openExistingNote(note)}>✎ {note.title}</button>\n                <button className=\"danger-text\" onClick={() => void deleteNote(note)}>Eliminar</button>\n              </div>)}<button onClick={() => openNewNote(classSession.id)}>＋ Nuevo apunte</button></div>\n            </article>)}\n          </div>\n        </section>\n      </div>}\n"""
new_subject_view = """      {page === 'subjects' && <AcademicWorkspace\n        cycleName={activeCycle?.name}\n        subjects={activeSubjects}\n        classes={activeClasses}\n        notes={activeNotes}\n        level={academicLevel}\n        selectedSubjectId={selectedSubjectId}\n        selectedClassId={selectedClassId}\n        onOpenSubjects={openSubjects}\n        onOpenSubject={openSubject}\n        onOpenClass={openClass}\n        onOpenNote={openExistingNote}\n        onNewNote={openNewNote}\n        onDeleteNote={deleteNote}\n        onCreateSubject={createSubjectFromWeb}\n        onCreateClass={createClassFromWeb}\n      />}\n"""
app = replace_once(app, old_subject_view, new_subject_view, "subjects workspace")

old_notes = """      {page === 'notes' && <section className=\"section-card collection-list note-collection\">\n        {notes.map((note) => <article className=\"note-list-row\" key={note.id}>\n          <button className=\"note-list-open\" onClick={() => openExistingNote(note)}>\n            <span className=\"file-icon blue\">≡</span>\n            <span><strong>{note.title}</strong><small>{stripHtml(note.body).slice(0, 100) || 'Sin contenido'}</small></span>\n            <time>{shortDate(note.updatedAtEpochMs)}<small>{shortTime(note.updatedAtEpochMs)}</small></time>\n          </button>\n          <button className=\"note-list-delete\" onClick={() => void deleteNote(note)} title=\"Eliminar apunte\">⌫</button>\n        </article>)}\n        {notes.length === 0 && <p className=\"empty-state\">Todavía no hay apuntes.</p>}\n      </section>}\n"""
new_notes = """      {page === 'notes' && <section className=\"section-card collection-list note-collection\">\n        {activeNotes.map((note) => <article className=\"note-list-row\" key={note.id}>\n          <button className=\"note-list-open\" onClick={() => openExistingNote(note)}>\n            <span className=\"file-icon blue\">≡</span>\n            <span><strong>{note.title}</strong><small>{stripHtml(note.body).slice(0, 100) || 'Sin contenido'}</small></span>\n            <time>{shortDate(note.updatedAtEpochMs)}<small>{shortTime(note.updatedAtEpochMs)}</small></time>\n          </button>\n          <button className=\"note-list-delete\" onClick={() => void deleteNote(note)} title=\"Eliminar apunte\">⌫</button>\n        </article>)}\n        {activeNotes.length === 0 && <p className=\"empty-state\">Todavía no hay apuntes en el ciclo activo.</p>}\n      </section>}\n"""
app = replace_once(app, old_notes, new_notes, "active cycle notes")

app = replace_once(
    app,
    "        <aside className=\"section-card list-panel grade-subjects\">{subjects.map((subject, index) =>",
    "        <aside className=\"section-card list-panel grade-subjects\">{activeSubjects.map((subject, index) =>",
    "grade active subjects",
)

app = replace_once(
    app,
    "            <select value={editorClassId ?? ''} onChange={(event) => setEditorClassId(event.target.value)}>{classes.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}</select>",
    "            <select value={editorClassId ?? ''} onChange={(event) => setEditorClassId(event.target.value)}>{activeClasses.map((item) => <option key={item.id} value={item.id}>{activeSubjects.find((subject) => subject.id === item.subjectId)?.name ?? 'Materia'} · {item.title}</option>)}</select>",
    "editor active classes",
)

app = replace_once(
    app,
    "      <footer className=\"data-footnote\">{cycles.length} ciclo · {subjects.length} materias · {classes.length} clases · almacenamiento local-first</footer>",
    "      <footer className=\"data-footnote\">{activeCycle?.name ?? 'Sin ciclo'} · {activeSubjects.length} materias · {activeClasses.length} clases · almacenamiento local-first</footer>",
    "dashboard cycle footer",
)

write(app_path, app)

main_path = "web/src/main.tsx"
main = read(main_path)
main = replace_once(main, "import './web-phase1.css'\n", "import './web-phase1.css'\nimport './academic-workspace.css'\n", "academic css import")
write(main_path, main)

print('NotCan Web Phase 2 academic hierarchy patch applied')
