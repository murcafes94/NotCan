import {
  type ChangeEvent,
  type FormEvent,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type WheelEvent as ReactWheelEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { Session } from '@supabase/supabase-js'
import { askNotCanAi, type AiContextItem } from './lib/ai'
import { db, getDeviceId, queueDelete, queueUpsert, seedDemoIfEmpty } from './lib/db'
import { syncNow } from './lib/sync'
import { supabase } from './lib/supabase'
import CycleManagementPanel from './CycleManagementPanel'
import type {
  ClassSessionRecord,
  GradeItemRecord,
  NotePageRecord,
  StudyCycleRecord,
  SubjectRecord,
} from './types/sync'

type SyncLabel = { text: string; kind: 'good' | 'warn' | 'muted' }
type SaveState = 'idle' | 'saving' | 'saved'
type Page = 'home' | 'subjects' | 'notes' | 'library' | 'calendar' | 'tasks' | 'grades' | 'maps' | 'ai' | 'sync' | 'settings' | 'account'
type LocalTask = { id: string; title: string; detail: string; done: boolean }
type LocalFile = { id: string; name: string; size: number; type: string; addedAt: number }
type AiMode = 'chat' | 'summary' | 'questions' | 'concept-map'

const subjectIcons = ['⌘', '✦', '⚖', '♡', '✚', '◈']
const subjectAccents = ['blue', 'violet', 'teal', 'gold', 'rose', 'indigo']
const APP_URL = `${window.location.origin}${window.location.pathname}`

function shortDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { day: '2-digit', month: 'short' }).format(new Date(epoch))
}

function shortTime(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', { hour: '2-digit', minute: '2-digit' }).format(new Date(epoch))
}

function fullDate(epoch: number) {
  return new Intl.DateTimeFormat('es-EC', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(epoch))
}

function escapeHtml(text: string) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function normalizeBodyForEditor(body: string) {
  const trimmed = body.trim()
  if (!trimmed) return ''
  if (/^<(p|div|h1|h2|ul|ol|blockquote|br|strong|b|em|i|u)\b/i.test(trimmed)) return body
  return escapeHtml(body).replaceAll('\n', '<br>')
}

function stripHtml(html: string) {
  const div = document.createElement('div')
  div.innerHTML = html
  return (div.textContent || div.innerText || '').replace(/\s+/g, ' ').trim()
}

