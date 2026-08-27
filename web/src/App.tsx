import { type ChangeEvent, type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { db, getDeviceId, queueUpsert, seedDemoIfEmpty } from './lib/db'
import { syncNow } from './lib/sync'
import { supabase } from './lib/supabase'
import type { ClassSessionRecord, GradeItemRecord, NotePageRecord, StudyCycleRecord, SubjectRecord } from './types/sync'

type SyncLabel = { text: string; kind: 'good' | 'warn' | 'muted' }
type SaveState = 'idle' | 'saving' | 'saved'
type Page = 'home' | 'subjects' | 'notes' | 'library' | 'calendar' | 'tasks' | 'grades' | 'maps' | 'ai' | 'sync' | 'settings' | 'account'
type LocalTask = { id: string; title: string; detail: string; done: boolean }
type LocalFile = { id: string; name: string; size: number; type: string; addedAt: number }

const subjectIcons = ['⌘', '✦', '⚖', '♡', '✚', '◈']
const subjectAccents = ['blue', 'violet', 'teal', 'gold', 'rose', 'indigo']

function shortDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { day: '2-digit', month: 'short' }).format(new Date(epoch))
}
function shortTime(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { hour: '2-digit', minute: '2-digit' }).format(new Date(epoch))
}
function fullDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(epoch))
}

