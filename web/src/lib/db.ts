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

export async function seedDemoIfEmpty() {
  if ((await db.studyCycles.count()) > 0) return

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