function sanitizeEditorHtml(html: string) {
  const template = document.createElement('template')
  template.innerHTML = html
  const allowed = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'H1', 'H2', 'UL', 'OL', 'LI', 'A', 'BLOCKQUOTE'])

  function clean(node: Node) {
    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        const element = child as HTMLElement
        if (!allowed.has(element.tagName)) {
          const fragment = document.createDocumentFragment()
          while (element.firstChild) fragment.appendChild(element.firstChild)
          element.replaceWith(fragment)
          clean(node)
          continue
        }

        for (const attribute of Array.from(element.attributes)) {
          const keepHref = element.tagName === 'A' && attribute.name.toLowerCase() === 'href'
          if (!keepHref) element.removeAttribute(attribute.name)
        }

        if (element.tagName === 'A') {
          const href = element.getAttribute('href') || ''
          if (!/^https?:\/\//i.test(href) && !/^mailto:/i.test(href)) element.removeAttribute('href')
          element.setAttribute('target', '_blank')
          element.setAttribute('rel', 'noopener noreferrer')
        }
        clean(element)
      }
    }
  }

  clean(template.content)
  return template.innerHTML
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
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null)
  const [theme, setTheme] = useState<'dark' | 'light'>(() => (localStorage.getItem('notcan-theme') === 'light' ? 'light' : 'dark'))

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
  const [formatHint, setFormatHint] = useState('')
  const editorRef = useRef<HTMLDivElement>(null)
  const loadedEditorIdRef = useRef<string | null>(null)

  const [localTasks, setLocalTasks] = useState<LocalTask[]>(() => {
    try { return JSON.parse(localStorage.getItem('notcan-tasks') || '[]') } catch { return [] }
  })
  const [localFiles, setLocalFiles] = useState<LocalFile[]>(() => {
    try { return JSON.parse(localStorage.getItem('notcan-files-meta') || '[]') } catch { return [] }
  })
  const [mapTitle, setMapTitle] = useState('Mi mapa conceptual')
  const [mapNodes, setMapNodes] = useState(['Idea central', 'Concepto 1', 'Concepto 2'])
  const [mapNodeDraft, setMapNodeDraft] = useState('')
  const [mapZoom, setMapZoom] = useState(1)
  const [mapPan, setMapPan] = useState({ x: 0, y: 0 })
  const mapDragRef = useRef<{ pointerId: number; startX: number; startY: number; panX: number; panY: number } | null>(null)

  const [aiPrompt, setAiPrompt] = useState('')
  const [aiAnswer, setAiAnswer] = useState('')
  const [aiBusy, setAiBusy] = useState(false)
  const [aiError, setAiError] = useState('')
  const [aiMode, setAiMode] = useState<AiMode>('chat')
  const [aiUseNotes, setAiUseNotes] = useState(true)
  const [aiModel, setAiModel] = useState('')

  const [gradeDraft, setGradeDraft] = useState({ title: '', score: '', maxScore: '100', weightPercent: '' })
  const [gradeTarget, setGradeTarget] = useState(80)
  const [gradeMessage, setGradeMessage] = useState('')

  const fileInputRef = useRef<HTMLInputElement>(null)

  async function refresh() {
    const [cycleRows, subjectRows, classRows, noteRows, gradeRows, outboxCount] = await Promise.all([
      db.studyCycles.toArray(),
      db.subjects.toArray(),
      db.classSessions.orderBy('startedAtEpochMs').reverse().toArray(),
      db.notePages.orderBy('updatedAtEpochMs').reverse().toArray(),
      db.gradeItems.toArray(),
      db.outbox.count(),
    ])

    setCycles(cycleRows)
    setSubjects(subjectRows)
    setClasses(classRows)
    setNotes(noteRows)
    setGrades(gradeRows)
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
    localStorage.setItem('notcan-tasks', JSON.stringify(localTasks))
  }, [localTasks])

  useEffect(() => {
    localStorage.setItem('notcan-files-meta', JSON.stringify(localFiles))
  }, [localFiles])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('notcan-theme', theme)
  }, [theme])

  useEffect(() => {
    if (page !== 'home') setSidebarOpen(false)
  }, [page])

  useEffect(() => {
    if (!supabase) return
    void supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession))
    return () => data.subscription.unsubscribe()
  }, [])

  const recentNotes = notes.slice(0, 4)
  const latestNote = notes[0] ?? null
  const latestClass = latestNote
    ? classes.find((item) => item.id === latestNote.classSessionId) ?? classes[0] ?? null
    : classes[0] ?? null
  const featuredSubject = latestClass
    ? subjects.find((item) => item.id === latestClass.subjectId) ?? subjects[0] ?? null
    : subjects[0] ?? null

  const activeCycle = cycles.find((cycle) => cycle.isActive) ?? cycles[0] ?? null
  const activeSubjects = useMemo(() => activeCycle ? subjects.filter((subject) => subject.cycleId === activeCycle.id) : subjects, [subjects, activeCycle?.id])
  const subjectCards = useMemo(() => activeSubjects.slice(0, 4).map((subject, index) => ({
    subject,
    index,
    classCount: classes.filter((item) => item.subjectId === subject.id).length,
    noteCount: notes.filter((note) => classes.find((c) => c.id === note.classSessionId)?.subjectId === subject.id).length,
  })), [activeSubjects, classes, notes])

  const selectedSubject = activeSubjects.find((s) => s.id === selectedSubjectId) ?? null
  const selectedSubjectClasses = classes.filter((c) => c.subjectId === selectedSubject?.id)
  const selectedGrades = grades.filter((grade) => grade.subjectId === selectedSubject?.id)
  const todayKey = new Date().toDateString()
  const todayClasses = classes.filter((classSession) => new Date(classSession.startedAtEpochMs).toDateString() === todayKey)
  const upcomingClasses = classes
    .filter((classSession) => classSession.startedAtEpochMs >= Date.now())
    .sort((a, b) => a.startedAtEpochMs - b.startedAtEpochMs)

  const gradeStats = useMemo(() => {
    const evaluatedWeight = selectedGrades.reduce((sum, grade) => sum + Math.max(0, grade.weightPercent), 0)
    const contribution = selectedGrades.reduce((sum, grade) => {
      const ratio = grade.maxScore > 0 ? grade.score / grade.maxScore : 0
      return sum + ratio * Math.max(0, grade.weightPercent)
    }, 0)
    const average = evaluatedWeight > 0 ? (contribution / evaluatedWeight) * 100 : 0
    const remainingWeight = Math.max(0, 100 - evaluatedWeight)
    const required = remainingWeight > 0 ? ((gradeTarget - contribution) / remainingWeight) * 100 : null
    const projected70 = Math.min(100, contribution + remainingWeight * 0.70)
    const projected85 = Math.min(100, contribution + remainingWeight * 0.85)
    const projected100 = Math.min(100, contribution + remainingWeight)
    const risk = selectedGrades.length === 0 ? 'Sin datos' : average >= 80 ? 'Estable' : average >= 70 ? 'Atención' : 'Riesgo'
    return { evaluatedWeight, contribution, average, remainingWeight, required, projected70, projected85, projected100, risk }
  }, [selectedGrades, gradeTarget])

  const searchResults = search.trim() ? [
    ...subjects
      .filter((s) => s.name.toLowerCase().includes(search.toLowerCase()))
      .map((s) => ({ type: 'Materia', title: s.name, action: () => { setSelectedSubjectId(s.id); navigate('subjects') } })),
    ...notes
      .filter((n) => `${n.title} ${stripHtml(n.body)}`.toLowerCase().includes(search.toLowerCase()))
      .slice(0, 6)
      .map((n) => ({ type: 'Apunte', title: n.title, action: () => openExistingNote(n) })),
  ] : []

  function navigate(next: Page) {
    setEditorOpen(false)
    setPage(next)
    setSidebarOpen(false)
    setSearch('')
  }

  function openNewNote(classId?: string) {
    const now = Date.now()
    const id = crypto.randomUUID()
    loadedEditorIdRef.current = null
    setEditorNoteId(id)
    setEditorClassId(classId ?? classes[0]?.id ?? null)
    setEditorTitle('')
    setEditorBody('')
    setEditorCreatedAt(now)
    setEditorRevision(1)
    setSaveState('idle')
    setEditorOpen(true)
  }

  function openExistingNote(note: NotePageRecord) {
    loadedEditorIdRef.current = null
    setEditorNoteId(note.id)
    setEditorClassId(note.classSessionId)
    setEditorTitle(note.title)
    setEditorBody(normalizeBodyForEditor(note.body))
    setEditorCreatedAt(note.createdAtEpochMs)
    setEditorRevision(note.revision)
    setSaveState('saved')
    setEditorOpen(true)
  }

  useEffect(() => {
    if (!editorOpen || !editorNoteId || !editorRef.current) return
    if (loadedEditorIdRef.current === editorNoteId) return
    editorRef.current.innerHTML = editorBody
    loadedEditorIdRef.current = editorNoteId
  }, [editorOpen, editorNoteId])

  useEffect(() => {
    if (!editorOpen || !editorNoteId || !editorClassId) return
    if (!editorTitle.trim() && !stripHtml(editorBody)) {
      setSaveState('idle')
      return
    }

    setSaveState('saving')
    const timer = window.setTimeout(() => {
      void (async () => {
        const now = Date.now()
        const existing = await db.notePages.get(editorNoteId)
        const revision = existing ? Math.max(existing.revision + 1, editorRevision) : editorRevision
        const cleanBody = sanitizeEditorHtml(editorBody)
        const note: NotePageRecord = {
          id: editorNoteId,
          classSessionId: editorClassId,
          title: editorTitle.trim() || 'Apunte sin título',
          body: cleanBody,
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
    }, 650)

    return () => window.clearTimeout(timer)
  }, [editorOpen, editorNoteId, editorClassId, editorTitle, editorBody])

  async function deleteNote(note: NotePageRecord | null) {
    if (!note) return
    const confirmed = window.confirm(`¿Eliminar el apunte “${note.title}”?\n\nSe eliminará también de los otros dispositivos cuando sincronices.`)
    if (!confirmed) return

    await db.notePages.delete(note.id)
    await queueDelete('note_pages', note.id)
    if (editorNoteId === note.id) {
      setEditorOpen(false)
      setEditorNoteId(null)
      setEditorBody('')
    }
    await refresh()
  }

  async function deleteCurrentNote() {
    if (!editorNoteId) return
    const existing = await db.notePages.get(editorNoteId)
    if (existing) {
      await deleteNote(existing)
      return
    }
    if (!editorTitle.trim() && !stripHtml(editorBody)) {
      setEditorOpen(false)
      return
    }
    const confirmed = window.confirm('¿Descartar este apunte nuevo?')
    if (confirmed) setEditorOpen(false)
  }

  function preserveSelection(event: ReactMouseEvent<HTMLButtonElement>) {
    event.preventDefault()
  }

  function applySelectionCommand(command: 'bold' | 'italic' | 'underline' | 'formatBlock' | 'insertUnorderedList' | 'insertOrderedList', value?: string) {
    const editor = editorRef.current
    const selection = window.getSelection()
    if (!editor || !selection || selection.rangeCount === 0 || selection.isCollapsed || !editor.contains(selection.anchorNode)) {
      setFormatHint('Selecciona primero el texto que quieres formatear.')
      window.setTimeout(() => setFormatHint(''), 1800)
      return
    }

    document.execCommand(command, false, value)

    // El formato inline se aplica solo a la selección. Al colapsar el cursor,
    // desactivamos el estado de escritura para evitar que el subrayado/negrita/cursiva
    // continúen en el texto que se escriba después.
    if (command === 'bold' || command === 'italic' || command === 'underline') {
      const currentSelection = window.getSelection()
      if (currentSelection?.rangeCount) {
        const range = currentSelection.getRangeAt(0)
        range.collapse(false)
        currentSelection.removeAllRanges()
        currentSelection.addRange(range)
        if (document.queryCommandState(command)) document.execCommand(command, false)
      }
    }

    setEditorBody(editor.innerHTML)
    editor.focus()
  }

  function createLinkForSelection() {
    const editor = editorRef.current
    const selection = window.getSelection()
    if (!editor || !selection || selection.rangeCount === 0 || selection.isCollapsed || !editor.contains(selection.anchorNode)) {
      setFormatHint('Selecciona primero el texto que llevará el enlace.')
      window.setTimeout(() => setFormatHint(''), 1800)
      return
    }
    const url = window.prompt('Dirección del enlace (https://...)')?.trim()
    if (!url) return
    document.execCommand('createLink', false, url)
    setEditorBody(editor.innerHTML)
    editor.focus()
  }

  async function handleSync() {
    if (!session) {
      setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' })
      setAccountOpen(true)
      return
    }

    setSyncLabel({ text: 'Sincronizando…', kind: 'warn' })
    const result = await syncNow()
    if (result.state === 'synced') setSyncLabel({ text: 'Sincronizado', kind: 'good' })
    else if (result.state === 'offline') setSyncLabel({ text: 'Sin Internet', kind: 'warn' })
    else if (result.state === 'unauthenticated') {
      setSyncLabel({ text: 'Inicia sesión para sincronizar', kind: 'warn' })
      setAccountOpen(true)
    } else {
      setSyncLabel({
        text: result.state === 'backend-not-configured' ? 'Solo local' : 'Error al sincronizar',
        kind: result.state === 'backend-not-configured' ? 'muted' : 'warn',
      })
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
        setAuthMessage('Sesión iniciada. Ya puedes sincronizar NotCan y usar NotCan AI.')
      } else {
        const { data, error } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: APP_URL },
        })
        if (error) throw error
        setAuthMessage(data.session
          ? 'Cuenta creada y sesión iniciada.'
          : 'Cuenta creada. Revisa tu correo para confirmar el acceso.')
        if (!data.session) setAuthMode('login')
      }
    } catch (error) {
      setAuthMessage(error instanceof Error ? error.message : String(error))
    } finally {
      setAuthBusy(false)
    }
  }

  function importFiles(event: ChangeEvent<HTMLInputElement>) {
    const chosen = Array.from(event.target.files ?? []).map((file) => ({
      id: crypto.randomUUID(),
      name: file.name,
      size: file.size,
      type: file.type || 'archivo',
      addedAt: Date.now(),
    }))
    setLocalFiles((prev) => [...chosen, ...prev])
    event.target.value = ''
  }

  async function addGrade(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault()
    if (!selectedSubject) return

    const score = Number(gradeDraft.score)
    const maxScore = Number(gradeDraft.maxScore)
    const weightPercent = Number(gradeDraft.weightPercent)
    const title = gradeDraft.title.trim()

    if (!title || !Number.isFinite(score) || !Number.isFinite(maxScore) || !Number.isFinite(weightPercent)) {
      setGradeMessage('Completa el nombre, la nota obtenida, la nota máxima y el peso.')
      return
    }
    if (maxScore <= 0 || weightPercent <= 0 || weightPercent > 100 || score < 0) {
      setGradeMessage('Revisa los valores: máximo y peso deben ser mayores que 0.')
      return
    }
    if (score > maxScore) {
      setGradeMessage('La nota obtenida no puede superar la nota máxima.')
      return
    }
    if (gradeStats.evaluatedWeight + weightPercent > 100.0001) {
      setGradeMessage(`El peso total superaría 100%. Te quedan ${gradeStats.remainingWeight.toFixed(1)}% por asignar.`)
      return
    }

    const now = Date.now()
    const item: GradeItemRecord = {
      id: crypto.randomUUID(),
      subjectId: selectedSubject.id,
      title,
      score,
      maxScore,
      weightPercent,
      createdAtEpochMs: now,
      updatedAtEpochMs: now,
      revision: 1,
      deviceId: getDeviceId(),
    }
    await db.gradeItems.add(item)
    await queueUpsert('grade_items', item.id, item)
    setGradeDraft({ title: '', score: '', maxScore: '100', weightPercent: '' })
    setGradeMessage('Calificación guardada localmente. Se sincronizará con tu cuenta.')
    await refresh()
  }

  async function deleteGrade(grade: GradeItemRecord) {
    if (!window.confirm(`¿Eliminar “${grade.title}”?`)) return
    await db.gradeItems.delete(grade.id)
    await queueDelete('grade_items', grade.id)
    setGradeMessage('Calificación eliminada.')
    await refresh()
  }

  function noteContext(limit = 8): AiContextItem[] {
    return notes.slice(0, limit).map((note) => {
      const classSession = classes.find((item) => item.id === note.classSessionId)
      const subject = subjects.find((item) => item.id === classSession?.subjectId)
      return {
        title: note.title,
        body: stripHtml(note.body),
        subject: subject?.name,
        classTitle: classSession?.title,
      }
    })
  }

  async function runAi(mode: AiMode = aiMode, promptOverride?: string) {
    const prompt = (promptOverride ?? aiPrompt).trim()
    if (!prompt || aiBusy) return
    if (!session) {
      setAiError('Inicia sesión para usar NotCan AI.')
      setAccountOpen(true)
      return
    }

    setAiBusy(true)
    setAiError('')
    setAiAnswer('')
    setAiMode(mode)
    try {
      const response = await askNotCanAi({
        prompt,
        mode,
        context: aiUseNotes ? noteContext() : [],
      })
      setAiAnswer(response.answer)
      setAiModel(response.model ?? '')
    } catch (error) {
      setAiError(error instanceof Error ? error.message : String(error))
    } finally {
      setAiBusy(false)
    }
  }

  function askAiAboutCurrentNote() {
    const text = stripHtml(editorBody)
    setAiPrompt(text
      ? `Ayúdame a estudiar este apunte. Explícame sus ideas principales, señala relaciones importantes y dime qué debería memorizar:\n\n${text.slice(0, 6000)}`
      : 'Ayúdame a preparar este tema para estudiar.')
    setAiMode('chat')
    setEditorOpen(false)
    setPage('ai')
  }

  function addMapNode() {
    const value = mapNodeDraft.trim()
    if (!value) return
    setMapNodes((prev) => [...prev, value])
    setMapNodeDraft('')
  }

  function handleMapPointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    const target = event.target as HTMLElement
    if (target.closest('button,input,textarea')) return
    mapDragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      panX: mapPan.x,
      panY: mapPan.y,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function handleMapPointerMove(event: ReactPointerEvent<HTMLDivElement>) {
    const drag = mapDragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return
    setMapPan({
      x: drag.panX + event.clientX - drag.startX,
      y: drag.panY + event.clientY - drag.startY,
    })
  }

  function handleMapPointerEnd(event: ReactPointerEvent<HTMLDivElement>) {
    if (mapDragRef.current?.pointerId === event.pointerId) mapDragRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
  }

  function handleMapWheel(event: ReactWheelEvent<HTMLDivElement>) {
    event.preventDefault()
    const next = mapZoom * (event.deltaY > 0 ? 0.9 : 1.1)
    setMapZoom(Math.min(2.8, Math.max(0.45, next)))
  }

  function resetMapView() {
    setMapZoom(1)
    setMapPan({ x: 0, y: 0 })
  }

  const navGroups: { label: string; items: { page: Page; icon: string; label: string }[] }[] = [
    { label: 'ESTUDIO', items: [
      { page: 'home', icon: '⌂', label: 'Inicio' },
      { page: 'subjects', icon: '▣', label: 'Materias' },
      { page: 'notes', icon: '✎', label: 'Apuntes' },
      { page: 'library', icon: '▥', label: 'Biblioteca' },
    ] },
    { label: 'ORGANIZACIÓN', items: [
      { page: 'calendar', icon: '□', label: 'Calendario' },
      { page: 'tasks', icon: '☑', label: 'Tareas' },
      { page: 'grades', icon: '☆', label: 'Calificaciones' },
    ] },
    { label: 'HERRAMIENTAS', items: [
      { page: 'maps', icon: '⌘', label: 'Mapas' },
      { page: 'ai', icon: '✦', label: 'NotCan AI' },
    ] },
  ]

  const saveText = saveState === 'saving'
    ? 'Guardando…'
    : saveState === 'saved'
      ? 'Guardado localmente'
      : 'Empieza a escribir'

  function renderHome() {
    return <main className="main-area">
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
                <div className="progress-row">
                  <span>{latestNote ? 'Último apunte disponible' : 'Comienza tu primer apunte'}</span>
                  <div className="progress"><i /></div>
                </div>
                <p>Último apunte</p>
                <strong className="latest-title">{latestNote?.title ?? 'Aún no hay apuntes recientes'}</strong>
                <small>{latestNote ? `Editado hoy, ${shortTime(latestNote.updatedAtEpochMs)}` : 'Crea uno para comenzar'}</small>
              </div>
              <div className="hero-art" aria-hidden="true"><span>✦</span><span>✧</span><span>✦</span></div>
              <button className="open-note" onClick={() => latestNote ? openExistingNote(latestNote) : openNewNote()}>
                {latestNote ? 'Abrir apunte' : 'Crear apunte'} →
              </button>
            </div>
          </article>

          <article className="section-card subjects-section">
            <div className="section-heading-row">
              <div className="section-title"><span>▣</span><strong>MATERIAS</strong></div>
              <button onClick={() => navigate('subjects')}>Ver todas</button>
            </div>
            <div className="subject-grid">
              {subjectCards.map(({ subject, index, classCount, noteCount }) => <button
                className="course-card clickable-card"
                key={subject.id}
                onClick={() => { setSelectedSubjectId(subject.id); navigate('subjects') }}
              >
                <div className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>{subjectIcons[index % subjectIcons.length]}</div>
                <div className="course-copy"><strong>{subject.name}</strong><small>{classCount} clases · {noteCount} apuntes</small></div>
                <span className="more">•••</span>
                <div className="course-progress"><i style={{ width: `${Math.min(90, 28 + classCount * 12)}%` }} /></div>
              </button>)}
            </div>
          </article>

          <article className="section-card recent-section">
            <div className="section-heading-row">
              <div className="section-title"><span>◷</span><strong>RECIENTES</strong></div>
              <button onClick={() => navigate('notes')}>Ver todos los recientes</button>
            </div>
            <div className="recent-grid">
              {recentNotes.map((note, index) => <article className="recent-item recent-note-item" key={note.id}>
                <button className="recent-open" onClick={() => openExistingNote(note)}>
                  <div className={`file-icon ${subjectAccents[index % subjectAccents.length]}`}>≡</div>
                  <div><strong>{note.title}</strong><span>Apunte de clase</span><small>{stripHtml(note.body).slice(0, 58) || 'Sin contenido'}</small></div>
                </button>
                <button className="note-delete-mini" title="Eliminar apunte" onClick={() => void deleteNote(note)}>⌫</button>
              </article>)}
            </div>
          </article>
        </section>

        <aside className="right-column">
          <article className="section-card tasks-card">
            <div className="section-heading-row">
              <div className="section-title"><span>□</span><strong>PRÓXIMAS TAREAS / EXÁMENES</strong></div>
              <button onClick={() => navigate('tasks')}>Ver todas</button>
            </div>
            <div className="task-list">
              {localTasks.filter((task) => !task.done).slice(0, 3).map((task, index) => <button className="task-row" key={task.id} onClick={() => navigate('tasks')}>
                <span className={`task-icon ${subjectAccents[index % subjectAccents.length]}`}>▤</span>
                <div><strong>{task.title}</strong><small>{task.detail}</small></div>
                <time>Pendiente</time>
              </button>)}
              {localTasks.filter((task) => !task.done).length === 0 && <p className="empty-state">No tienes tareas pendientes.</p>}
            </div>
          </article>

          <article className="section-card sync-panel">
            <div className={`sync-check ${syncLabel.kind}`}>✓</div>
            <div>
              <div className="section-title"><strong>SINCRONIZACIÓN</strong></div>
              <h3>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Modo local'}</h3>
              <p>{session ? 'Pulsa sincronizar para actualizar tus dispositivos.' : 'Inicia sesión para sincronizar entre dispositivos.'}</p>
            </div>
            <button onClick={handleSync}>↻</button>
          </article>

          <article className="section-card quick-card">
            <div className="section-title"><span>✦</span><strong>ACCESOS RÁPIDOS</strong></div>
            <button onClick={() => openNewNote()}>✎ Nuevo apunte <span>→</span></button>
            <button onClick={() => navigate('maps')}>⌘ Crear mapa conceptual <span>→</span></button>
            <button onClick={() => navigate('ai')}>✦ Preguntar a NotCan AI <span>→</span></button>
          </article>
        </aside>
      </div>
      <footer className="data-footnote">{cycles.length} ciclo · {subjects.length} materias · {classes.length} clases · almacenamiento local-first</footer>
    </main>
  }

  function renderAi() {
    const quickActions: { mode: AiMode; label: string; prompt: string }[] = [
      { mode: 'summary', label: 'Resumir mis apuntes', prompt: 'Resume los apuntes recientes y organiza las ideas principales por tema.' },
      { mode: 'questions', label: 'Crear preguntas', prompt: 'Crea 10 preguntas de estudio con sus respuestas a partir de mis apuntes recientes.' },
      { mode: 'concept-map', label: 'Mapa conceptual', prompt: 'Construye un mapa conceptual textual a partir de mis apuntes recientes, indicando nodos y relaciones.' },
    ]

    return <main className="main-area feature-page ai-page">
      <div className="feature-header">
        <div><p className="eyebrow">HERRAMIENTAS</p><h1>NotCan AI</h1><p>Pregunta, resume, crea cuestionarios y organiza tus apuntes.</p></div>
        <span className={`status-pill ${session ? '' : 'muted'}`}>{session ? 'Cuenta conectada' : 'Requiere sesión'}</span>
      </div>

      <div className="ai-layout">
        <section className="section-card ai-main-card">
          <div className="ai-welcome-row"><div className="sparkle">✦</div><div><h2>¿Qué quieres estudiar?</h2><p>NotCan puede usar tus apuntes recientes como contexto.</p></div></div>

          <div className="ai-quick-actions">
            {quickActions.map((action) => <button key={action.mode} onClick={() => {
              setAiPrompt(action.prompt)
              void runAi(action.mode, action.prompt)
            }}>{action.label}</button>)}
          </div>

          <label className="ai-context-toggle">
            <input type="checkbox" checked={aiUseNotes} onChange={(event) => setAiUseNotes(event.target.checked)} />
            <span><strong>Usar mis apuntes como contexto</strong><small>Se enviarán hasta 8 apuntes recientes a NotCan AI para esta consulta.</small></span>
          </label>

          <div className="ai-composer ai-composer-live">
            <textarea value={aiPrompt} onChange={(event) => setAiPrompt(event.target.value)} placeholder="Por ejemplo: explícame la diferencia entre naturaleza y persona…" />
            <button className="primary" disabled={aiBusy || !aiPrompt.trim()} onClick={() => void runAi()}>{aiBusy ? 'Pensando…' : 'Enviar'}</button>
          </div>

          {aiError && <div className="ai-error"><strong>No pude responder todavía</strong><p>{aiError}</p></div>}
          {aiAnswer && <article className="ai-answer"><div className="ai-answer-head"><span className="sparkle small">✦</span><strong>NotCan AI</strong>{aiModel && <small>{aiModel}</small>}</div><div className="ai-answer-text">{aiAnswer}</div></article>}
        </section>

        <aside className="section-card ai-side-card">
          <div className="section-title"><strong>CONTEXTO</strong></div>
          <h3>{notes.length} apuntes disponibles</h3>
          <p>Cuando activas el contexto, la IA prioriza lo que ya has escrito en NotCan.</p>
          <div className="ai-source-list">{notes.slice(0, 5).map((note) => <button key={note.id} onClick={() => openExistingNote(note)}>✎ <span>{note.title}</span></button>)}</div>
          <p className="ai-privacy-note">La clave del proveedor no se guarda en la página pública. La llamada pasa por el backend de NotCan.</p>
        </aside>
      </div>
    </main>
  }

  function renderPage() {
    if (page === 'home') return renderHome()
    if (page === 'ai') return renderAi()

    const titles: Record<Page, [string, string]> = {
      home: ['', ''],
      subjects: ['Materias', 'Organiza tus clases, apuntes y recursos por materia.'],
      notes: ['Apuntes', 'Todo lo que has escrito, ordenado por fecha.'],
      library: ['Biblioteca', 'Tus documentos académicos en un mismo lugar.'],
      calendar: ['Calendario académico', 'Ciclo, clases de hoy y próximas actividades.'],
      tasks: ['Tareas', 'Pendientes de estudio y próximas entregas.'],
      grades: ['Calificaciones', 'Analiza tu rendimiento y calcula qué necesitas para alcanzar tu meta.'],
      maps: ['Mapas', 'Construye mapas mentales y conceptuales.'],
      ai: ['', ''],
      sync: ['Sincronización', 'Estado de tus cambios entre dispositivos.'],
      settings: ['Ajustes', 'Personaliza el comportamiento de NotCan.'],
      account: ['Cuenta', 'Gestiona tu sesión y sincronización.'],
    }
    const [title, subtitle] = titles[page]

    return <main className="main-area feature-page">
      <div className="feature-header">
        <div><p className="eyebrow">NOTCAN</p><h1>{title}</h1><p>{subtitle}</p></div>
        {['notes', 'library'].includes(page) && <button className="new-button" onClick={() => {
          if (page === 'notes') openNewNote()
          else if (page === 'library') fileInputRef.current?.click()
        }}>＋ Nuevo</button>}
      </div>

      {page === 'subjects' && <div className="feature-grid subjects-view">
        <aside className="section-card list-panel">
          {activeSubjects.map((subject, index) => <button key={subject.id} className={selectedSubject?.id === subject.id ? 'selected' : ''} onClick={() => setSelectedSubjectId(subject.id)}>
            <span className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>{subjectIcons[index % subjectIcons.length]}</span>
            <span><strong>{subject.name}</strong><small>{classes.filter((c) => c.subjectId === subject.id).length} clases</small></span>
          </button>)}
        </aside>
        <section className="section-card detail-panel">
          <div className="detail-heading"><div><p className="eyebrow">MATERIA</p><h2>{selectedSubject?.name ?? 'Selecciona una materia'}</h2></div><button className="primary" disabled={!selectedSubjectClasses[0]} onClick={() => selectedSubjectClasses[0] && openNewNote(selectedSubjectClasses[0].id)}>＋ Apunte</button></div>
          <div className="class-note-list">
            {selectedSubjectClasses.map((classSession) => <article key={classSession.id}>
              <div><strong>{classSession.title}</strong><small>{fullDate(classSession.startedAtEpochMs)}</small></div>
              <div>{notes.filter((n) => n.classSessionId === classSession.id).map((note) => <div className="class-note-row" key={note.id}>
                <button onClick={() => openExistingNote(note)}>✎ {note.title}</button>
                <button className="danger-text" onClick={() => void deleteNote(note)}>Eliminar</button>
              </div>)}<button onClick={() => openNewNote(classSession.id)}>＋ Nuevo apunte</button></div>
            </article>)}
          </div>
        </section>
      </div>}

      {page === 'notes' && <section className="section-card collection-list note-collection">
        {notes.map((note) => <article className="note-list-row" key={note.id}>
          <button className="note-list-open" onClick={() => openExistingNote(note)}>
            <span className="file-icon blue">≡</span>
            <span><strong>{note.title}</strong><small>{stripHtml(note.body).slice(0, 100) || 'Sin contenido'}</small></span>
            <time>{shortDate(note.updatedAtEpochMs)}<small>{shortTime(note.updatedAtEpochMs)}</small></time>
          </button>
          <button className="note-list-delete" onClick={() => void deleteNote(note)} title="Eliminar apunte">⌫</button>
        </article>)}
        {notes.length === 0 && <p className="empty-state">Todavía no hay apuntes.</p>}
      </section>}

      {page === 'library' && <>
        <input ref={fileInputRef} type="file" multiple accept=".pdf,.doc,.docx,.epub,.txt" hidden onChange={importFiles} />
        <section className="section-card library-drop" onClick={() => fileInputRef.current?.click()}><strong>＋ Importar documentos</strong><p>PDF, DOC/DOCX, EPUB y TXT.</p></section>
        <section className="file-grid">{localFiles.map((file) => <article className="section-card file-tile" key={file.id}><div className="file-icon rose">DOC</div><strong>{file.name}</strong><small>{(file.size / 1024 / 1024).toFixed(2)} MB · {shortDate(file.addedAt)}</small><button onClick={() => setLocalFiles((prev) => prev.filter((item) => item.id !== file.id))}>Eliminar</button></article>)}</section>
      </>}

      {page === 'calendar' && <div className="academic-calendar">
        <section className="calendar-summary-grid">
          <article className="section-card semester-card">
            <span className="calendar-summary-icon">▣</span>
            <div><small>CICLO ACTIVO</small><strong>{activeCycle?.name ?? 'Sin ciclo configurado'}</strong><p>{activeCycle && activeCycle.startEpochDay > 0 && activeCycle.endEpochDay > 0 ? `${shortDate(activeCycle.startEpochDay * 86400000)} – ${shortDate(activeCycle.endEpochDay * 86400000)}` : 'Puedes definir las fechas del ciclo desde la app.'}</p></div>
          </article>
          <article className="section-card calendar-stat"><small>HOY</small><strong>{todayClasses.length}</strong><span>{todayClasses.length === 1 ? 'clase registrada' : 'clases registradas'}</span></article>
          <article className="section-card calendar-stat"><small>PRÓXIMAS</small><strong>{upcomingClasses.length}</strong><span>clases por venir</span></article>
        </section>

        <section className="section-card today-panel">
          <div className="section-heading-row"><div><p className="eyebrow">HOY</p><h2>{fullDate(Date.now())}</h2></div><span className="status-pill">{todayClasses.length ? 'Con actividad' : 'Sin clases'}</span></div>
          {todayClasses.length ? <div className="calendar-compact-list">{[...todayClasses].sort((a, b) => a.startedAtEpochMs - b.startedAtEpochMs).map((classSession) => <article key={classSession.id}><time>{shortTime(classSession.startedAtEpochMs)}</time><div><strong>{classSession.title}</strong><small>{subjects.find((s) => s.id === classSession.subjectId)?.name ?? 'Materia'}</small></div></article>)}</div> : <p className="empty-state">No hay materias programadas para hoy.</p>}
        </section>

        <section className="section-card upcoming-panel">
          <div className="section-heading-row"><div><p className="eyebrow">AGENDA</p><h2>Próximas clases</h2></div><span>{upcomingClasses.length} pendientes</span></div>
          {upcomingClasses.slice(0, 8).map((classSession) => <article className="calendar-event modern" key={classSession.id}><time><strong>{new Date(classSession.startedAtEpochMs).getDate()}</strong><span>{shortDate(classSession.startedAtEpochMs).split(' ')[1]}</span></time><div><strong>{classSession.title}</strong><small>{subjects.find((s) => s.id === classSession.subjectId)?.name ?? 'Materia'} · {shortTime(classSession.startedAtEpochMs)}</small></div></article>)}
          {upcomingClasses.length === 0 && <p className="empty-state">No hay clases futuras registradas todavía.</p>}
        </section>
      </div>}

      {page === 'tasks' && <section className="section-card tasks-view">
        <form onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          const taskTitle = String(form.get('title') || '').trim()
          if (!taskTitle) return
          setLocalTasks((prev) => [...prev, { id: crypto.randomUUID(), title: taskTitle, detail: 'Pendiente personal', done: false }])
          event.currentTarget.reset()
        }}><input name="title" placeholder="Nueva tarea…" /><button className="primary">Añadir</button></form>
        {localTasks.length === 0 && <p className="empty-state">No tienes tareas personales todavía.</p>}
        {localTasks.map((task) => <label className={`task-check ${task.done ? 'done' : ''}`} key={task.id}><input type="checkbox" checked={task.done} onChange={() => setLocalTasks((prev) => prev.map((item) => item.id === task.id ? { ...item, done: !item.done } : item))} /><span><strong>{task.title}</strong><small>{task.detail}</small></span><button onClick={(event) => { event.preventDefault(); setLocalTasks((prev) => prev.filter((item) => item.id !== task.id)) }}>×</button></label>)}
      </section>}

      {page === 'grades' && <div className="feature-grid grades-workspace">
        <aside className="section-card list-panel grade-subjects">{subjects.map((subject, index) => <button key={subject.id} className={selectedSubject?.id === subject.id ? 'selected' : ''} onClick={() => { setSelectedSubjectId(subject.id); setGradeMessage('') }}><span className={`course-icon ${subjectAccents[index % subjectAccents.length]}`}>☆</span><span><strong>{subject.name}</strong><small>{grades.filter((grade) => grade.subjectId === subject.id).length} notas</small></span></button>)}</aside>

        <div className="grades-dashboard">
          <section className="section-card grade-summary-card">
            <div className="grade-summary-heading"><div><p className="eyebrow">RESUMEN AUTOMÁTICO</p><h2>{selectedSubject?.name ?? 'Selecciona una materia'}</h2></div><span className={`risk-pill ${gradeStats.risk.toLowerCase().replace('ó', 'o').replace(' ', '-')}`}>{gradeStats.risk}</span></div>
            <div className="grade-summary-body">
              <div className="grade-ring"><strong>{gradeStats.average.toFixed(1)}%</strong><small>promedio</small></div>
              <div className="grade-metrics">
                <div><span>Aporte acumulado</span><strong>{gradeStats.contribution.toFixed(1)} / 100</strong></div>
                <div><span>Porcentaje ya evaluado</span><strong>{gradeStats.evaluatedWeight.toFixed(1)}%</strong></div>
                <div><span>Falta por evaluar</span><strong>{gradeStats.remainingWeight.toFixed(1)}%</strong></div>
              </div>
            </div>
            <div className="weight-progress"><i style={{ width: `${Math.min(100, gradeStats.evaluatedWeight)}%` }} /></div>
          </section>

          <section className="section-card target-card">
            <div className="section-heading-row"><div><p className="eyebrow">QUÉ NECESITO SACAR</p><h2>Meta final</h2></div><div className="target-buttons">{[70, 80, 90].map((target) => <button key={target} className={gradeTarget === target ? 'selected' : ''} onClick={() => setGradeTarget(target)}>{target}</button>)}</div></div>
            <div className="needed-result">
              {selectedGrades.length === 0 ? <><strong>Registra tu primera nota</strong><span>Después NotCan calculará automáticamente lo necesario.</span></> : gradeStats.remainingWeight <= 0 ? <><strong>{gradeStats.contribution >= gradeTarget ? 'Meta alcanzada' : 'Período evaluado al 100%'}</strong><span>Tu aporte final actual es {gradeStats.contribution.toFixed(1)} / 100.</span></> : (gradeStats.required ?? 0) <= 0 ? <><strong>Meta ya asegurada</strong><span>Incluso con el porcentaje pendiente, ya alcanzaste {gradeTarget}.</span></> : (gradeStats.required ?? 0) > 100 ? <><strong>La meta {gradeTarget} ya no es alcanzable</strong><span>Necesitarías {(gradeStats.required ?? 0).toFixed(1)}% en el {gradeStats.remainingWeight.toFixed(1)}% restante.</span></> : <><strong>{(gradeStats.required ?? 0).toFixed(1)}%</strong><span>promedio necesario en el {gradeStats.remainingWeight.toFixed(1)}% restante para terminar con {gradeTarget}.</span></>}
            </div>
            <div className="projection-grid"><div><small>Si promedias 70</small><strong>{gradeStats.projected70.toFixed(1)}</strong></div><div><small>Si promedias 85</small><strong>{gradeStats.projected85.toFixed(1)}</strong></div><div><small>Si sacas 100</small><strong>{gradeStats.projected100.toFixed(1)}</strong></div></div>
          </section>

          <section className="section-card grade-entry-card">
            <div><p className="eyebrow">NUEVA ACTIVIDAD</p><h2>Añadir calificación</h2></div>
            <form className="grade-form" onSubmit={addGrade}>
              <label className="grade-title-field"><span>Actividad</span><input value={gradeDraft.title} onChange={(event) => setGradeDraft((prev) => ({ ...prev, title: event.target.value }))} placeholder="Examen, ensayo, prueba de lectura…" /></label>
              <label><span>Obtenido</span><input inputMode="decimal" type="number" min="0" step="0.01" value={gradeDraft.score} onChange={(event) => setGradeDraft((prev) => ({ ...prev, score: event.target.value }))} placeholder="0" /></label>
              <label><span>Máximo</span><input inputMode="decimal" type="number" min="0.01" step="0.01" value={gradeDraft.maxScore} onChange={(event) => setGradeDraft((prev) => ({ ...prev, maxScore: event.target.value }))} /></label>
              <label><span>Peso %</span><input inputMode="decimal" type="number" min="0.01" max="100" step="0.01" value={gradeDraft.weightPercent} onChange={(event) => setGradeDraft((prev) => ({ ...prev, weightPercent: event.target.value }))} placeholder={gradeStats.remainingWeight.toFixed(1)} /></label>
              <button className="primary">＋ Guardar calificación</button>
            </form>
            {gradeMessage && <p className="grade-message">{gradeMessage}</p>}
          </section>

          <section className="section-card grade-list enhanced">
            <div className="section-heading-row"><div><p className="eyebrow">ACTIVIDADES</p><h2>Calificaciones registradas</h2></div><span>{selectedGrades.length}</span></div>
            {selectedGrades.map((grade) => <article key={grade.id}><div><strong>{grade.title}</strong><small>Peso {grade.weightPercent}% · aporte {grade.maxScore > 0 ? ((grade.score / grade.maxScore) * grade.weightPercent).toFixed(1) : '0.0'} pts</small></div><div className="grade-row-score"><strong>{grade.score}/{grade.maxScore}</strong><span>{grade.maxScore > 0 ? ((grade.score / grade.maxScore) * 100).toFixed(1) : '0.0'}%</span></div><button className="danger-text" onClick={() => void deleteGrade(grade)}>Eliminar</button></article>)}
            {selectedGrades.length === 0 && <p className="empty-state">Todavía no has registrado calificaciones para esta materia.</p>}
          </section>
        </div>
      </div>}

      {page === 'maps' && <section className="section-card map-workspace">
        <input className="map-title-input" value={mapTitle} onChange={(event) => setMapTitle(event.target.value)} />

        <div className="map-toolbar">
          <div className="map-toolbar-group">
            <button onClick={() => setMapZoom((value) => Math.max(0.45, value / 1.15))} aria-label="Alejar mapa">−</button>
            <span>{Math.round(mapZoom * 100)}%</span>
            <button onClick={() => setMapZoom((value) => Math.min(2.8, value * 1.15))} aria-label="Acercar mapa">＋</button>
            <button onClick={resetMapView}>Centrar</button>
          </div>
          <small>Arrastra para mover · rueda o pellizco del navegador para acercar y alejar</small>
        </div>

        <div
          className="map-stage"
          onPointerDown={handleMapPointerDown}
          onPointerMove={handleMapPointerMove}
          onPointerUp={handleMapPointerEnd}
          onPointerCancel={handleMapPointerEnd}
          onWheel={handleMapWheel}
        >
          <div
            className="map-surface"
            style={{ transform: `translate(${mapPan.x}px, ${mapPan.y}px) scale(${mapZoom})` }}
          >
            <div className="map-node root">{mapNodes[0]}</div>
            {mapNodes.slice(1).map((node, index) => {
              const total = Math.max(1, mapNodes.length - 1)
              const angle = (Math.PI * 2 * index) / total - Math.PI / 2
              const radiusX = total > 8 ? 39 : 33
              const radiusY = total > 8 ? 37 : 31
              return <div
                className="map-node child"
                key={`${node}-${index}`}
                style={{
                  left: `${50 + Math.cos(angle) * radiusX}%`,
                  top: `${50 + Math.sin(angle) * radiusY}%`,
                }}
              >{node}</div>
            })}
          </div>
        </div>

        <div className="map-node-composer">
          <textarea
            value={mapNodeDraft}
            onChange={(event) => setMapNodeDraft(event.target.value)}
            placeholder="Escribe un concepto, explicación o fragmento largo…"
            onKeyDown={(event) => {
              if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
                event.preventDefault()
                addMapNode()
              }
            }}
          />
          <button onClick={addMapNode}>＋ Nodo</button>
          <button onClick={() => { setAiPrompt('Crea un mapa conceptual a partir de mis apuntes recientes.'); setAiMode('concept-map'); navigate('ai') }}>✦ Generar con IA</button>
        </div>
        <small className="map-composer-hint">El texto de cada nodo se muestra completo. Ctrl/⌘ + Enter añade el nodo.</small>
      </section>}

      {page === 'sync' && <section className="section-card settings-list"><article><div><strong>{session ? 'Cuenta conectada' : 'Modo local'}</strong><p>{session ? session.user.email : 'Puedes trabajar sin cuenta. Para sincronizar entre dispositivos necesitas iniciar sesión.'}</p></div><button className="primary" onClick={session ? handleSync : () => setAccountOpen(true)}>{session ? 'Sincronizar ahora' : 'Iniciar sesión'}</button></article><article><div><strong>Cambios pendientes</strong><p>{pending} operaciones esperan sincronización.</p></div><span className="big-number">{pending}</span></article></section>}

      {page === 'settings' && <div className="settings-phase1-stack">
        <section className="section-card settings-list">
          <article><div><strong>Apariencia</strong><p>Usa la misma idea visual de NotCan Android.</p></div><button className="settings-action" onClick={() => setTheme((value) => value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? 'Cambiar a claro' : 'Cambiar a oscuro'}</button></article>
          <article><div><strong>Modo offline</strong><p>NotCan guarda primero en este dispositivo.</p></div><span className="status-pill">Activo</span></article>
          <article><div><strong>Autoguardado</strong><p>Los apuntes se guardan mientras escribes.</p></div><span className="status-pill">Activo</span></article>
          <article><div><strong>Formato por selección</strong><p>El editor web seguirá alineándose con el editor de Android durante esta fase.</p></div><span className="status-pill">Activo</span></article>
        </section>
        <CycleManagementPanel cycles={cycles} onChanged={refresh} />
      </div>}

      {page === 'account' && <section className="section-card account-page"><div className="account-avatar">{session?.user.email?.[0]?.toUpperCase() ?? 'N'}</div><h2>{session ? 'Cuenta de NotCan' : 'Usando NotCan localmente'}</h2><p>{session?.user.email ?? 'No necesitas cuenta para estudiar. Inicia sesión si quieres sincronizar y usar NotCan AI.'}</p><button className="primary" onClick={() => setAccountOpen(true)}>{session ? 'Gestionar cuenta' : 'Iniciar sesión / Crear cuenta'}</button></section>}
    </main>
  }

  const currentEditorNote = editorNoteId ? notes.find((note) => note.id === editorNoteId) ?? null : null
  const wordCount = stripHtml(editorBody).split(/\s+/).filter(Boolean).length

  return <div className={`app-shell ${page === 'home' ? 'sidebar-home' : sidebarOpen ? 'sidebar-open' : 'sidebar-hidden'}`}>
    <aside className="sidebar">
      <div className="brand-row"><div className="brand-mark">N</div><div><strong>NotCan</strong><span>Web</span></div></div>
      {navGroups.map((group) => <div className="nav-group" key={group.label}><p>{group.label}</p>{group.items.map((item) => <button key={item.page} className={`nav-item ${page === item.page && !editorOpen ? 'active' : ''} ${item.page === 'ai' ? 'ai-nav' : ''}`} onClick={() => navigate(item.page)}><span>{item.icon}</span>{item.label}</button>)}</div>)}
      <div className="sidebar-bottom">
        <button className={`nav-item ${page === 'sync' ? 'active' : ''}`} onClick={() => navigate('sync')}><span>↻</span>Sincronización <i className={`dot ${syncLabel.kind}`} /></button>
        <button className={`nav-item ${page === 'settings' ? 'active' : ''}`} onClick={() => navigate('settings')}><span>⚙</span>Ajustes</button>
        <button className={`nav-item ${page === 'account' ? 'active' : ''}`} onClick={() => navigate('account')}><span>○</span>Cuenta</button>
      </div>
    </aside>
    {page !== 'home' && sidebarOpen && <button className="sidebar-scrim" aria-label="Cerrar navegación" onClick={() => setSidebarOpen(false)} />}

    <div className="page-area">
      <header className="global-topbar">
        {page !== 'home' && <button className="sidebar-toggle" aria-label="Abrir navegación" onClick={() => setSidebarOpen(true)}>☰</button>}
        <div className="search-wrap">
          <label className="search-box"><span>⌕</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar en NotCan…" /><kbd>Ctrl K</kbd></label>
          {searchResults.length > 0 && <div className="search-results">{searchResults.map((result, index) => <button key={`${result.type}-${index}`} onClick={result.action}><small>{result.type}</small><strong>{result.title}</strong></button>)}</div>}
        </div>
        <div className="topbar-actions">
          <button className="sync-status" onClick={handleSync}><span className={`dot ${session ? syncLabel.kind : 'muted'}`} /><span><strong>{session ? syncLabel.text : 'Solo local'}</strong><small>{session ? (pending ? `${pending} cambios pendientes` : 'Todo al día') : 'Inicia sesión para sincronizar'}</small></span></button>
          <button className="theme-toggle" aria-label={theme === 'dark' ? 'Activar modo claro' : 'Activar modo oscuro'} title={theme === 'dark' ? 'Modo claro' : 'Modo oscuro'} onClick={() => setTheme((value) => value === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? '☀' : '☾'}</button>
          <button className="new-button" onClick={() => openNewNote()}>＋ Nuevo</button>
          <button className="avatar-button" onClick={() => navigate('account')}>{session?.user.email?.[0]?.toUpperCase() ?? 'N'}</button>
        </div>
      </header>

      {editorOpen ? <main className="note-editor-page">
        <div className="editor-toolbar">
          <button className="back-editor" onClick={() => setEditorOpen(false)}>← Volver</button>
          <div className="editor-save-state"><span className={`dot ${saveState === 'saved' ? 'good' : saveState === 'saving' ? 'warn' : 'muted'}`} />{saveText}</div>
          <div className="editor-toolbar-actions"><button className="editor-ai-button" onClick={askAiAboutCurrentNote}>✦ IA</button><button className="editor-delete-button" onClick={() => void deleteCurrentNote()}>⌫ Eliminar</button></div>
        </div>

        <div className="editor-sheet">
          <div className="editor-meta-row">
            <select value={editorClassId ?? ''} onChange={(event) => setEditorClassId(event.target.value)}>{classes.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}</select>
            <span>{editorCreatedAt ? fullDate(editorCreatedAt) : ''}</span>
          </div>
          <input className="editor-title-input" value={editorTitle} onChange={(event) => setEditorTitle(event.target.value)} placeholder="Título del apunte" />

          <div className="format-bar" role="toolbar" aria-label="Formato del apunte">
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('bold')} title="Negrita (solo selección)"><strong>B</strong></button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('italic')} title="Cursiva (solo selección)"><em>I</em></button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('underline')} title="Subrayado (solo selección)"><u>U</u></button>
            <span />
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('formatBlock', 'H1')}>H1</button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('formatBlock', 'H2')}>H2</button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('insertUnorderedList')}>• Lista</button>
            <button onMouseDown={preserveSelection} onClick={() => applySelectionCommand('insertOrderedList')}>1. Lista</button>
            <span />
            <button onMouseDown={preserveSelection} onClick={createLinkForSelection}>↗ Enlace</button>
            <button onClick={askAiAboutCurrentNote}>✦ IA</button>
          </div>
          {formatHint && <div className="format-hint">{formatHint}</div>}

          <div
            ref={editorRef}
            className="editor-body-input rich-editor"
            contentEditable
            suppressContentEditableWarning
            data-placeholder="Empieza a escribir tus apuntes…"
            onInput={(event) => setEditorBody(event.currentTarget.innerHTML)}
            onPaste={(event) => {
              event.preventDefault()
              const text = event.clipboardData.getData('text/plain')
              document.execCommand('insertText', false, text)
              setEditorBody(event.currentTarget.innerHTML)
            }}
          />
          <div className="editor-footer"><span>{wordCount} palabras</span><span>{currentEditorNote ? 'Autoguardado · eliminación sincronizable' : 'Se guardará automáticamente al escribir'}</span></div>
        </div>
      </main> : renderPage()}
    </div>

    {accountOpen && <div className="modal-backdrop" onMouseDown={() => setAccountOpen(false)}>
      <section className="account-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-title"><div><p className="eyebrow">CUENTA NOTCAN</p><h2>{session ? 'Tu cuenta' : authMode === 'login' ? 'Inicia sesión' : 'Crea tu cuenta'}</h2></div><button onClick={() => setAccountOpen(false)}>×</button></div>
        {session ? <div className="signed-account"><div className="account-avatar">{session.user.email?.[0]?.toUpperCase()}</div><strong>{session.user.email}</strong><p>La cuenta permite sincronizar NotCan y usar NotCan AI.</p><button className="primary" onClick={handleSync}>↻ Sincronizar ahora</button><button className="ghost" onClick={() => void supabase?.auth.signOut()}>Cerrar sesión</button></div> : <>
          <p className="auth-copy">Puedes usar NotCan sin cuenta. Inicia sesión para sincronizar datos y utilizar NotCan AI.</p>
          <form className="auth-form" onSubmit={submitAuth}>
            <label>Correo<input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>Contraseña<input type="password" required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            <button className="primary" disabled={authBusy}>{authBusy ? 'Procesando…' : authMode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</button>
          </form>
          {authMessage && <p className="auth-message">{authMessage}</p>}
          <button className="text-button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthMessage('') }}>{authMode === 'login' ? 'Crear una cuenta nueva' : 'Ya tengo una cuenta'}</button>
        </>}
      </section>
    </div>}
  </div>
}
