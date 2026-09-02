import { useEffect, useMemo, useRef, useState } from 'react'
import MarkdownMessage from './MarkdownMessage'

type ChatMode = 'chat' | 'summary' | 'questions' | 'concept-map'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  text: string
  model?: string
}

type Props = {
  connected: boolean
  contextCount: number
  useContext: boolean
  initialPrompt?: string
  onInitialPromptConsumed?: () => void
  onUseContextChange: (value: boolean) => void
  onAsk: (prompt: string, mode: ChatMode, useContextOverride?: boolean) => Promise<{ answer: string; model?: string }>
  onOpenAccount: () => void
}

const starters: { mode: ChatMode; label: string; prompt: string }[] = [
  { mode: 'summary', label: 'Resumir', prompt: 'Resume mis apuntes recientes y organiza las ideas principales por tema.' },
  { mode: 'questions', label: 'Preguntas', prompt: 'Crea 10 preguntas de estudio con sus respuestas a partir de mis apuntes recientes.' },
  { mode: 'concept-map', label: 'Mapa', prompt: 'Construye un mapa conceptual textual a partir de mis apuntes recientes, indicando nodos y relaciones.' },
]

export default function TuNotChat({ connected, contextCount, useContext, initialPrompt = '', onInitialPromptConsumed, onUseContextChange, onAsk, onOpenAccount }: Props) {
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const endRef = useRef<HTMLDivElement>(null)
  const hasConversation = messages.length > 0
  const contextLabel = useMemo(() => {
    if (!useContext) return 'Sin contexto de apuntes'
    return `${contextCount} ${contextCount === 1 ? 'apunte' : 'apuntes'} del ciclo`
  }, [contextCount, useContext])

  useEffect(() => {
    const prompt = initialPrompt.trim()
    if (!prompt) return
    setDraft(prompt)
    onInitialPromptConsumed?.()
  }, [initialPrompt, onInitialPromptConsumed])

  useEffect(() => {
    if (!hasConversation && !busy) return
    requestAnimationFrame(() => endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }))
  }, [messages, busy, hasConversation])

  async function send(prompt = draft, mode: ChatMode = 'chat') {
    const text = prompt.trim()
    if (!text || busy) return
    if (!connected) {
      setError('Inicia sesión para usar TuNot en la web.')
      onOpenAccount()
      return
    }

    const studyAction = mode !== 'chat'
    if (studyAction && contextCount === 0) {
      setError('Todavía no hay apuntes en el ciclo activo para realizar esta acción.')
      return
    }

    const shouldUseContext = studyAction && contextCount > 0 ? true : useContext
    if (shouldUseContext && !useContext) onUseContextChange(true)

    setBusy(true)
    setError('')
    setDraft('')
    const userMessage: ChatMessage = { id: crypto.randomUUID(), role: 'user', text }
    setMessages((prev) => [...prev, userMessage])
    try {
      const result = await onAsk(text, mode, shouldUseContext)
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), role: 'assistant', text: result.answer, model: result.model }])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return <main className="tunot-chat-page">
    <header className="tunot-chat-head">
      <div className="tunot-identity"><div className="tunot-orb">✦</div><div><p className="eyebrow">TU BIBLIOTECA DE ESTUDIO</p><h1>TuNot</h1><p>Pregunta, resume y estudia con tus propios apuntes.</p></div></div>
      <div className="tunot-head-actions"><span className={`status-pill ${connected ? '' : 'muted'}`}>{connected ? 'En línea' : 'Local'}</span><button className="tunot-new-chat" onClick={() => { setMessages([]); setError(''); setDraft('') }}>＋ Nuevo chat</button></div>
    </header>

    <section className={`tunot-conversation ${hasConversation ? 'has-messages' : 'empty-chat'}`}>
      {!hasConversation && <div className="tunot-empty-state">
        <div className="tunot-large-orb">✦</div>
        <h2>¿Qué quieres estudiar?</h2>
        <p>Puedo usar el contenido del ciclo activo para ayudarte a comprender, repasar y practicar.</p>
        <div className="tunot-starters">{starters.map((starter) => <button key={starter.mode} onClick={() => void send(starter.prompt, starter.mode)}><span>✦</span><strong>{starter.label}</strong><small>{starter.prompt}</small></button>)}</div>
      </div>}

      {messages.map((message) => <article key={message.id} className={`tunot-message ${message.role}`}>
        <div className="tunot-message-avatar">{message.role === 'assistant' ? '✦' : 'Tú'}</div>
        <div className="tunot-bubble">
          <div className="tunot-message-meta"><strong>{message.role === 'assistant' ? 'TuNot' : 'Tú'}</strong>{message.model && <small>{message.model}</small>}</div>
          <div className="tunot-message-text">{message.role === 'assistant' ? <MarkdownMessage text={message.text} /> : message.text}</div>
          {message.role === 'assistant' && <div className="tunot-message-actions"><button onClick={() => void navigator.clipboard.writeText(message.text)}>Copiar</button></div>}
        </div>
      </article>)}
      {busy && <article className="tunot-message assistant"><div className="tunot-message-avatar">✦</div><div className="tunot-bubble thinking"><span /><span /><span /></div></article>}
      <div ref={endRef} />
    </section>

    <footer className="tunot-composer-dock">
      {error && <div className="tunot-error">{error}</div>}
      <div className="tunot-context-row"><label><input type="checkbox" checked={useContext} disabled={contextCount === 0} onChange={(event) => onUseContextChange(event.target.checked)} /><span>Usar mis apuntes</span></label><small>{contextLabel}</small></div>
      <div className="tunot-composer"><textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Escribe a TuNot…" rows={1} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send() } }} /><button disabled={!draft.trim() || busy} onClick={() => void send()}>{busy ? '…' : '↑'}</button></div>
      <small className="tunot-disclaimer">TuNot puede equivocarse. Para estudiar, verifica siempre con tus fuentes.</small>
    </footer>
  </main>
}
