import { useEffect, useMemo, useState } from 'react'
import { db, getDeviceId, queueUpsert, seedDemoIfEmpty } from './lib/db'
import { syncNow } from './lib/sync'
import type { ClassSessionRecord, NotePageRecord, StudyCycleRecord, SubjectRecord } from './types/sync'

type SyncLabel = { text: string; kind: 'good' | 'warn' | 'muted' }

const subjectIcons = ['⌘', '✦', '⚖', '♡', '✚', '◈']
const subjectAccents = ['blue', 'violet', 'teal', 'gold', 'rose', 'indigo']

function shortDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { day: '2-digit', month: 'short' }).format(new Date(epoch))
}

function shortTime(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { hour: '2-digit', minute: '2-digit' }).format(new Date(epoch))
}

export default function App() {
  const [cycles, setCycles] = useState<StudyCycleRecord[]>([])
  const [subjects, setSubjects] = useState<SubjectRecord[]>([])
  const [classes, setClasses] = useState<ClassSessionRecord[]>([])
  const [notes, setNotes] = useState<NotePageRecord[]>([])
  const [pending, setPending] = useState(0)
  const [syncLabel, setSyncLabel] = useState<SyncLabel>({ text: 'Solo local', kind: 'muted' })
  const [composerOpen, setComposerOpen] = useState(false)
  const [newNoteTitle, setNewNoteTitle] = useState('')
  const [newNoteBody, setNewNoteBody] = useState('')
  const [selectedClassId, setSelectedClassId] = useState<string | null>(null)

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
    if (!selectedClassId && classRows[0]) setSelectedClassId(classRows[0].id)
  }

  useEffect(() => {
    void (async () => {
      await seedDemoIfEmpty()
      await refresh()
    })()
  }, [])

  const recentNotes = notes.slice(0, 3)
  const latestNote = notes[0] ?? null
  const latestClass = latestNote
    ? classes.find((item) => item.id === latestNote.classSessionId) ?? classes[0] ?? null
    : classes[0] ?? null
  const featuredSubject = latestClass
    ? subjects.find((item) => item.id === latestClass.subjectId) ?? subjects[0] ?? null
    : subjects[0] ?? null

  const subjectCards = useMemo(
    () => subjects.slice(0, 4).map((subject, index) => ({
      subject,
      index,
      classCount: classes.filter((item) => item.subjectId === subject.id).length,
      noteCount: notes.filter((note) => {
        const classSession = classes.find((item) => item.id === note.classSessionId)
        return classSession?.subjectId === subject.id
      }).length,
    })),
    [subjects, classes, notes],
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
    setComposerOpen(false)
    await refresh()
  }

  async function handleSync() {
    setSyncLabel({ text: 'Sincronizando…', kind: 'warn' })
    const result = await syncNow()
    if (result.state === 'synced') {
      setSyncLabel({ text: 'Sincronizado', kind: 'good' })
    } else if (result.state === 'offline') {
      setSyncLabel({ text: 'Sin Internet', kind: 'warn' })
    } else if (result.state === 'backend-not-configured') {
      setSyncLabel({ text: 'Solo local', kind: 'muted' })
    } else if (result.state === 'unauthenticated') {
      setSyncLabel({ text: 'Falta iniciar sesión', kind: 'warn' })
    } else {
      setSyncLabel({ text: 'Error al sincronizar', kind: 'warn' })
    }
    await refresh()
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">N</div>
          <div><strong>NotCan</strong><span>Web</span></div>
        </div>

        <div className="nav-group">
          <p>ESTUDIO</p>
          <button className="nav-item active"><span>⌂</span>Inicio</button>
          <button className="nav-item"><span>▣</span>Materias</button>
          <button className="nav-item"><span>✎</span>Apuntes</button>
          <button className="nav-item"><span>▥</span>Biblioteca</button>
        </div>

        <div className="nav-group">
          <p>ORGANIZACIÓN</p>
          <button className="nav-item"><span>□</span>Calendario</button>
          <button className="nav-item"><span>☑</span>Tareas</button>
          <button className="nav-item"><span>☆</span>Calificaciones</button>
        </div>

        <div className="nav-group">
          <p>HERRAMIENTAS</p>
          <button className="nav-item"><span>⌘</span>Mapas</button>
          <button className="nav-item ai-nav"><span>✦</span>NotCan AI</button>
        </div>

        <div className="sidebar-bottom">
          <button className="nav-item" onClick={handleSync}><span>↻</span>Sincronización <i className={`dot ${syncLabel.kind}`} /></button>
          <button className="nav-item"><span>⚙</span>Ajustes</button>
          <button className="nav-item"><span>○</span>Cuenta</button>
        </div>
      </aside>

      <div className="page-area">
        <header className="global-topbar">
          <label className="search-box">
            <span>⌕</span>
            <input placeholder="Buscar en NotCan…" />
            <kbd>Ctrl K</kbd>
          </label>
          <div className="topbar-actions">
            <button className="sync-status" onClick={handleSync}>
              <span className={`dot ${syncLabel.kind}`} />
              <span><strong>{syncLabel.text}</strong><small>{pending ? `${pending} cambios pendientes` : 'Todo al día'}</small></span>
            </button>
            <button className="new-button" onClick={() => setComposerOpen(true)}>＋ Nuevo</button>
            <button className="avatar-button">N</button>
          </div>
        </header>

        <main className="main-area">
          <section className="welcome">
            <div className="sparkle">✦</div>
            <div>
              <h1>Tu estudio, en cualquier dispositivo</h1>
              <p>Organiza tus ideas. Profundiza tu conocimiento. Todo en un mismo espacio.</p>
            </div>
          </section>

          <div className="dashboard-grid">
            <section className="content-column">
              <article className="section-card continue-card-wrap">
                <div className="section-title"><span>▣</span><strong>CONTINUAR ESTUDIANDO</strong></div>
                <div className="continue-card">
                  <div className="continue-copy">
                    <div className="subject-symbol">✦</div>
                    <h2>{featuredSubject?.name ?? 'Tu próxima materia'}</h2>
                    <div className="progress-row"><span>{latestNote ? 'Último apunte disponible' : 'Comienza tu primer apunte'}</span><div className="progress"><i /></div></div>
                    <p>Último apunte</p>
                    <strong className="latest-title">{latestNote?.title ?? 'Aún no hay apuntes recientes'}</strong>
                    <small>{latestNote ? `Editado hoy, ${shortTime(latestNote.updatedAtEpochMs)}` : 'Crea uno para comenzar'}</small>
                  </div>
                  <div className="hero-art" aria-hidden="true"><span>✦</span><span>✧</span><span>✦</span></div>
                  <button className="open-note" onClick={() => setComposerOpen(true)}>{latestNote ? 'Abrir apunte' : 'Crear apunte'} →</button>
                </div>
              </article>

              <article className="section-card subjects-section">
                <div className="section-heading-row"><div className="section-title"><span>▣</span><strong>MATERIAS</strong></div><button>Ver todas</button></div>
                <div className="subject-grid">
                  {subjectCards.map(({ subject, index, classCount, noteCount }) => (
                    <article className="course-card" key={subject.id}>
                      <div className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>{subjectIcons[index % subjectIcons.length]}</div>
                      <div className="course-copy"><strong>{subject.name}</strong><small>{classCount} clases · {noteCount} apuntes</small></div>
                      <button className="more">•••</button>
                      <div className="course-progress"><i style={{ width: `${Math.min(90, 28 + classCount * 12)}%` }} /></div>
                    </article>
                  ))}
                  {subjectCards.length === 0 && <p className="empty-state">Tus materias aparecerán aquí.</p>}
                </div>
              </article>

              <article className="section-card recent-section">
                <div className="section-heading-row"><div className="section-title"><span>◷</span><strong>RECIENTES</strong></div><button>Ver todos los recientes</button></div>
                <div className="recent-grid">
                  {recentNotes.map((note, index) => (
                    <article className="recent-item" key={note.id}>
                      <div className={`file-icon ${subjectAccents[index % subjectAccents.length]}`}>{index === 2 ? '⌘' : '≡'}</div>
                      <div><strong>{note.title}</strong><span>Apunte de clase</span><small>Editado {shortDate(note.updatedAtEpochMs)}, {shortTime(note.updatedAtEpochMs)}</small></div>
                      <button>☆</button>
                    </article>
                  ))}
                  {recentNotes.length === 0 && <p className="empty-state">Tus documentos y apuntes recientes aparecerán aquí.</p>}
                </div>
              </article>
            </section>

            <aside className="right-column">
              <article className="section-card tasks-card">
                <div className="section-heading-row"><div className="section-title"><span>□</span><strong>PRÓXIMAS TAREAS / EXÁMENES</strong></div><button>Ver todas</button></div>
                <div className="task-list">
                  <div className="task-row"><span className="task-icon blue">▤</span><div><strong>Próxima entrega</strong><small>{featuredSubject?.name ?? 'Materia'}</small></div><time>Mañana<small>23:59</small></time></div>
                  <div className="task-row"><span className="task-icon violet">▤</span><div><strong>Revisar apuntes</strong><small>Sesión de estudio</small></div><time>Esta semana<small>18:00</small></time></div>
                  <div className="task-row"><span className="task-icon rose">▤</span><div><strong>Preparar evaluación</strong><small>Calendario académico</small></div><time>Próximo<small>09:00</small></time></div>
                </div>
              </article>

              <article className="section-card sync-panel">
                <div className={`sync-check ${syncLabel.kind}`}>✓</div>
                <div><div className="section-title"><strong>SINCRONIZACIÓN</strong></div><h3>{pending ? `${pending} cambios pendientes` : 'Todo al día'}</h3><p>{pending ? 'Tus cambios están guardados localmente y se enviarán al sincronizar.' : 'Tus cambios están sincronizados en este dispositivo.'}</p></div>
                <button onClick={handleSync}>↻</button>
              </article>

              <article className="section-card quick-card">
                <div className="section-title"><span>✦</span><strong>ACCESOS RÁPIDOS</strong></div>
                <button onClick={() => setComposerOpen(true)}>✎ Nuevo apunte <span>→</span></button>
                <button>⌘ Crear mapa conceptual <span>→</span></button>
                <button>✦ Preguntar a NotCan AI <span>→</span></button>
              </article>
            </aside>
          </div>

          <footer className="data-footnote">{cycles.length} ciclo · {subjects.length} materias · {classes.length} clases · almacenamiento local-first</footer>
        </main>
      </div>

      {composerOpen && (
        <div className="modal-backdrop" onMouseDown={() => setComposerOpen(false)}>
          <section className="note-modal" onMouseDown={(event) => event.stopPropagation()}>
            <div className="modal-title"><div><p className="eyebrow">NUEVO APUNTE</p><h2>Guarda una idea sin perder el ritmo</h2></div><button onClick={() => setComposerOpen(false)}>×</button></div>
            <label>Clase<select value={selectedClassId ?? ''} onChange={(e) => setSelectedClassId(e.target.value)}>{classes.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label>
            <label>Título<input value={newNoteTitle} onChange={(e) => setNewNoteTitle(e.target.value)} placeholder="Título del apunte" /></label>
            <label>Contenido<textarea value={newNoteBody} onChange={(e) => setNewNoteBody(e.target.value)} placeholder="Escribe aquí…" /></label>
            <div className="modal-actions"><button className="ghost" onClick={() => setComposerOpen(false)}>Cancelar</button><button className="primary" onClick={createNote} disabled={!selectedClassId || !newNoteBody.trim()}>Guardar apunte</button></div>
          </section>
        </div>
      )}
    </div>
  )
}
