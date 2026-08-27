import { db } from './db'
import { isSupabaseConfigured, supabase } from './supabase'
import type { SyncEntity } from '../types/sync'

const LAST_PULL_KEY = 'notcan-last-pull-epoch-ms'

type SyncResult =
  | { state: 'synced'; pushed: number; pulled: number }
  | { state: 'offline' }
  | { state: 'backend-not-configured' }
  | { state: 'unauthenticated' }
  | { state: 'error'; message: string }

function toRemote(entity: SyncEntity, payload: Record<string, unknown>, userId: string): Record<string, unknown> {
  const common = {
    id: payload.id,
    user_id: userId,
    revision: payload.revision ?? 1,
    device_id: payload.deviceId,
    created_at: new Date(Number(payload.createdAtEpochMs ?? Date.now())).toISOString(),
    updated_at: new Date(Number(payload.updatedAtEpochMs ?? Date.now())).toISOString(),
    deleted_at: payload.deletedAtEpochMs ? new Date(Number(payload.deletedAtEpochMs)).toISOString() : null,
  }

  switch (entity) {
    case 'study_cycles':
      return { ...common, name: payload.name, is_active: payload.isActive, start_epoch_day: payload.startEpochDay ?? 0, end_epoch_day: payload.endEpochDay ?? 0 }
    case 'subjects':
      return { ...common, cycle_id: payload.cycleId, name: payload.name, color_hex: payload.colorHex ?? null }
    case 'class_sessions':
      return {
        ...common,
        subject_id: payload.subjectId,
        title: payload.title,
        started_at_epoch_ms: payload.startedAtEpochMs,
        ended_at_epoch_ms: payload.endedAtEpochMs ?? null,
      }
    case 'note_pages':
      return { ...common, class_session_id: payload.classSessionId, title: payload.title, body: payload.body }
    case 'grade_items':
      return {
        ...common,
        subject_id: payload.subjectId,
        title: payload.title,
        score: payload.score,
        max_score: payload.maxScore,
        weight_percent: payload.weightPercent,
      }
  }
}

async function applyRemote(entity: SyncEntity, row: Record<string, unknown>) {
  const updatedAtEpochMs = Date.parse(String(row.updated_at))
  const deletedAtEpochMs = row.deleted_at ? Date.parse(String(row.deleted_at)) : null
  const common = {
    id: String(row.id),
    revision: Number(row.revision ?? 1),
    deviceId: String(row.device_id ?? 'remote'),
    createdAtEpochMs: Date.parse(String(row.created_at)),
    updatedAtEpochMs,
    deletedAtEpochMs,
  }

  if (deletedAtEpochMs) {
    switch (entity) {
      case 'study_cycles': await db.studyCycles.delete(common.id); break
      case 'subjects': await db.subjects.delete(common.id); break
      case 'class_sessions': await db.classSessions.delete(common.id); break
      case 'note_pages': await db.notePages.delete(common.id); break
      case 'grade_items': await db.gradeItems.delete(common.id); break
    }
    return
  }

  switch (entity) {
    case 'study_cycles':
      await db.studyCycles.put({ ...common, name: String(row.name), isActive: Boolean(row.is_active), startEpochDay: Number(row.start_epoch_day ?? 0), endEpochDay: Number(row.end_epoch_day ?? 0) })
      break
    case 'subjects':
      await db.subjects.put({ ...common, cycleId: String(row.cycle_id), name: String(row.name), colorHex: row.color_hex ? String(row.color_hex) : null })
      break
    case 'class_sessions':
      await db.classSessions.put({ ...common, subjectId: String(row.subject_id), title: String(row.title), startedAtEpochMs: Number(row.started_at_epoch_ms), endedAtEpochMs: row.ended_at_epoch_ms == null ? null : Number(row.ended_at_epoch_ms) })
      break
    case 'note_pages':
      await db.notePages.put({ ...common, classSessionId: String(row.class_session_id), title: String(row.title), body: String(row.body ?? '') })
      break
    case 'grade_items':
      await db.gradeItems.put({ ...common, subjectId: String(row.subject_id), title: String(row.title), score: Number(row.score), maxScore: Number(row.max_score), weightPercent: Number(row.weight_percent) })
      break
  }
}

export async function syncNow(): Promise<SyncResult> {
  if (!navigator.onLine) return { state: 'offline' }
  if (!isSupabaseConfigured || !supabase) return { state: 'backend-not-configured' }

  try {
    const { data: authData } = await supabase.auth.getUser()
    const user = authData.user
    if (!user) return { state: 'unauthenticated' }

    const pending = await db.outbox.orderBy('changedAtEpochMs').toArray()
    let pushed = 0

    for (const item of pending) {
      const remoteTable = supabase.from(item.entity) as any
      if (item.operation === 'UPSERT') {
        const row = toRemote(item.entity, item.payload as Record<string, unknown>, user.id)
        const { error } = await remoteTable.upsert(row, { onConflict: 'id' })
        if (error) throw error
      } else {
        const { error } = await remoteTable
          .update({ deleted_at: new Date(item.changedAtEpochMs).toISOString(), device_id: 'web' })
          .eq('id', item.entityId)
        if (error) throw error
      }
      await db.outbox.delete(item.id)
      pushed += 1
    }

    const tables: SyncEntity[] = ['study_cycles', 'subjects', 'class_sessions', 'note_pages', 'grade_items']
    const lastPull = Number(localStorage.getItem(LAST_PULL_KEY) ?? 0)
    const since = new Date(Math.max(0, lastPull - 5000)).toISOString()
    let pulled = 0

    for (const table of tables) {
      const remoteTable = supabase.from(table) as any
      const { data, error } = await remoteTable.select('*').gt('updated_at', since).order('updated_at')
      if (error) throw error
      for (const row of data ?? []) {
        await applyRemote(table, row as Record<string, unknown>)
        pulled += 1
      }
    }

    localStorage.setItem(LAST_PULL_KEY, String(Date.now()))
    return { state: 'synced', pushed, pulled }
  } catch (error) {
    return { state: 'error', message: error instanceof Error ? error.message : String(error) }
  }
}
