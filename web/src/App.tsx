import { type FormEvent, useEffect, useMemo, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { db, getDeviceId, queueUpsert, seedDemoIfEmpty } from './lib/db'
import { syncNow } from './lib/sync'
import { supabase } from './lib/supabase'
import type { ClassSessionRecord, NotePageRecord, StudyCycleRecord, SubjectRecord } from './types/sync'

type SyncLabel = { text: string; kind: 'good' | 'warn' | 'muted' }
type SaveState = 'idle' | 'saving' | 'saved'

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
    if (!editorClassId && classRows[0]) setEditorClassId(classRows[0].id)
  }

  useEffect(() => {
    void (async () => {
      await seedDemoIfEmpty()
      await refresh()
    })()
  }, [])

  useEffect(() => {
    if (!supabase) return
    void supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession))
    return () => data.subscription.unsubscribe()
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

  function openNewNote() {
    const now = Date.now()
    setEditorNoteId(crypto.randomUUID())
    setEditorClassId(classes[0]?.id ?? null)
    setEditorTitle('')
    setEditorBody('')
    setEditorCreatedAt(now)
    setEditorRevision(1)
    setSaveState('idle')
    setEditorOpen(true)
  }

  function openExistingNote(note: NotePageRecord) {
    setEditorNoteId(note.id)
    setEditorClassId(note.classSessionId)
    setEditorTitle(note.title)
    setEditorBody(note.body)
    setEditorCreatedAt(note.createdAtEpochMs)
    setEditorRevision(note.revision)
    setSaveState('saved')
    setEditorOpen(true)
  }

  useEffect(() => {
    if (!editorOpen || !editorNoteId || !editorClassId) return
    if (!editorTitle.trim() && !editorBody.trim()) {
      setSaveState('idle')
      return
    }

    setSaveState('saving')
    const timer = window.setTimeout(() => {
      void (async () => {
        const now = Date.now()
        const existing = await db.notePages.get(editorNoteId)
        const revision = existing ? Math.max(existing.revision + 1, editorRevision) : editorRevision
        const note: NotePageRecord = {
          id: editorNoteId,
          classSessionId: editorClassId,
          title: editorTitle.trim() || 'Apunte sin título',
          body: editorBody,
          createdAtEpochMs: editorCreatedAt ?? now,
          updatedAtEpochMs: now,
          revision,
          deviceId: getDeviceId(),
        }
        await db.notePages.put(note)
        await queueUpsert('note_pages', note.id, note)
        setEditorRevision(revision)
        setSaveState('saved')
        await refresh()
      })()
    }, 700)

    return () => window.clearTimeout(timer)
  }, [editorOpen, editorNoteId, editorClassId, editorTitle, editorBody])

  async function handleSync() {
    if (!session) {
      setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' })
      setAccountOpen(true)
      return
    }

    setSyncLabel({ text: 'Sincronizando…', kind: 'warn' })
    const result = await syncNow()
    if (result.state === 'synced') {
      setSyncLabel({ text: 'Sincronizado', kind: 'good' })
    } else if (result.state === 'offline') {
      setSyncLabel({ text: 'Sin Internet', kind: 'warn' })
    } else if (result.state === 'backend-not-configured') {
      setSyncLabel({ text: 'Solo local', kind: 'muted' })
    } else if (result.state === 'unauthenticated') {
      setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' })
      setAccountOpen(true)
    } else {
      setSyncLabel({ text: 'Error al sincronizar', kind: 'warn' })
    }
    await refresh()
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault()
    if (!supabase || authBusy) return
    setAuthBusy(true)
    setAuthMessage('')
    try {
      if (authMode === 'login') {
        const { error } = await supabase.auth.signInWithPassword({ email, password })
        if (error) throw error
        setAuthMessage('Sesión iniciada. Ya puedes sincronizar NotCan.')
      } else {
        const { data, error } = await supabase.auth.signUp({ email, password })
        if (error) throw error
        if (!data.session) {
          setAuthMessage('Cuenta creada. Revisa tu correo para confirmar el acceso.')
          setAuthMode('login')
        } else {
          setAuthMessage('Cuenta creada y sesión iniciada.')
        }
      }
    } catch (error) {
      setAuthMessage(error instanceof Error ? error.message : String(error))
    } finally {
      setAuthBusy(false)
    }
  }

  const saveText = saveState === 'saving' ? 'Guardando…' : saveState === 'saved' ? 'Guardado localmente' : 'Empieza a escribir'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">N</div>
          <div><strong>NotCan</strong><span>Web</span></div>
        </div>

        <div className="nav-group">
          <p>ESTUDIO</p>
          <button className="nav-item active" onClick={() => setEditorOpen(false)}><span>⌂</span>Inicio</button>
          <button className="nav-item"><span>▣</span>Materias</button>
          <button className="nav-item" onClick={() => latestNote && openExistingNote(latestNote)}><span>✎</span>Apuntes</button>
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
          <button className="nav-item" onClick={() => setAccountOpen(true)}><span>○</span>Cuenta</button>
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
              <span className={`dot ${session ? syncLabel.kind : 'muted'}`} />
              <span><strong>{session ? syncLabel.text : 'Solo local'}</strong><small>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Inicia sesión para sincronizar'}</small></span>
            </button>
            <button className="new-button" onClick={openNewNote}>＋ Nuevo</button>
            <button className="avatar-button" onClick={() => setAccountOpen(true)}>{session?.user.email?.[0]?.toUpperCase() ?? 'N'}</button>
          </div>
        </header>

        {editorOpen ? (
          <main className="note-editor-page">
            <div className="editor-toolbar">
              <button className="back-editor" onClick={() => setEditorOpen(false)}>← Inicio</button>
              <div className="editor-save-state"><span className={`dot ${saveState === 'saved' ? 'good' : saveState === 'saving' ? 'warn' : 'muted'}`} />{saveText}</div>
              <button className="editor-more">•••</button>
            </div>

            <div className="editor-sheet">
              <div className="editor-meta-row">
                <select value={editorClassId ?? ''} onChange={(e) => setEditorClassId(e.target.value)}>
                  {classes.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
                </select>
                <span>{editorCreatedAt ? new Date(editorCreatedAt).toLocaleDateString('es-EC', { day: '2-digit', month: 'long', year: 'numeric' }) : ''}</span>
              </div>
              <input className="editor-title-input" value={editorTitle} onChange={(e) => setEditorTitle(e.target.value)} placeholder="Título del apunte" autoFocus />
              <div className="format-bar" aria-label="Herramientas de formato">
                <button><strong>B</strong></button><button><em>I</em></button><button>U</button><span />
                <button>H1</button><button>H2</button><button>• Lista</button><button>☑</button><span />
                <button>↗ Enlace</button><button>✦ IA</button>
              </div>
              <textarea className="editor-body-input" value={editorBody} onChange={(e) => setEditorBody(e.target.value)} placeholder="Empieza a escribir tus apuntes…" />
              <div className="editor-footer"><span>{editorBody.trim() ? editorBody.trim().split(/\s+/).length : 0} palabras</span><span>Se guarda automáticamente en este dispositivo</span></div>
            </div>
          </main>
        ) : (
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
                    <button className="open-note" onClick={() => latestNote ? openExistingNote(latestNote) : openNewNote()}>{latestNote ? 'Abrir apunte' : 'Crear apunte'} →</button>
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
                      <article className="recent-item" key={note.id} onClick={() => openExistingNote(note)}>
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
                  <div className={`sync-check ${session ? syncLabel.kind : 'muted'}`}>{session ? '✓' : '↻'}</div>
                  <div><div className="section-title"><strong>SINCRONIZACIÓN</strong></div><h3>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Solo en este dispositivo'}</h3><p>{session ? (pending ? 'Tus cambios están guardados localmente y se enviarán al sincronizar.' : 'Tus cambios están sincronizados.') : 'Puedes estudiar sin cuenta. Inicia sesión únicamente si quieres sincronizar con Android u otros equipos.'}</p></div>
                  <button onClick={session ? handleSync : () => setAccountOpen(true)}>{session ? '↻' : 'Entrar'}</button>
                </article>

                <article className="section-card quick-card">
                  <div className="section-title"><span>✦</span><strong>ACCESOS RÁPIDOS</strong></div>
                  <button onClick={openNewNote}>✎ Nuevo apunte <span>→</span></button>
                  <button>⌘ Crear mapa conceptual <span>→</span></button>
                  <button>✦ Preguntar a NotCan AI <span>→</span></button>
                </article>
              </aside>
            </div>

            <footer className="data-footnote">{cycles.length} ciclo · {subjects.length} materias · {classes.length} clases · almacenamiento local-first</footer>
          </main>
        )}
      </div>

      {accountOpen && (
        <div className="modal-backdrop" onMouseDown={() => setAccountOpen(false)}>
          <section className="account-modal" onMouseDown={(event) => event.stopPropagation()}>
            <div className="modal-title"><div><p className="eyebrow">CUENTA NOTCAN</p><h2>{session ? 'Tu cuenta' : authMode === 'login' ? 'Sincroniza tus dispositivos' : 'Crea tu cuenta'}</h2></div><button onClick={() => setAccountOpen(false)}>×</button></div>
            {session ? (
              <div className="account-session">
                <div className="account-avatar">{session.user.email?.[0]?.toUpperCase() ?? 'N'}</div>
                <strong>{session.user.email}</strong>
                <p>Esta cuenta permite sincronizar tus materias, clases y apuntes entre NotCan Web y Android.</p>
                <button className="primary" onClick={handleSync}>↻ Sincronizar ahora</button>
                <button className="ghost" onClick={() => void supabase?.auth.signOut()}>Cerrar sesión</button>
              </div>
            ) : (
              <>
                <div className="local-note"><strong>La cuenta es opcional.</strong><p>Puedes usar NotCan y guardar apuntes sin iniciar sesión. Solo necesitas una cuenta para sincronizar entre dispositivos.</p></div>
                <form className="auth-form" onSubmit={submitAuth}>
                  <label>Correo<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
                  <label>Contraseña<input type="password" minLength={8} required value={password} onChange={(e) => setPassword(e.target.value)} /></label>
                  <button className="primary" disabled={authBusy}>{authBusy ? 'Procesando…' : authMode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</button>
                </form>
                {authMessage && <p className="auth-message">{authMessage}</p>}
                <button className="text-button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthMessage('') }}>{authMode === 'login' ? 'Crear una cuenta nueva' : 'Ya tengo una cuenta'}</button>
              </>
            )}
          </section>
        </div>
      )}
    </div>
  )
}
