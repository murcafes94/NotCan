import Dexie, { type EntityTable } from 'dexie'
import type {
  ClassSessionRecord,
  GradeItemRecord,
  NotePageRecord,
  OutboxRecord,
  StudyCycleRecord,
  SubjectRecord,
} from '../types/sync'

export class NotCanDb extends Dexie {
  studyCycles!: EntityTable<StudyCycleRecord, 'id'>
  subjects!: EntityTable<SubjectRecord, 'id'>
  classSessions!: EntityTable<ClassSessionRecord, 'id'>
  notePages!: EntityTable<NotePageRecord, 'id'>
  gradeItems!: EntityTable<GradeItemRecord, 'id'>
  outbox!: EntityTable<OutboxRecord, 'id'>

  constructor() {
    super('notcan-web')
    this.version(1).stores({
      studyCycles: 'id, isActive, updatedAtEpochMs',
      subjects: 'id, cycleId, updatedAtEpochMs',
      classSessions: 'id, subjectId, startedAtEpochMs, updatedAtEpochMs',
      notePages: 'id, classSessionId, updatedAtEpochMs',
      gradeItems: 'id, subjectId, updatedAtEpochMs',
      outbox: 'id, entity, entityId, changedAtEpochMs',
    })
  }
}

export const db = new NotCanDb()

const DEVICE_KEY = 'notcan-device-id'
export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

export async function queueUpsert(entity: OutboxRecord['entity'], entityId: string, payload: unknown) {
  await db.outbox.put({
    id: crypto.randomUUID(),
    entity,
    entityId,
    operation: 'UPSERT',
    payload,
    changedAtEpochMs: Date.now(),
  })
}

export async function queueDelete(entity: OutboxRecord['entity'], entityId: string) {
  await db.outbox.where('entityId').equals(entityId).delete()
  await db.outbox.put({
    id: crypto.randomUUID(),
    entity,
    entityId,
    operation: 'DELETE',
    payload: null,
    changedAtEpochMs: Date.now(),
  })
}

export type CycleTreePreview = { subjects: number; classes: number; notes: number; grades: number }

export async function previewCycleTree(cycleId: string): Promise<CycleTreePreview> {
  const subjects = await db.subjects.where('cycleId').equals(cycleId).toArray()
  const subjectIds = new Set(subjects.map((item) => item.id))
  const classes = (await db.classSessions.toArray()).filter((item) => subjectIds.has(item.subjectId))
  const classIds = new Set(classes.map((item) => item.id))
  const notes = (await db.notePages.toArray()).filter((item) => classIds.has(item.classSessionId))
  const grades = (await db.gradeItems.toArray()).filter((item) => subjectIds.has(item.subjectId))
  return { subjects: subjects.length, classes: classes.length, notes: notes.length, grades: grades.length }
}

export async function activateCycleLocal(cycleId: string) {
  const now = Date.now()
  const deviceId = getDeviceId()
  const cycles = await db.studyCycles.toArray()
  await db.transaction('rw', db.studyCycles, db.outbox, async () => {
    for (const cycle of cycles) {
      const nextActive = cycle.id === cycleId
      if (cycle.isActive === nextActive) continue
      const next = { ...cycle, isActive: nextActive, updatedAtEpochMs: now, revision: cycle.revision + 1, deviceId }
      await db.studyCycles.put(next)
      await queueUpsert('study_cycles', next.id, next)
    }
  })
}

export async function deleteCycleTree(cycleId: string) {
  const deletingCycle = await db.studyCycles.get(cycleId)
  if (!deletingCycle) return

  const subjects = await db.subjects.where('cycleId').equals(cycleId).toArray()
  const subjectIds = new Set(subjects.map((item) => item.id))
  const classes = (await db.classSessions.toArray()).filter((item) => subjectIds.has(item.subjectId))
  const classIds = new Set(classes.map((item) => item.id))
  const notes = (await db.notePages.toArray()).filter((item) => classIds.has(item.classSessionId))
  const grades = (await db.gradeItems.toArray()).filter((item) => subjectIds.has(item.subjectId))

  await db.transaction('rw', [db.studyCycles, db.subjects, db.classSessions, db.notePages, db.gradeItems, db.outbox], async () => {
    // Supabase usa soft-delete. Encolamos cada descendiente para que Android y web
    // reciban exactamente el mismo árbol eliminado, no solo el registro padre.
    for (const note of notes) { await queueDelete('note_pages', note.id); await db.notePages.delete(note.id) }
    for (const grade of grades) { await queueDelete('grade_items', grade.id); await db.gradeItems.delete(grade.id) }
    for (const session of classes) { await queueDelete('class_sessions', session.id); await db.classSessions.delete(session.id) }
    for (const subject of subjects) { await queueDelete('subjects', subject.id); await db.subjects.delete(subject.id) }
    await queueDelete('study_cycles', cycleId)
    await db.studyCycles.delete(cycleId)
  })

  if (deletingCycle.isActive) {
    const replacement = (await db.studyCycles.toArray()).sort((a, b) => b.createdAtEpochMs - a.createdAtEpochMs)[0]
    if (replacement) await activateCycleLocal(replacement.id)
  }
}

const DEMO_SEEDED_KEY = 'notcan-demo-seeded-v1'
export async function seedDemoIfEmpty() {
  if ((await db.studyCycles.count()) > 0) {
    localStorage.setItem(DEMO_SEEDED_KEY, '1')
    return
  }
  // Si el usuario eliminó manualmente su último ciclo, no recreamos el demo al recargar.
  if (localStorage.getItem(DEMO_SEEDED_KEY) === '1') return

  const now = Date.now()
  const deviceId = getDeviceId()
  const cycleId = crypto.randomUUID()
  const subjectId = crypto.randomUUID()
  const classId = crypto.randomUUID()
  const noteId = crypto.randomUUID()

  const cycle: StudyCycleRecord = {
    id: cycleId,
    name: 'Semestre actual',
    isActive: true,
    createdAtEpochMs: now,
    startEpochDay: 0,
    endEpochDay: 0,
    updatedAtEpochMs: now,
    revision: 1,
    deviceId,
  }
  const subject: SubjectRecord = {
    id: subjectId,
    cycleId,
    name: 'Materia de prueba',
    colorHex: '#7aa2ff',
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    revision: 1,
    deviceId,
  }
  const classSession: ClassSessionRecord = {
    id: classId,
    subjectId,
    title: 'Primera clase',
    startedAtEpochMs: now,
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    revision: 1,
    deviceId,
  }
  const note: NotePageRecord = {
    id: noteId,
    classSessionId: classId,
    title: 'Apunte sincronizable',
    body: 'Este apunte vive primero en IndexedDB. Cuando conectemos el backend, la misma UUID viajará entre web y Android.',
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    revision: 1,
    deviceId,
  }

  await db.transaction('rw', db.studyCycles, db.subjects, db.classSessions, db.notePages, db.outbox, async () => {
    localStorage.setItem(DEMO_SEEDED_KEY, '1')
    await db.studyCycles.add(cycle)
    await db.subjects.add(subject)
    await db.classSessions.add(classSession)
    await db.notePages.add(note)
    await queueUpsert('study_cycles', cycle.id, cycle)
    await queueUpsert('subjects', subject.id, subject)
    await queueUpsert('class_sessions', classSession.id, classSession)
    await queueUpsert('note_pages', note.id, note)
  })
}
