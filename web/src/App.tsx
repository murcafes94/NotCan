import { useEffect, useMemo, useState } from 'react'
import { db, getDeviceId, queueUpsert, seedDemoIfEmpty } from './lib/db'
import { syncNow } from './lib/sync'
import type { ClassSessionRecord, NotePageRecord, StudyCycleRecord, SubjectRecord } from './types/sync'

type SyncLabel = { text: string; kind: 'good' | 'warn' | 'muted' }

export default function App() {
  const [cycles, setCycles] = useState<StudyCycleRecord[]>([])
  const [subjects, setSubjects] = useState<SubjectRecord[]>([])
  const [classes, setClasses] = useState<ClassSessionRecord[]>([])
  const [notes, setNotes] = useState<NotePageRecord[]>([])
  const [pending, setPending] = useState(0)
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)
  const [selectedClassId, setSelectedClassId] = useState<string | null>(null)
  const [syncLabel, setSyncLabel] = useState<SyncLabel>({ text: 'Solo local', kind: 'muted' })
  const [newNoteTitle, setNewNoteTitle] = useState('')
  const [newNoteBody, setNewNoteBody] = useState('')

  async function refresh() {
    const [cycleRows, subjectRows, classRows, noteRows, outboxCount] = await Promise.all([
      db.studyCycles.toArray(),
      db.subjects.toArray(),
      db.classSessions.orderBy('startedAtEpochMs').reverse().toArray(),
      db.notePages.orderBy('updatedAtEpochMs').reverse().toArray(),
      db.outbox.count(),
    ])
    setCycles(cycleRows)
    setSubjects(subjectRows)
    setClasses(classRows)
    setNotes(noteRows)
    setPending(outboxCount)
    if (!selectedSubjectId && subjectRows[0]) setSelectedSubjectId(subjectRows[0].id)
  }

  useEffect(() => {
    void (async () => {
      await seedDemoIfEmpty()
      await refresh()
    })()
  }, [])

  useEffect(() => {
    if (!selectedSubjectId) return
    const firstClass = classes.find((item) => item.subjectId === selectedSubjectId)
    setSelectedClassId(firstClass?.id ?? null)
  }, [selectedSubjectId, classes])

  const selectedSubject = subjects.find((item) => item.id === selectedSubjectId) ?? null
  const subjectClasses = useMemo(
    () => classes.filter((item) => item.subjectId === selectedSubjectId),
    [classes, selectedSubjectId],
  )
  const classNotes = useMemo(
    () => notes.filter((item) => item.classSessionId === selectedClassId),
    [notes, selectedClassId],
  )

  async function createNote() {
    if (!selectedClassId || !newNoteBody.trim()) return
    const now = Date.now()
    const note: NotePageRecord = {
      id: crypto.randomUUID(),
      classSessionId: selectedClassId,
      title: newNoteTitle.trim() || 'Apunte sin título',
      body: newNoteBody.trim(),
      createdAtEpochMs: now,
      updatedAtEpochMs: now,
      revision: 1,
      deviceId: getDeviceId(),
    }
    await db.notePages.add(note)
    await queueUpsert('note_pages', note.id, note)
    setNewNoteTitle('')
    setNewNoteBody('')
    await refresh()
  }

  async function handleSync() {
    setSyncLabel({ text: 'Sincronizando…', kind: 'warn' })
    const result = await syncNow()
    if (result.state === 'synced') {
      setSyncLabel({ text: `Sincronizado · ↑${result.pushed} ↓${result.pulled}`, kind: 'good' })
    } else if (result.state === 'offline') {
      setSyncLabel({ text: 'Sin Internet · cambios guardados', kind: 'warn' })
    } else if (result.state === 'backend-not-configured') {
      setSyncLabel({ text: 'Backend pendiente · datos seguros localmente', kind: 'muted' })
    } else if (result.state === 'unauthenticated') {
      setSyncLabel({ text: 'Falta iniciar sesión', kind: 'warn' })
    } else {
      setSyncLabel({ text: `Error: ${result.message}`, kind: 'warn' })
    }
    await refresh()
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">N</div>
          <div>
            <strong>NotCan</strong>
            <span>Web</span>
          </div>
        </div>

        <nav>
          <button className="nav-item active">⌂ <span>Inicio</span></button>
          <button className="nav-item">▤ <span>Materias</span></button>
          <button className="nav-item">✎ <span>Apuntes</span></button>
          <button className="nav-item">□ <span>Biblioteca</span></button>
          <button className="nav-item">◉ <span>Mapas</span></button>
          <button className="nav-item">◇ <span>Calendario</span></button>
          <button className="nav-item">AI <span>Asistente</span></button>
        </nav>

        <div className="sidebar-bottom">
          <div className="sync-chip"><span className={`dot ${syncLabel.kind}`} />{syncLabel.text}</div>
          <small>{pending} cambios pendientes</small>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div>
            <p className="eyebrow">ESPACIO ACADÉMICO</p>
            <h1>Tu estudio, en cualquier dispositivo</h1>
          </div>
          <button className="primary" onClick={handleSync}>↻ Sincronizar</button>
        </header>

        <section className="stats-grid">
          <article className="stat-card"><span>Ciclos</span><strong>{cycles.length}</strong><small>estructura compatible con Android</small></article>
          <article className="stat-card"><span>Materias</span><strong>{subjects.length}</strong><small>UUID estables</small></article>
          <article className="stat-card"><span>Clases</span><strong>{classes.length}</strong><small>local-first</small></article>
          <article className="stat-card"><span>Pendientes</span><strong>{pending}</strong><small>cola de sincronización</small></article>
        </section>

        <section className="workspace-grid">
          <div className="panel subjects-panel">
            <div className="panel-header"><div><p className="eyebrow">MATERIAS</p><h2>Semestre actual</h2></div></div>
            <div className="subject-list">
              {subjects.map((subject) => (
                <button
                  key={subject.id}
                  className={`subject-card ${selectedSubjectId === subject.id ? 'selected' : ''}`}
                  onClick={() => setSelectedSubjectId(subject.id)}
                >
                  <span className="subject-color" style={{ background: subject.colorHex ?? '#7aa2ff' }} />
                  <span><strong>{subject.name}</strong><small>{classes.filter((item) => item.subjectId === subject.id).length} clases</small></span>
                </button>
              ))}
            </div>
          </div>

          <div className="panel classes-panel">
            <div className="panel-header">
              <div><p className="eyebrow">{selectedSubject?.name ?? 'MATERIA'}</p><h2>Clases</h2></div>
            </div>
            <div className="class-list">
              {subjectClasses.map((classSession) => (
                <button
                  key={classSession.id}
                  className={`class-row ${selectedClassId === classSession.id ? 'selected' : ''}`}
                  onClick={() => setSelectedClassId(classSession.id)}
                >
                  <span>▣</span>
                  <span><strong>{classSession.title}</strong><small>{new Date(classSession.startedAtEpochMs).toLocaleDateString('es')}</small></span>
                </button>
              ))}
              {subjectClasses.length === 0 && <p className="empty">Todavía no hay clases en esta materia.</p>}
            </div>
          </div>

          <div className="panel notes-panel">
            <div className="panel-header"><div><p className="eyebrow">APUNTES</p><h2>Clase seleccionada</h2></div></div>
            <div className="notes-list">
              {classNotes.map((note) => (
                <article className="note-card" key={note.id}>
                  <strong>{note.title}</strong>
                  <p>{note.body}</p>
                  <small>rev. {note.revision} · {new Date(note.updatedAtEpochMs).toLocaleString('es')}</small>
                </article>
              ))}
            </div>
            <div className="composer">
              <input value={newNoteTitle} onChange={(e) => setNewNoteTitle(e.target.value)} placeholder="Título" />
              <textarea value={newNoteBody} onChange={(e) => setNewNoteBody(e.target.value)} placeholder="Escribe un apunte. Se guardará aunque no haya Internet." />
              <button className="primary" onClick={createNote} disabled={!selectedClassId || !newNoteBody.trim()}>Guardar apunte</button>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
