import { useEffect, useMemo, useState } from 'react'
import { activateCycleLocal, deleteCycleTree, previewCycleTree, type CycleTreePreview } from './lib/db'
import type { StudyCycleRecord } from './types/sync'

type Props = { cycles: StudyCycleRecord[]; onChanged: () => Promise<void> | void }

function cycleDate(epochDay: number) {
  if (!epochDay) return 'Sin fecha'
  const date = new Date(epochDay * 86_400_000)
  return new Intl.DateTimeFormat('es-EC', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(date)
}

export default function CycleManagementPanel({ cycles, onChanged }: Props) {
  const [previews, setPreviews] = useState<Record<string, CycleTreePreview>>({})
  const [pendingDelete, setPendingDelete] = useState<StudyCycleRecord | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  const sorted = useMemo(() => [...cycles].sort((a, b) => Number(b.isActive) - Number(a.isActive) || b.createdAtEpochMs - a.createdAtEpochMs), [cycles])

  useEffect(() => {
    let cancelled = false
    void Promise.all(sorted.map(async (cycle) => [cycle.id, await previewCycleTree(cycle.id)] as const)).then((items) => {
      if (!cancelled) setPreviews(Object.fromEntries(items))
    })
    return () => { cancelled = true }
  }, [sorted])

  async function activate(id: string) {
    setBusyId(id); setMessage('')
    try { await activateCycleLocal(id); await onChanged(); setMessage('Ciclo activo actualizado. Se sincronizará con tus otros dispositivos.') }
    finally { setBusyId(null) }
  }

  async function remove(cycle: StudyCycleRecord) {
    setBusyId(cycle.id); setMessage('')
    try {
      await deleteCycleTree(cycle.id)
      setPendingDelete(null)
      await onChanged()
      setMessage(`“${cycle.name}” se eliminó de este dispositivo. La eliminación quedará en la cola de sincronización.`)
    } finally { setBusyId(null) }
  }

  const pendingPreview = pendingDelete ? previews[pendingDelete.id] : null

  return <section className="cycle-manager">
    <div className="cycle-manager-heading">
      <div><strong>Ciclos académicos</strong><p>Activa o elimina ciclos manualmente. NotCan nunca los borra por su cuenta.</p></div>
      <span>{cycles.length}</span>
    </div>
    {message && <p className="cycle-manager-message">{message}</p>}
    {sorted.length === 0 ? <div className="cycle-empty">No hay ciclos guardados. Puedes crear uno desde NotCan Android o desde la próxima pantalla de creación web.</div> :
      <div className="cycle-list">{sorted.map((cycle) => {
        const preview = previews[cycle.id]
        return <article className="cycle-row" key={cycle.id}>
          <div className="cycle-main">
            <div className="cycle-title-row"><strong>{cycle.name}</strong>{cycle.isActive && <span className="cycle-active">Activo</span>}</div>
            <small>{cycleDate(cycle.startEpochDay)} → {cycleDate(cycle.endEpochDay)}</small>
            <p>{preview ? `${preview.subjects} materias · ${preview.classes} clases · ${preview.notes} apuntes · ${preview.grades} calificaciones` : 'Calculando contenido…'}</p>
          </div>
          <div className="cycle-actions">
            {!cycle.isActive && <button disabled={busyId !== null} onClick={() => void activate(cycle.id)}>Activar</button>}
            <button className="danger-outline" disabled={busyId !== null} onClick={() => setPendingDelete(cycle)}>Eliminar</button>
          </div>
        </article>
      })}</div>}

    {pendingDelete && <div className="cycle-dialog-backdrop" role="presentation" onMouseDown={() => busyId === null && setPendingDelete(null)}>
      <div className="cycle-dialog" role="dialog" aria-modal="true" aria-labelledby="cycle-delete-title" onMouseDown={(event) => event.stopPropagation()}>
        <h3 id="cycle-delete-title">Eliminar “{pendingDelete.name}”</h3>
        <p>Esta acción elimina manualmente el ciclo y todo su contenido académico asociado.</p>
        <div className="cycle-delete-summary">
          <strong>{pendingPreview ? `${pendingPreview.subjects} materias · ${pendingPreview.classes} clases · ${pendingPreview.notes} apuntes · ${pendingPreview.grades} calificaciones` : 'Calculando contenido…'}</strong>
          <small>La eliminación se sincronizará con Android y la web. No se puede deshacer desde NotCan.</small>
        </div>
        <div className="cycle-dialog-actions">
          <button disabled={busyId !== null} onClick={() => setPendingDelete(null)}>Cancelar</button>
          <button className="danger-solid" disabled={busyId !== null} onClick={() => void remove(pendingDelete)}>{busyId === pendingDelete.id ? 'Eliminando…' : 'Eliminar definitivamente'}</button>
        </div>
      </div>
    </div>}
  </section>
}
