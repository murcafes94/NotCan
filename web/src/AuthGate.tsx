import { type FormEvent, type ReactNode, useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './lib/supabase'

export default function AuthGate({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!supabase) {
      setLoading(false)
      return
    }

    void supabase.auth.getSession().then(({ data }) => {
      setSession(data.session)
      setLoading(false)
    })

    const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
    })

    return () => listener.subscription.unsubscribe()
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!supabase || busy) return
    setBusy(true)
    setMessage('')

    try {
      if (mode === 'login') {
        const { error } = await supabase.auth.signInWithPassword({ email, password })
        if (error) throw error
      } else {
        const { data, error } = await supabase.auth.signUp({ email, password })
        if (error) throw error
        if (!data.session) {
          setMessage('Cuenta creada. Revisa tu correo para confirmar el acceso y luego inicia sesión.')
          setMode('login')
        }
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error))
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return <div className="auth-screen"><div className="auth-card"><strong>NotCan</strong><p>Preparando tu espacio…</p></div></div>
  }

  if (!supabase) {
    return <>{children}</>
  }

  if (!session) {
    return (
      <div className="auth-screen">
        <section className="auth-card">
          <div className="auth-logo">N</div>
          <p className="eyebrow">NOTCAN WEB</p>
          <h1>{mode === 'login' ? 'Inicia sesión' : 'Crea tu cuenta'}</h1>
          <p className="auth-copy">La misma cuenta será la llave para sincronizar NotCan entre la web y Android.</p>
          <form onSubmit={submit} className="auth-form">
            <label>Correo<input type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></label>
            <label>Contraseña<input type="password" minLength={8} required autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(e) => setPassword(e.target.value)} /></label>
            <button className="primary" disabled={busy}>{busy ? 'Procesando…' : mode === 'login' ? 'Entrar' : 'Crear cuenta'}</button>
          </form>
          {message && <p className="auth-message">{message}</p>}
          <button className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setMessage('') }}>
            {mode === 'login' ? 'Crear una cuenta nueva' : 'Ya tengo una cuenta'}
          </button>
        </section>
      </div>
    )
  }

  return (
    <>
      <div className="account-strip">
        <span>☁ {session.user.email}</span>
        <button className="text-button" onClick={() => void supabase.auth.signOut()}>Cerrar sesión</button>
      </div>
      {children}
    </>
  )
}
