import { useMemo, useState } from 'react'
import {
  clearMistralSessionKey,
  getAiProvider,
  getLocalAiConfig,
  getMistralConfig,
  setAiProvider,
  setLocalAiConfig,
  setMistralConfig,
  type AiProvider,
} from './lib/ai'
import { clearGroqSessionKey, getGroqConfig, setGroqConfig } from './lib/apiConfig'

const providerLabels: Record<AiProvider, string> = {
  auto: 'Automático',
  mistral: 'Mistral · TuNot',
  local: 'Ollama local',
  free: 'Mis fuentes · gratuito',
}

export default function ApiSettingsPanel() {
  const initialMistral = getMistralConfig()
  const initialLocal = getLocalAiConfig()
  const initialGroq = getGroqConfig()

  const [provider, setProviderState] = useState<AiProvider>(() => getAiProvider())
  const [mistralAgent, setMistralAgent] = useState(initialMistral.agentId)
  const [mistralKey, setMistralKey] = useState('')
  const [mistralReady, setMistralReady] = useState(Boolean(initialMistral.apiKey))
  const [ollamaUrl, setOllamaUrl] = useState(initialLocal.url)
  const [ollamaModel, setOllamaModel] = useState(initialLocal.model)
  const [groqKey, setGroqKey] = useState('')
  const [groqModel, setGroqModel] = useState(initialGroq.model)
  const [groqReady, setGroqReady] = useState(Boolean(initialGroq.apiKey))
  const [message, setMessage] = useState('')

  const providerHelp = useMemo(() => provider === 'auto'
    ? 'TuNot intentará el motor local si lo configuraste; después usará la nube disponible y mantendrá como respaldo el modo basado en tus fuentes.'
    : provider === 'mistral'
      ? 'TuNot usará el Agent ID y la API key de Mistral que configures para esta sesión.'
      : provider === 'local'
        ? 'Las consultas se procesarán mediante Ollama en tu propio equipo o red local.'
        : 'TuNot se limitará a organizar y recuperar el material de NotCan sin un modelo externo.', [provider])

  function flash(text: string) {
    setMessage(text)
    window.setTimeout(() => setMessage(''), 2200)
  }

  function saveProvider(next: AiProvider) {
    setProviderState(next)
    setAiProvider(next)
    window.dispatchEvent(new Event('notcan-ai-config-changed'))
  }

  function saveMistral() {
    setMistralConfig(mistralAgent, mistralKey || undefined)
    if (mistralKey.trim()) setMistralReady(true)
    setMistralKey('')
    window.dispatchEvent(new Event('notcan-ai-config-changed'))
    flash('Configuración de Mistral guardada.')
  }

  function clearMistral() {
    clearMistralSessionKey()
    setMistralKey('')
    setMistralReady(false)
    window.dispatchEvent(new Event('notcan-ai-config-changed'))
    flash('Clave de Mistral eliminada de esta sesión.')
  }

  function saveOllama() {
    setLocalAiConfig(ollamaUrl, ollamaModel)
    window.dispatchEvent(new Event('notcan-ai-config-changed'))
    flash('Conexión local guardada.')
  }

  function saveGroq() {
    setGroqConfig(groqKey || undefined, groqModel)
    if (groqKey.trim()) setGroqReady(true)
    setGroqKey('')
    window.dispatchEvent(new Event('notcan-api-config-changed'))
    flash('Configuración de Groq guardada para esta sesión.')
  }

  function clearGroq() {
    clearGroqSessionKey()
    setGroqKey('')
    setGroqReady(false)
    window.dispatchEvent(new Event('notcan-api-config-changed'))
    flash('Clave de Groq eliminada de esta sesión.')
  }

  return <section className="api-settings section-card">
    <header className="api-settings-head">
      <div className="api-settings-icon">⌁</div>
      <div><p className="eyebrow">CONEXIONES</p><h2>APIs y motores</h2><p>Configura TuNot, transcripción y motores locales sin exponer tus claves en la página pública.</p></div>
    </header>

    <div className="api-provider-row">
      <div><strong>Motor preferido de TuNot</strong><small>{providerHelp}</small></div>
      <select value={provider} onChange={(event) => saveProvider(event.target.value as AiProvider)}>
        {(['auto', 'mistral', 'local', 'free'] as AiProvider[]).map((item) => <option value={item} key={item}>{providerLabels[item]}</option>)}
      </select>
    </div>

    <div className="api-cards">
      <article className="api-card">
        <div className="api-card-title"><div><span className="api-logo">M</span><div><strong>Mistral · TuNot</strong><small>Chat y herramientas de estudio</small></div></div><span className={`api-state ${mistralReady ? 'ready' : ''}`}>{mistralReady ? 'Clave activa' : 'Sin clave'}</span></div>
        <label><span>Agent ID</span><input value={mistralAgent} onChange={(event) => setMistralAgent(event.target.value)} placeholder="ag:..." autoComplete="off" /></label>
        <label><span>API key</span><input type="password" value={mistralKey} onChange={(event) => setMistralKey(event.target.value)} placeholder={mistralReady ? 'Clave configurada · escribe otra para reemplazarla' : 'Pega tu clave de Mistral'} autoComplete="new-password" /></label>
        <div className="api-actions"><button className="primary" onClick={saveMistral}>Guardar</button><button onClick={clearMistral} disabled={!mistralReady}>Quitar clave</button></div>
        <p className="api-security-note">La clave permanece únicamente en <strong>sessionStorage</strong>: sobrevive a una recarga, pero se elimina al cerrar la sesión del navegador o al pulsar “Quitar clave”.</p>
      </article>

      <article className="api-card">
        <div className="api-card-title"><div><span className="api-logo groq">G</span><div><strong>Groq · Whisper</strong><small>Transcripción final online</small></div></div><span className={`api-state ${groqReady ? 'ready' : ''}`}>{groqReady ? 'Clave activa' : 'Sin clave'}</span></div>
        <label><span>Modelo</span><select value={groqModel} onChange={(event) => setGroqModel(event.target.value)}><option value="whisper-large-v3">Whisper Large V3</option><option value="whisper-large-v3-turbo">Whisper Large V3 Turbo</option></select></label>
        <label><span>API key</span><input type="password" value={groqKey} onChange={(event) => setGroqKey(event.target.value)} placeholder={groqReady ? 'Clave configurada · escribe otra para reemplazarla' : 'Pega tu clave de Groq'} autoComplete="new-password" /></label>
        <div className="api-actions"><button className="primary" onClick={saveGroq}>Guardar</button><button onClick={clearGroq} disabled={!groqReady}>Quitar clave</button></div>
        <p className="api-security-note">La web ya queda preparada para reutilizar esta configuración cuando activemos grabación/subida de audio en navegador. La clave no se inserta en el bundle ni se sincroniza con Supabase.</p>
      </article>

      <article className="api-card">
        <div className="api-card-title"><div><span className="api-logo local">O</span><div><strong>Ollama local</strong><small>IA sin enviar contenido a un proveedor externo</small></div></div><span className="api-state ready">Local</span></div>
        <label><span>URL</span><input value={ollamaUrl} onChange={(event) => setOllamaUrl(event.target.value)} placeholder="http://127.0.0.1:11434" /></label>
        <label><span>Modelo</span><input value={ollamaModel} onChange={(event) => setOllamaModel(event.target.value)} placeholder="qwen3:1.7b" /></label>
        <div className="api-actions"><button className="primary" onClick={saveOllama}>Guardar conexión</button></div>
        <p className="api-security-note">No contiene una clave. Si accedes desde otra tablet o PC, Ollama debe estar disponible en la misma red y aceptar conexiones desde el navegador.</p>
      </article>

      <article className="api-card managed">
        <div className="api-card-title"><div><span className="api-logo supa">S</span><div><strong>Supabase</strong><small>Cuenta, sincronización y funciones de NotCan</small></div></div><span className="api-state ready">Gestionado</span></div>
        <p>Las credenciales públicas de conexión se suministran durante el despliegue y las claves privadas permanecen en el backend. No necesitas pegar ninguna clave de Supabase aquí.</p>
      </article>
    </div>

    {message && <div className="api-toast">✓ {message}</div>}
  </section>
}
