import { type CSSProperties, useMemo, useState } from 'react'
import type { ClassSessionRecord, NotePageRecord, SubjectRecord } from './types/sync'

export type AcademicLevel = 'subjects' | 'classes' | 'class'

type Props = {
  cycleName?: string | null
  subjects: SubjectRecord[]
  classes: ClassSessionRecord[]
  notes: NotePageRecord[]
  level: AcademicLevel
  selectedSubjectId: string | null
  selectedClassId: string | null
  onOpenSubjects: () => void
  onOpenSubject: (subjectId: string) => void
  onOpenClass: (classId: string) => void
  onOpenNote: (note: NotePageRecord) => void
  onNewNote: (classId: string) => void
  onDeleteNote: (note: NotePageRecord) => void | Promise<void>
  onCreateSubject: (name: string) => Promise<string | null>
  onCreateClass: (subjectId: string, title: string) => Promise<string | null>
}

function formatDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(epoch))
}

function textPreview(html: string) {
  const element = document.createElement('div')
  element.innerHTML = html
  return (element.textContent || '').replace(/\s+/g, ' ').trim()
}

export default function AcademicWorkspace({
  cycleName,
  subjects,
  classes,
  notes,
  level,
  selectedSubjectId,
  selectedClassId,
  onOpenSubjects,
  onOpenSubject,
  onOpenClass,
  onOpenNote,
  onNewNote,
  onDeleteNote,
  onCreateSubject,
  onCreateClass,
}: Props) {
  const [createMode, setCreateMode] = useState<'subject' | 'class' | null>(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  const selectedSubject = subjects.find((subject) => subject.id === selectedSubjectId) ?? null
  const selectedClass = classes.find((classSession) => classSession.id === selectedClassId) ?? null
  const selectedSubjectClasses = selectedSubject ? classes.filter((classSession) => classSession.subjectId === selectedSubject.id) : []
  const selectedClassNotes = selectedClass ? notes.filter((note) => note.classSessionId === selectedClass.id) : []

  const subjectStats = useMemo(() => {
    const result = new Map<string, { classes: number; notes: number; latest: number }>()
    for (const subject of subjects) {
      const subjectClasses = classes.filter((classSession) => classSession.subjectId === subject.id)
      const ids = new Set(subjectClasses.map((classSession) => classSession.id))
      const subjectNotes = notes.filter((note) => ids.has(note.classSessionId))
      result.set(subject.id, {
        classes: subjectClasses.length,
        notes: subjectNotes.length,
        latest: Math.max(subject.updatedAtEpochMs, ...subjectClasses.map((item) => item.updatedAtEpochMs), ...subjectNotes.map((item) => item.updatedAtEpochMs)),
      })
    }
    return result
  }, [subjects, classes, notes])

  async function submitCreate(event: React.FormEvent) {
    event.preventDefault()
    const value = draft.trim()
    if (!value || busy) return
    setBusy(true)
    setMessage('')
    try {
      if (createMode === 'subject') {
        const id = await onCreateSubject(value)
        if (id) {
          setCreateMode(null)
          setDraft('')
          onOpenSubject(id)
        } else {
          setMessage('Necesitas un ciclo activo para crear una materia.')
        }
      } else if (createMode === 'class' && selectedSubject) {
        const id = await onCreateClass(selectedSubject.id, value)
        if (id) {
          setCreateMode(null)
          setDraft('')
          onOpenClass(id)
        }
      }
    } finally {
      setBusy(false)
    }
  }

  function openCreate(mode: 'subject' | 'class') {
    setDraft('')
    setMessage('')
    setCreateMode(mode)
  }

  return <section className="academic-shell">
    <nav className="academic-breadcrumb" aria-label="Ruta académica">
      <button className={level === 'subjects' ? 'current' : ''} onClick={onOpenSubjects}>Materias</button>
      {selectedSubject && <><span>›</span><button className={level === 'classes' ? 'current' : ''} onClick={() => onOpenSubject(selectedSubject.id)}>{selectedSubject.name}</button></>}
      {selectedClass && <><span>›</span><strong>{selectedClass.title}</strong></>}
    </nav>

    {level === 'subjects' && <>
      <header className="academic-head">
        <div>
          <p className="eyebrow">{cycleName ? `CICLO · ${cycleName}` : 'SIN CICLO ACTIVO'}</p>
          <h2>Materias</h2>
          <p>Selecciona una materia para ver únicamente sus clases.</p>
        </div>
        <button className="primary academic-primary" onClick={() => openCreate('subject')}>＋ Materia</button>
      </header>

      {subjects.length > 0 ? <div className="academic-subject-grid">
        {subjects.map((subject) => {
          const stats = subjectStats.get(subject.id)
          const style = { '--subject-color': subject.colorHex || '#5b82d8' } as CSSProperties
          return <button className="academic-subject-card" style={style} key={subject.id} onClick={() => onOpenSubject(subject.id)}>
            <span className="academic-subject-mark">{subject.name.trim().slice(0, 1).toUpperCase() || 'M'}</span>
            <span className="academic-card-copy">
              <strong>{subject.name}</strong>
              <small>{stats?.classes ?? 0} clases · {stats?.notes ?? 0} apuntes</small>
            </span>
            <span className="academic-card-arrow">→</span>
            <span className="academic-card-foot">{stats?.latest ? `Actualizada ${formatDate(stats.latest)}` : 'Lista para comenzar'}</span>
          </button>
        })}
        <button className="academic-add-card" onClick={() => openCreate('subject')}><span>＋</span><strong>Nueva materia</strong><small>Dentro del ciclo activo</small></button>
      </div> : <div className="academic-empty">
        <div className="academic-empty-icon">▣</div>
        <h3>Este ciclo todavía no tiene materias</h3>
        <p>Crea la primera aquí; aparecerá también en Android cuando sincronices.</p>
        <button className="primary" onClick={() => openCreate('subject')}>＋ Crear materia</button>
      </div>}
    </>}

    {level === 'classes' && selectedSubject && <>
      <header className="academic-head academic-subject-head">
        <div>
          <button className="academic-back-link" onClick={onOpenSubjects}>← Todas las materias</button>
          <p className="eyebrow">MATERIA</p>
          <h2>{selectedSubject.name}</h2>
          <p>{selectedSubjectClasses.length} {selectedSubjectClasses.length === 1 ? 'clase' : 'clases'} · {subjectStats.get(selectedSubject.id)?.notes ?? 0} apuntes</p>
        </div>
        <button className="primary academic-primary" onClick={() => openCreate('class')}>＋ Clase</button>
      </header>

      {selectedSubjectClasses.length > 0 ? <div className="academic-class-grid">
        {selectedSubjectClasses.map((classSession, index) => {
          const classNotes = notes.filter((note) => note.classSessionId === classSession.id)
          const latest = classNotes[0]
          return <button className="academic-class-card" key={classSession.id} onClick={() => onOpenClass(classSession.id)}>
            <div className="academic-class-index">{String(index + 1).padStart(2, '0')}</div>
            <div className="academic-class-copy">
              <strong>{classSession.title}</strong>
              <small>{formatDate(classSession.startedAtEpochMs)}</small>
              <p>{classNotes.length} {classNotes.length === 1 ? 'apunte' : 'apuntes'}{latest ? ` · ${latest.title}` : ''}</p>
            </div>
            <span>→</span>
          </button>
        })}
        <button className="academic-add-card compact" onClick={() => openCreate('class')}><span>＋</span><strong>Nueva clase</strong><small>{selectedSubject.name}</small></button>
      </div> : <div className="academic-empty">
        <div className="academic-empty-icon">◷</div>
        <h3>Aún no hay clases en {selectedSubject.name}</h3>
        <p>La materia está lista. Crea su primera clase para empezar a tomar apuntes.</p>
        <button className="primary" onClick={() => openCreate('class')}>＋ Crear primera clase</button>
      </div>}
    </>}

    {level === 'class' && selectedSubject && selectedClass && <>
      <header className="academic-head class-focus-head">
        <div>
          <button className="academic-back-link" onClick={() => onOpenSubject(selectedSubject.id)}>← Clases de {selectedSubject.name}</button>
          <p className="eyebrow">{selectedSubject.name.toUpperCase()}</p>
          <h2>{selectedClass.title}</h2>
          <p>{formatDate(selectedClass.startedAtEpochMs)} · {selectedClassNotes.length} {selectedClassNotes.length === 1 ? 'apunte' : 'apuntes'}</p>
        </div>
        <button className="primary academic-primary" onClick={() => onNewNote(selectedClass.id)}>＋ Apunte</button>
      </header>

      <div className="class-focus-strip"><strong>Apuntes</strong><span>{selectedClassNotes.length}</span><small>Vista principal de la clase</small></div>

      {selectedClassNotes.length > 0 ? <div className="academic-note-grid">
        {selectedClassNotes.map((note) => <article className="academic-note-card" key={note.id}>
          <button className="academic-note-open" onClick={() => onOpenNote(note)}>
            <div className="academic-note-icon">✎</div>
            <div>
              <strong>{note.title}</strong>
              <p>{textPreview(note.body).slice(0, 170) || 'Apunte sin contenido todavía.'}</p>
              <small>Editado {formatDate(note.updatedAtEpochMs)}</small>
            </div>
          </button>
          <button className="academic-note-delete" title="Eliminar apunte" aria-label={`Eliminar ${note.title}`} onClick={() => void onDeleteNote(note)}>⌫</button>
        </article>)}
        <button className="academic-add-card note-add" onClick={() => onNewNote(selectedClass.id)}><span>＋</span><strong>Nuevo apunte</strong><small>Se guardará automáticamente</small></button>
      </div> : <div className="academic-empty">
        <div className="academic-empty-icon">✎</div>
        <h3>Esta clase todavía no tiene apuntes</h3>
        <p>El primer apunte abrirá directamente el editor y quedará asociado a esta clase.</p>
        <button className="primary" onClick={() => onNewNote(selectedClass.id)}>＋ Crear apunte</button>
      </div>}
    </>}

    {message && <p className="academic-message">{message}</p>}

    {createMode && <div className="academic-dialog-backdrop" onMouseDown={() => !busy && setCreateMode(null)}>
      <form className="academic-dialog" onSubmit={submitCreate} onMouseDown={(event) => event.stopPropagation()}>
        <div><p className="eyebrow">{createMode === 'subject' ? 'CICLO ACADÉMICO' : selectedSubject?.name.toUpperCase()}</p><h3>{createMode === 'subject' ? 'Nueva materia' : 'Nueva clase'}</h3></div>
        <label><span>{createMode === 'subject' ? 'Nombre de la materia' : 'Título de la clase'}</span><input autoFocus value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={createMode === 'subject' ? 'Ej. Cristología' : 'Ej. Clase 12 · Docetismo'} /></label>
        <div className="academic-dialog-actions"><button type="button" onClick={() => setCreateMode(null)}>Cancelar</button><button className="primary" disabled={!draft.trim() || busy}>{busy ? 'Creando…' : 'Crear'}</button></div>
      </form>
    </div>}
  </section>
}
