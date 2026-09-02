import type { Session } from '@supabase/supabase-js'
import CycleManagementPanel from './CycleManagementPanel'
import type { StudyCycleRecord } from './types/sync'

type Props = {
  theme: 'light' | 'dark'
  onThemeChange: (theme: 'light' | 'dark') => void
  session: Session | null
  pending: number
  syncText: string
  syncKind: 'good' | 'warn' | 'muted'
  onSync: () => void | Promise<void>
  onAccount: () => void
  cycles: StudyCycleRecord[]
  onCyclesChanged: () => void | Promise<void>
}

export default function UnifiedSettings({ theme, onThemeChange, session, pending, syncText, syncKind, onSync, onAccount, cycles, onCyclesChanged }: Props) {
  return <div className="unified-settings">
    <section className="settings-hero">
      <div><p className="eyebrow">NOTCAN</p><h1>Ajustes</h1><p>Cuenta, sincronización, apariencia, estudio y ciclos en un solo lugar.</p></div>
      <span className={`status-pill ${syncKind}`}>{session ? syncText : 'Solo local'}</span>
    </section>

    <div className="settings-grid">
      <section className="settings-group section-card">
        <header><span>◐</span><div><h2>Apariencia</h2><p>Elige cómo se ve NotCan en este navegador.</p></div></header>
        <div className="settings-segmented" role="group" aria-label="Tema">
          <button className={theme === 'light' ? 'selected' : ''} onClick={() => onThemeChange('light')}>☀ Claro</button>
          <button className={theme === 'dark' ? 'selected' : ''} onClick={() => onThemeChange('dark')}>☾ Oscuro</button>
        </div>
      </section>

      <section className="settings-group section-card">
        <header><span>○</span><div><h2>Cuenta y sincronización</h2><p>{session?.user.email ?? 'Trabajas localmente en este dispositivo.'}</p></div></header>
        <div className="settings-data-row"><span>Estado</span><strong>{session ? syncText : 'Sin cuenta'}</strong></div>
        <div className="settings-data-row"><span>Cambios pendientes</span><strong>{pending}</strong></div>
        <div className="settings-buttons"><button className="primary" onClick={() => void onSync()}>{session ? '↻ Sincronizar ahora' : 'Iniciar sesión'}</button><button onClick={onAccount}>{session ? 'Gestionar cuenta' : 'Crear cuenta'}</button></div>
      </section>

      <section className="settings-group section-card">
        <header><span>✎</span><div><h2>Apuntes</h2><p>Comportamiento del editor web.</p></div></header>
        <div className="settings-toggle-row"><div><strong>Autoguardado</strong><small>Guarda mientras escribes y conserva un borrador de recuperación.</small></div><span className="status-pill">Activo</span></div>
        <div className="settings-toggle-row"><div><strong>Formato por selección</strong><small>Resaltado, tamaño, alineación y estilos sobre el texto seleccionado.</small></div><span className="status-pill">Activo</span></div>
        <div className="settings-toggle-row"><div><strong>Trabajo local-first</strong><small>Puedes seguir escribiendo aunque se interrumpa Internet.</small></div><span className="status-pill">Activo</span></div>
      </section>

      <section className="settings-group section-card">
        <header><span>✦</span><div><h2>TuNot</h2><p>Asistente de estudio de NotCan.</p></div></header>
        <div className="settings-data-row"><span>Interfaz</span><strong>Chat</strong></div>
        <div className="settings-data-row"><span>Contexto</span><strong>Apuntes del ciclo activo</strong></div>
        <p className="settings-note">En la web, las consultas al proveedor de IA requieren una cuenta conectada. El material local no se envía salvo cuando activas “Usar mis apuntes”.</p>
      </section>
    </div>

    <CycleManagementPanel cycles={cycles} onChanged={onCyclesChanged} />
  </div>
}
