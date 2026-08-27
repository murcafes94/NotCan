export type SyncOperation = 'UPSERT' | 'DELETE'

export interface SyncMeta {
  id: string
  updatedAtEpochMs: number
  deletedAtEpochMs?: number | null
  revision: number
  deviceId: string
}

export interface StudyCycleRecord extends SyncMeta {
  name: string
  isActive: boolean
  createdAtEpochMs: number
  startEpochDay: number
  endEpochDay: number
}

export interface SubjectRecord extends SyncMeta {
  cycleId: string
  name: string
  colorHex?: string | null
  createdAtEpochMs: number
}

export interface ClassSessionRecord extends SyncMeta {
  subjectId: string
  title: string
  startedAtEpochMs: number
  endedAtEpochMs?: number | null
  createdAtEpochMs: number
}

export interface NotePageRecord extends SyncMeta {
  classSessionId: string
  title: string
  body: string
  createdAtEpochMs: number
}

export interface GradeItemRecord extends SyncMeta {
  subjectId: string
  title: string
  score: number
  maxScore: number
  weightPercent: number
  createdAtEpochMs: number
}

export type SyncEntity = 'study_cycles' | 'subjects' | 'class_sessions' | 'note_pages' | 'grade_items'

export interface OutboxRecord {
  id: string
  entity: SyncEntity
  entityId: string
  operation: SyncOperation
  payload: unknown
  changedAtEpochMs: number
}