export default function App() {
  const [cycles, setCycles] = useState<StudyCycleRecord[]>([])
  const [subjects, setSubjects] = useState<SubjectRecord[]>([])
  const [classes, setClasses] = useState<ClassSessionRecord[]>([])
  const [notes, setNotes] = useState<NotePageRecord[]>([])
  const [grades, setGrades] = useState<GradeItemRecord[]>([])
  const [pending, setPending] = useState(0)
  const [syncLabel, setSyncLabel] = useState<SyncLabel>({ text: 'Solo local', kind: 'muted' })
  const [page, setPage] = useState<Page>('home')
  const [search, setSearch] = useState('')
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)

  const [session, setSession] = useState<Session | null>(null)
  const [accountOpen, setAccountOpen] = useState(false)
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authMessage, setAuthMessage] = useState('')
  const [authBusy, setAuthBusy] = useState(false)

  const [editorOpen, setEditorOpen] = useState(false)
  const [editorNoteId, setEditorNoteId] = useState<string | null>(null)
  const [editorClassId, setEditorClassId] = useState<string | null>(null)
  const [editorTitle, setEditorTitle] = useState('')
  const [editorBody, setEditorBody] = useState('')
  const [editorCreatedAt, setEditorCreatedAt] = useState<number | null>(null)
  const [editorRevision, setEditorRevision] = useState(1)
  const [saveState, setSaveState] = useState<SaveState>('idle')

  const [localTasks, setLocalTasks] = useState<LocalTask[]>(() => {
    try { return JSON.parse(localStorage.getItem('notcan-tasks') || '[]') } catch { return [] }
  })
  const [localFiles, setLocalFiles] = useState<LocalFile[]>(() => {
    try { return JSON.parse(localStorage.getItem('notcan-files-meta') || '[]') } catch { return [] }
  })
  const [mapTitle, setMapTitle] = useState('Mi mapa conceptual')
  const [mapNodes, setMapNodes] = useState(['Idea central', 'Concepto 1', 'Concepto 2'])
  const [aiPrompt, setAiPrompt] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  async function refresh() {
    const [cycleRows, subjectRows, classRows, noteRows, gradeRows, outboxCount] = await Promise.all([
      db.studyCycles.toArray(), db.subjects.toArray(), db.classSessions.orderBy('startedAtEpochMs').reverse().toArray(),
      db.notePages.orderBy('updatedAtEpochMs').reverse().toArray(), db.gradeItems.toArray(), db.outbox.count(),
    ])
    setCycles(cycleRows); setSubjects(subjectRows); setClasses(classRows); setNotes(noteRows); setGrades(gradeRows); setPending(outboxCount)
    if (!editorClassId && classRows[0]) setEditorClassId(classRows[0].id)
    if (!selectedSubjectId && subjectRows[0]) setSelectedSubjectId(subjectRows[0].id)
  }

  useEffect(() => { void (async () => { await seedDemoIfEmpty(); await refresh() })() }, [])
  useEffect(() => { localStorage.setItem('notcan-tasks', JSON.stringify(localTasks)) }, [localTasks])
  useEffect(() => { localStorage.setItem('notcan-files-meta', JSON.stringify(localFiles)) }, [localFiles])

  useEffect(() => {
    if (!supabase) return
    void supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession))
    return () => data.subscription.unsubscribe()
  }, [])

  const recentNotes = notes.slice(0, 3)
  const latestNote = notes[0] ?? null
  const latestClass = latestNote ? classes.find((item) => item.id === latestNote.classSessionId) ?? classes[0] ?? null : classes[0] ?? null
  const featuredSubject = latestClass ? subjects.find((item) => item.id === latestClass.subjectId) ?? subjects[0] ?? null : subjects[0] ?? null
  const subjectCards = useMemo(() => subjects.slice(0, 4).map((subject, index) => ({
    subject, index,
    classCount: classes.filter((item) => item.subjectId === subject.id).length,
    noteCount: notes.filter((note) => classes.find((c) => c.id === note.classSessionId)?.subjectId === subject.id).length,
  })), [subjects, classes, notes])
  const selectedSubject = subjects.find(s => s.id === selectedSubjectId) ?? subjects[0] ?? null
  const selectedSubjectClasses = classes.filter(c => c.subjectId === selectedSubject?.id)
  const selectedSubjectNotes = notes.filter(n => selectedSubjectClasses.some(c => c.id === n.classSessionId))
  const searchResults = search.trim() ? [
    ...subjects.filter(s => s.name.toLowerCase().includes(search.toLowerCase())).map(s => ({ type: 'Materia', title: s.name, action: () => { setSelectedSubjectId(s.id); navigate('subjects') } })),
    ...notes.filter(n => `${n.title} ${n.body}`.toLowerCase().includes(search.toLowerCase())).slice(0, 6).map(n => ({ type: 'Apunte', title: n.title, action: () => openExistingNote(n) })),
  ] : []

  function navigate(next: Page) { setEditorOpen(false); setPage(next); setSearch('') }
  function openNewNote(classId?: string) {
    const now = Date.now(); setEditorNoteId(crypto.randomUUID()); setEditorClassId(classId ?? classes[0]?.id ?? null)
    setEditorTitle(''); setEditorBody(''); setEditorCreatedAt(now); setEditorRevision(1); setSaveState('idle'); setEditorOpen(true)
  }
  function openExistingNote(note: NotePageRecord) {
    setEditorNoteId(note.id); setEditorClassId(note.classSessionId); setEditorTitle(note.title); setEditorBody(note.body)
    setEditorCreatedAt(note.createdAtEpochMs); setEditorRevision(note.revision); setSaveState('saved'); setEditorOpen(true)
  }

  useEffect(() => {
    if (!editorOpen || !editorNoteId || !editorClassId) return
    if (!editorTitle.trim() && !editorBody.trim()) { setSaveState('idle'); return }
    setSaveState('saving')
    const timer = window.setTimeout(() => { void (async () => {
      const now = Date.now(); const existing = await db.notePages.get(editorNoteId)
      const revision = existing ? Math.max(existing.revision + 1, editorRevision) : editorRevision
      const note: NotePageRecord = { id: editorNoteId, classSessionId: editorClassId, title: editorTitle.trim() || 'Apunte sin título', body: editorBody, createdAtEpochMs: editorCreatedAt ?? now, updatedAtEpochMs: now, revision, deviceId: getDeviceId() }
      await db.notePages.put(note); await queueUpsert('note_pages', note.id, note); setEditorRevision(revision); setSaveState('saved'); await refresh()
    })() }, 700)
    return () => window.clearTimeout(timer)
  }, [editorOpen, editorNoteId, editorClassId, editorTitle, editorBody])

  async function handleSync() {
    if (!session) { setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' }); setAccountOpen(true); return }
    setSyncLabel({ text: 'Sincronizando…', kind: 'warn' }); const result = await syncNow()
    if (result.state === 'synced') setSyncLabel({ text: 'Sincronizado', kind: 'good' })
    else if (result.state === 'offline') setSyncLabel({ text: 'Sin Internet', kind: 'warn' })
    else if (result.state === 'unauthenticated') { setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' }); setAccountOpen(true) }
    else setSyncLabel({ text: result.state === 'backend-not-configured' ? 'Solo local' : 'Error al sincronizar', kind: result.state === 'backend-not-configured' ? 'muted' : 'warn' })
    await refresh()
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault(); if (!supabase || authBusy) return; setAuthBusy(true); setAuthMessage('')
    try {
      if (authMode === 'login') { const { error } = await supabase.auth.signInWithPassword({ email, password }); if (error) throw error; setAuthMessage('Sesión iniciada. Ya puedes sincronizar NotCan.') }
      else { const { data, error } = await supabase.auth.signUp({ email, password }); if (error) throw error; setAuthMessage(data.session ? 'Cuenta creada y sesión iniciada.' : 'Cuenta creada. Revisa tu correo para confirmar el acceso.'); if (!data.session) setAuthMode('login') }
    } catch (error) { setAuthMessage(error instanceof Error ? error.message : String(error)) } finally { setAuthBusy(false) }
  }

  function importFiles(event: ChangeEvent<HTMLInputElement>) {
    const chosen = Array.from(event.target.files ?? []).map(file => ({ id: crypto.randomUUID(), name: file.name, size: file.size, type: file.type || 'archivo', addedAt: Date.now() }))
    setLocalFiles(prev => [...chosen, ...prev]); event.target.value = ''
  }
  async function addGrade() {
    if (!selectedSubject) return
    const now = Date.now(); const item: GradeItemRecord = { id: crypto.randomUUID(), subjectId: selectedSubject.id, title: `Evaluación ${grades.length + 1}`, score: 0, maxScore: 10, weightPercent: 10, createdAtEpochMs: now, updatedAtEpochMs: now, revision: 1, deviceId: getDeviceId() }
    await db.gradeItems.add(item); await queueUpsert('grade_items', item.id, item); await refresh()
  }

  const navGroups: { label: string; items: { page: Page; icon: string; label: string }[] }[] = [
    { label: 'ESTUDIO', items: [{ page: 'home', icon: '⌂', label: 'Inicio' }, { page: 'subjects', icon: '▣', label: 'Materias' }, { page: 'notes', icon: '✎', label: 'Apuntes' }, { page: 'library', icon: '▥', label: 'Biblioteca' }] },
    { label: 'ORGANIZACIÓN', items: [{ page: 'calendar', icon: '□', label: 'Calendario' }, { page: 'tasks', icon: '☑', label: 'Tareas' }, { page: 'grades', icon: '☆', label: 'Calificaciones' }] },
    { label: 'HERRAMIENTAS', items: [{ page: 'maps', icon: '⌘', label: 'Mapas' }, { page: 'ai', icon: '✦', label: 'NotCan AI' }] },
  ]

  const saveText = saveState === 'saving' ? 'Guardando…' : saveState === 'saved' ? 'Guardado localmente' : 'Empieza a escribir'

  function renderHome() {
    return <main className="main-area">
      <section className="welcome"><div className="sparkle">✦</div><div><h1>Tu estudio, en cualquier dispositivo</h1><p>Organiza tus ideas. Profundiza tu conocimiento. Todo en un mismo espacio.</p></div></section>
      <div className="dashboard-grid"><section className="content-column">
        <article className="section-card continue-card-wrap"><div className="section-title"><span>▣</span><strong>CONTINUAR ESTUDIANDO</strong></div><div className="continue-card"><div className="continue-copy"><div className="subject-symbol">✦</div><h2>{featuredSubject?.name ?? 'Tu próxima materia'}</h2><div className="progress-row"><span>{latestNote ? 'Último apunte disponible' : 'Comienza tu primer apunte'}</span><div className="progress"><i /></div></div><p>Último apunte</p><strong className="latest-title">{latestNote?.title ?? 'Aún no hay apuntes recientes'}</strong><small>{latestNote ? `Editado hoy, ${shortTime(latestNote.updatedAtEpochMs)}` : 'Crea uno para comenzar'}</small></div><div className="hero-art" aria-hidden="true"><span>✦</span><span>✧</span><span>✦</span></div><button className="open-note" onClick={() => latestNote ? openExistingNote(latestNote) : openNewNote()}>{latestNote ? 'Abrir apunte' : 'Crear apunte'} →</button></div></article>
        <article className="section-card subjects-section"><div className="section-heading-row"><div className="section-title"><span>▣</span><strong>MATERIAS</strong></div><button onClick={() => navigate('subjects')}>Ver todas</button></div><div className="subject-grid">{subjectCards.map(({ subject, index, classCount, noteCount }) => <button className="course-card clickable-card" key={subject.id} onClick={() => { setSelectedSubjectId(subject.id); navigate('subjects') }}><div className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>{subjectIcons[index % subjectIcons.length]}</div><div className="course-copy"><strong>{subject.name}</strong><small>{classCount} clases · {noteCount} apuntes</small></div><span className="more">•••</span><div className="course-progress"><i style={{ width: `${Math.min(90, 28 + classCount * 12)}%` }} /></div></button>)}</div></article>
        <article className="section-card recent-section"><div className="section-heading-row"><div className="section-title"><span>◷</span><strong>RECIENTES</strong></div><button onClick={() => navigate('notes')}>Ver todos los recientes</button></div><div className="recent-grid">{recentNotes.map((note, index) => <button className="recent-item clickable-card" key={note.id} onClick={() => openExistingNote(note)}><div className={`file-icon ${subjectAccents[index % subjectAccents.length]}`}>≡</div><div><strong>{note.title}</strong><span>Apunte de clase</span><small>Editado {shortDate(note.updatedAtEpochMs)}, {shortTime(note.updatedAtEpochMs)}</small></div><span>☆</span></button>)}</div></article>
      </section><aside className="right-column">
        <article className="section-card tasks-card"><div className="section-heading-row"><div className="section-title"><span>□</span><strong>PRÓXIMAS TAREAS / EXÁMENES</strong></div><button onClick={() => navigate('tasks')}>Ver todas</button></div><div className="task-list"><button className="task-row" onClick={() => navigate('tasks')}><span className="task-icon blue">▤</span><div><strong>Próxima entrega</strong><small>{featuredSubject?.name ?? 'Materia'}</small></div><time>Mañana<small>23:59</small></time></button><button className="task-row" onClick={() => navigate('tasks')}><span className="task-icon violet">▤</span><div><strong>Revisar apuntes</strong><small>Sesión de estudio</small></div><time>Esta semana<small>18:00</small></time></button></div></article>
        <article className="section-card sync-panel"><div className={`sync-check ${syncLabel.kind}`}>✓</div><div><div className="section-title"><strong>SINCRONIZACIÓN</strong></div><h3>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Modo local'}</h3><p>{session ? 'Pulsa sincronizar para actualizar tus dispositivos.' : 'Tus datos están seguros en este dispositivo. Inicia sesión para sincronizar.'}</p></div><button onClick={handleSync}>↻</button></article>
        <article className="section-card quick-card"><div className="section-title"><span>✦</span><strong>ACCESOS RÁPIDOS</strong></div><button onClick={() => openNewNote()}>✎ Nuevo apunte <span>→</span></button><button onClick={() => navigate('maps')}>⌘ Crear mapa conceptual <span>→</span></button><button onClick={() => navigate('ai')}>✦ Preguntar a NotCan AI <span>→</span></button></article>
      </aside></div><footer className="data-footnote">{cycles.length} ciclo · {subjects.length} materias · {classes.length} clases · almacenamiento local-first</footer>
    </main>
  }

  function renderPage() {
    if (page === 'home') return renderHome()
    const titles: Record<Page, [string, string]> = {
      home: ['', ''], subjects: ['Materias', 'Organiza tus clases, apuntes y recursos por materia.'], notes: ['Apuntes', 'Todo lo que has escrito, ordenado por fecha.'], library: ['Biblioteca', 'Tus documentos académicos en un mismo lugar.'], calendar: ['Calendario', 'Clases y actividades organizadas por fecha.'], tasks: ['Tareas', 'Pendientes de estudio y próximas entregas.'], grades: ['Calificaciones', 'Registra tus notas y sigue tu progreso.'], maps: ['Mapas', 'Construye mapas mentales y conceptuales.'], ai: ['NotCan AI', 'Asistente académico para estudiar y trabajar con tus materiales.'], sync: ['Sincronización', 'Estado de tus cambios entre dispositivos.'], settings: ['Ajustes', 'Personaliza el comportamiento de NotCan.'], account: ['Cuenta', 'Gestiona tu sesión y sincronización.']
    }
    const [title, subtitle] = titles[page]
    return <main className="main-area feature-page"><div className="feature-header"><div><p className="eyebrow">NOTCAN</p><h1>{title}</h1><p>{subtitle}</p></div>{['notes','library','grades'].includes(page) && <button className="new-button" onClick={() => page === 'notes' ? openNewNote() : page === 'library' ? fileInputRef.current?.click() : void addGrade()}>＋ Nuevo</button>}</div>
      {page === 'subjects' && <div className="feature-grid subjects-view"><aside className="section-card list-panel">{subjects.map((subject, i) => <button key={subject.id} className={selectedSubject?.id === subject.id ? 'selected' : ''} onClick={() => setSelectedSubjectId(subject.id)}><span className={`course-icon ${subjectAccents[i % subjectAccents.length]}`}>{subjectIcons[i % subjectIcons.length]}</span><span><strong>{subject.name}</strong><small>{classes.filter(c => c.subjectId === subject.id).length} clases</small></span></button>)}</aside><section className="section-card detail-panel"><div className="detail-heading"><div><p className="eyebrow">MATERIA</p><h2>{selectedSubject?.name ?? 'Selecciona una materia'}</h2></div><button className="primary" onClick={() => openNewNote(selectedSubjectClasses[0]?.id)}>＋ Apunte</button></div><div className="class-note-list">{selectedSubjectClasses.map(c => <article key={c.id}><div><strong>{c.title}</strong><small>{fullDate(c.startedAtEpochMs)}</small></div><div>{notes.filter(n => n.classSessionId === c.id).map(n => <button key={n.id} onClick={() => openExistingNote(n)}>✎ {n.title}</button>)}<button onClick={() => openNewNote(c.id)}>＋ Nuevo apunte</button></div></article>)}</div></section></div>}
      {page === 'notes' && <section className="section-card collection-list">{notes.map(note => <button key={note.id} onClick={() => openExistingNote(note)}><span className="file-icon blue">≡</span><span><strong>{note.title}</strong><small>{note.body.slice(0, 100) || 'Sin contenido'}</small></span><time>{shortDate(note.updatedAtEpochMs)}<small>{shortTime(note.updatedAtEpochMs)}</small></time></button>)}</section>}
      {page === 'library' && <><input ref={fileInputRef} type="file" multiple accept=".pdf,.doc,.docx,.epub,.txt" hidden onChange={importFiles} /><section className="section-card library-drop" onClick={() => fileInputRef.current?.click()}><strong>＋ Importar documentos</strong><p>PDF, DOC/DOCX, EPUB y TXT. En esta fase guardamos el registro local; el almacenamiento de archivos se conectará después.</p></section><section className="file-grid">{localFiles.map(file => <article className="section-card file-tile" key={file.id}><div className="file-icon rose">DOC</div><strong>{file.name}</strong><small>{(file.size / 1024 / 1024).toFixed(2)} MB · {shortDate(file.addedAt)}</small><button onClick={() => setLocalFiles(prev => prev.filter(f => f.id !== file.id))}>Eliminar</button></article>)}</section></>}
      {page === 'calendar' && <section className="calendar-view">{classes.map(c => <article className="section-card calendar-event" key={c.id}><time><strong>{new Date(c.startedAtEpochMs).getDate()}</strong><span>{shortDate(c.startedAtEpochMs).split(' ')[1]}</span></time><div><strong>{c.title}</strong><small>{subjects.find(s => s.id === c.subjectId)?.name ?? 'Materia'} · {shortTime(c.startedAtEpochMs)}</small></div></article>)}</section>}
      {page === 'tasks' && <section className="section-card tasks-view"><form onSubmit={e => { e.preventDefault(); const form = new FormData(e.currentTarget); const title = String(form.get('title') || '').trim(); if (!title) return; setLocalTasks(prev => [...prev, { id: crypto.randomUUID(), title, detail: 'Pendiente personal', done: false }]); e.currentTarget.reset() }}><input name="title" placeholder="Nueva tarea…" /><button className="primary">Añadir</button></form>{localTasks.length === 0 && <p className="empty-state">No tienes tareas personales todavía.</p>}{localTasks.map(task => <label className={`task-check ${task.done ? 'done' : ''}`} key={task.id}><input type="checkbox" checked={task.done} onChange={() => setLocalTasks(prev => prev.map(t => t.id === task.id ? { ...t, done: !t.done } : t))} /><span><strong>{task.title}</strong><small>{task.detail}</small></span><button onClick={e => { e.preventDefault(); setLocalTasks(prev => prev.filter(t => t.id !== task.id)) }}>×</button></label>)}</section>}
      {page === 'grades' && <div className="feature-grid"><aside className="section-card list-panel">{subjects.map((s,i) => <button key={s.id} className={selectedSubject?.id === s.id ? 'selected' : ''} onClick={() => setSelectedSubjectId(s.id)}><span className={`course-icon ${subjectAccents[i % subjectAccents.length]}`}>☆</span><span><strong>{s.name}</strong><small>{grades.filter(g => g.subjectId === s.id).length} notas</small></span></button>)}</aside><section className="section-card grade-list">{grades.filter(g => g.subjectId === selectedSubject?.id).map(g => <article key={g.id}><div><strong>{g.title}</strong><small>Peso {g.weightPercent}%</small></div><strong>{g.score}/{g.maxScore}</strong></article>)}<button className="primary" onClick={addGrade}>＋ Añadir calificación</button></section></div>}
      {page === 'maps' && <section className="section-card map-workspace"><input className="map-title-input" value={mapTitle} onChange={e => setMapTitle(e.target.value)} /><div className="map-canvas"><div className="map-node root">{mapNodes[0]}</div>{mapNodes.slice(1).map((node,i) => <div className={`map-node child child-${i}`} key={i}>{node}</div>)}</div><div className="map-controls"><input placeholder="Nuevo concepto" onKeyDown={e => { if (e.key === 'Enter') { const value = e.currentTarget.value.trim(); if (value) { setMapNodes(prev => [...prev, value]); e.currentTarget.value = '' } } }} /><button onClick={() => setMapNodes(prev => [...prev, `Concepto ${prev.length}`])}>＋ Nodo</button><button onClick={() => setMapNodes(['Idea central','Concepto 1','Concepto 2'])}>Restablecer</button></div></section>}
      {page === 'ai' && <section className="section-card ai-workspace"><div className="ai-empty"><div className="sparkle">✦</div><h2>NotCan AI</h2><p>La interfaz ya está preparada. La conexión con el proveedor de IA será el siguiente módulo.</p></div><div className="ai-composer"><textarea value={aiPrompt} onChange={e => setAiPrompt(e.target.value)} placeholder="Pregunta sobre tus apuntes, una materia o un documento…" /><button className="primary" onClick={() => alert('La interfaz está lista; todavía falta conectar el proveedor de IA.')}>Enviar</button></div></section>}
      {page === 'sync' && <section className="section-card settings-list"><article><div><strong>{session ? 'Cuenta conectada' : 'Modo local'}</strong><p>{session ? session.user.email : 'Puedes trabajar sin cuenta. Para sincronizar entre dispositivos necesitas iniciar sesión.'}</p></div><button className="primary" onClick={session ? handleSync : () => setAccountOpen(true)}>{session ? 'Sincronizar ahora' : 'Iniciar sesión'}</button></article><article><div><strong>Cambios pendientes</strong><p>{pending} operaciones esperan sincronización.</p></div><span className="big-number">{pending}</span></article></section>}
      {page === 'settings' && <section className="section-card settings-list"><article><div><strong>Modo offline</strong><p>NotCan guarda primero en este dispositivo.</p></div><span className="status-pill">Activo</span></article><article><div><strong>Autoguardado</strong><p>Los apuntes se guardan mientras escribes.</p></div><span className="status-pill">Activo</span></article><article><div><strong>Sincronización automática</strong><p>La activaremos cuando terminemos el motor Android ↔ Web.</p></div><span className="status-pill muted">Próximamente</span></article></section>}
      {page === 'account' && <section className="section-card account-page"><div className="account-avatar">{session?.user.email?.[0]?.toUpperCase() ?? 'N'}</div><h2>{session ? 'Cuenta de NotCan' : 'Usando NotCan localmente'}</h2><p>{session?.user.email ?? 'No necesitas cuenta para estudiar. Inicia sesión solo si quieres sincronizar entre dispositivos.'}</p><button className="primary" onClick={() => setAccountOpen(true)}>{session ? 'Gestionar cuenta' : 'Iniciar sesión / Crear cuenta'}</button></section>}
    </main>
  }

  return <div className="app-shell"><aside className="sidebar"><div className="brand-row"><div className="brand-mark">N</div><div><strong>NotCan</strong><span>Web</span></div></div>{navGroups.map(group => <div className="nav-group" key={group.label}><p>{group.label}</p>{group.items.map(item => <button key={item.page} className={`nav-item ${page === item.page && !editorOpen ? 'active' : ''} ${item.page === 'ai' ? 'ai-nav' : ''}`} onClick={() => navigate(item.page)}><span>{item.icon}</span>{item.label}</button>)}</div>)}<div className="sidebar-bottom"><button className={`nav-item ${page === 'sync' ? 'active' : ''}`} onClick={() => navigate('sync')}><span>↻</span>Sincronización <i className={`dot ${syncLabel.kind}`} /></button><button className={`nav-item ${page === 'settings' ? 'active' : ''}`} onClick={() => navigate('settings')}><span>⚙</span>Ajustes</button><button className={`nav-item ${page === 'account' ? 'active' : ''}`} onClick={() => navigate('account')}><span>○</span>Cuenta</button></div></aside><div className="page-area"><header className="global-topbar"><div className="search-wrap"><label className="search-box"><span>⌕</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar en NotCan…" /><kbd>Ctrl K</kbd></label>{searchResults.length > 0 && <div className="search-results">{searchResults.map((r,i) => <button key={`${r.type}-${i}`} onClick={r.action}><small>{r.type}</small><strong>{r.title}</strong></button>)}</div>}</div><div className="topbar-actions"><button className="sync-status" onClick={handleSync}><span className={`dot ${session ? syncLabel.kind : 'muted'}`} /><span><strong>{session ? syncLabel.text : 'Solo local'}</strong><small>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Inicia sesión para sincronizar'}</small></span></button><button className="new-button" onClick={() => openNewNote()}>＋ Nuevo</button><button className="avatar-button" onClick={() => navigate('account')}>{session?.user.email?.[0]?.toUpperCase() ?? 'N'}</button></div></header>{editorOpen ? <main className="note-editor-page"><div className="editor-toolbar"><button className="back-editor" onClick={() => setEditorOpen(false)}>← Volver</button><div className="editor-save-state"><span className={`dot ${saveState === 'saved' ? 'good' : saveState === 'saving' ? 'warn' : 'muted'}`} />{saveText}</div><button className="editor-more">•••</button></div><div className="editor-sheet"><div className="editor-meta-row"><select value={editorClassId ?? ''} onChange={e => setEditorClassId(e.target.value)}>{classes.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select><span>{editorCreatedAt ? fullDate(editorCreatedAt) : ''}</span></div><input className="editor-title-input" value={editorTitle} onChange={e => setEditorTitle(e.target.value)} placeholder="Título del apunte" autoFocus /><div className="format-bar"><button><strong>B</strong></button><button><em>I</em></button><button>U</button><span /><button>H1</button><button>H2</button><button>• Lista</button><button>☑</button><span /><button>↗ Enlace</button><button onClick={() => { setEditorOpen(false); setPage('ai') }}>✦ IA</button></div><textarea className="editor-body-input" value={editorBody} onChange={e => setEditorBody(e.target.value)} placeholder="Empieza a escribir tus apuntes…" /><div className="editor-footer"><span>{editorBody.trim() ? editorBody.trim().split(/\s+/).length : 0} palabras</span><span>Se guarda automáticamente en este dispositivo</span></div></div></main> : renderPage()}</div>{accountOpen && <div className="modal-backdrop" onMouseDown={() => setAccountOpen(false)}><section className="account-modal" onMouseDown={e => e.stopPropagation()}><div className="modal-title"><div><p className="eyebrow">CUENTA NOTCAN</p><h2>{session ? 'Tu cuenta' : authMode === 'login' ? 'Inicia sesión' : 'Crea tu cuenta'}</h2></div><button onClick={() => setAccountOpen(false)}>×</button></div>{session ? <div className="signed-account"><div className="account-avatar">{session.user.email?.[0]?.toUpperCase()}</div><strong>{session.user.email}</strong><p>La cuenta permite sincronizar NotCan entre la web y Android.</p><button className="primary" onClick={handleSync}>↻ Sincronizar ahora</button><button className="ghost" onClick={() => void supabase?.auth.signOut()}>Cerrar sesión</button></div> : <><p className="auth-copy">Puedes usar NotCan sin cuenta. Inicia sesión únicamente para sincronizar tus datos entre dispositivos.</p><form className="auth-form" onSubmit={submitAuth}><label>Correo<input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></label><label>Contraseña<input type="password" required minLength={8} value={password} onChange={e => setPassword(e.target.value)} /></label><button className="primary" disabled={authBusy}>{authBusy ? 'Procesando…' : authMode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</button></form>{authMessage && <p className="auth-message">{authMessage}</p>}<button className="text-button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthMessage('') }}>{authMode === 'login' ? 'Crear una cuenta nueva' : 'Ya tengo una cuenta'}</button></>}</section></div>}</div>
