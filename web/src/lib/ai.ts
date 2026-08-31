import { supabase } from './supabase'

export type AiProvider = 'auto' | 'local' | 'mistral' | 'free'

export type AiContextItem = {
  title: string
  body: string
  subject?: string
  classTitle?: string
}

export type AiRequest = {
  prompt: string
  context?: AiContextItem[]
  mode?: 'chat' | 'summary' | 'questions' | 'concept-map'
  provider?: AiProvider
}

export type AiResponse = {
  answer: string
  model?: string
  provider?: string
}

const PROVIDER_KEY = 'notcan-ai-provider'
const LOCAL_URL_KEY = 'notcan-ai-local-url'
const LOCAL_MODEL_KEY = 'notcan-ai-local-model'
const MISTRAL_AGENT_KEY = 'notcan-mistral-agent-id'
const MISTRAL_API_SESSION_KEY = 'notcan-mistral-api-key-session'

export function getAiProvider(): AiProvider {
  const value = localStorage.getItem(PROVIDER_KEY)
  return ['auto', 'local', 'mistral', 'free'].includes(value || '')
    ? value as AiProvider
    : 'auto'
}

export function setAiProvider(provider: AiProvider) {
  localStorage.setItem(PROVIDER_KEY, provider)
}

export function getLocalAiConfig() {
  return {
    url: localStorage.getItem(LOCAL_URL_KEY) || 'http://127.0.0.1:11434',
    model: localStorage.getItem(LOCAL_MODEL_KEY) || 'qwen3:1.7b',
  }
}

export function setLocalAiConfig(url: string, model: string) {
  localStorage.setItem(LOCAL_URL_KEY, url.trim().replace(/\/$/, ''))
  localStorage.setItem(LOCAL_MODEL_KEY, model.trim() || 'qwen3:1.7b')
}

export function getMistralConfig() {
  return {
    agentId: localStorage.getItem(MISTRAL_AGENT_KEY) || '',
    apiKey: sessionStorage.getItem(MISTRAL_API_SESSION_KEY) || '',
  }
}

export function setMistralConfig(agentId: string, apiKey?: string) {
  localStorage.setItem(MISTRAL_AGENT_KEY, agentId.trim())
  if (apiKey !== undefined) {
    const clean = apiKey.trim()
    if (clean) sessionStorage.setItem(MISTRAL_API_SESSION_KEY, clean)
    else sessionStorage.removeItem(MISTRAL_API_SESSION_KEY)
  }
}

export function clearMistralSessionKey() {
  sessionStorage.removeItem(MISTRAL_API_SESSION_KEY)
}

function buildLocalPrompt(request: AiRequest) {
  const modeInstruction = request.mode === 'summary'
    ? 'Resume el material de forma clara, fiel y útil para estudiar.'
    : request.mode === 'questions'
      ? 'Crea preguntas de estudio con sus respuestas basadas en el material disponible.'
      : request.mode === 'concept-map'
        ? 'Organiza la respuesta como mapa conceptual textual, con concepto central, nodos y relaciones.'
        : 'Responde la consulta académica con claridad y precisión.'

  const context = (request.context || []).slice(0, 8).map((item, index) =>
    `[Fuente ${index + 1}] ${item.title}\n${item.subject ? `Materia: ${item.subject}\n` : ''}${item.classTitle ? `Clase: ${item.classTitle}\n` : ''}${item.body.slice(0, 7000)}`,
  ).join('\n\n')

  return `Eres TuNot, el asistente académico de NotCan. Responde en español claro; no inventes autores, citas, páginas ni referencias; si hay material de NotCan, úsalo como contexto y distingue lo que procede de las fuentes. Conserva literalmente los textos entre comillas cuando debas citarlos.\nTarea: ${modeInstruction}\n\nConsulta del estudiante:\n${request.prompt}${context ? `\n\nMaterial disponible en NotCan:\n${context}` : ''}`
}

async function askLocal(request: AiRequest): Promise<AiResponse> {
  const { url, model } = getLocalAiConfig()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 12000)

  try {
    const response = await fetch(`${url}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        prompt: buildLocalPrompt(request),
        stream: false,
        options: { num_predict: 2048 },
      }),
      signal: controller.signal,
    })

    if (!response.ok) throw new Error(`Ollama respondió ${response.status}`)
    const data = await response.json()
    const answer = String(data?.response || '').trim()
    if (!answer) throw new Error('La IA local no devolvió texto.')
    return { answer, model, provider: 'local' }
  } finally {
    window.clearTimeout(timeout)
  }
}

async function askCloud(request: AiRequest, provider: 'auto' | 'mistral' | 'free'): Promise<AiResponse> {
  if (!supabase) throw new Error('Supabase no está configurado.')

  const { data: sessionData } = await supabase.auth.getSession()
  if (!sessionData.session) throw new Error('Inicia sesión para usar NotCan AI en la nube.')

  const mistral = getMistralConfig()
  if (provider === 'mistral' && (!mistral.agentId || !mistral.apiKey)) {
    throw new Error('Configura el Agent ID y la API key de Mistral. La clave se conserva solo durante esta sesión del navegador.')
  }

  const { data, error } = await supabase.functions.invoke('notcan-ai', {
    body: {
      ...request,
      provider,
      mistralAgentId: mistral.agentId || undefined,
      mistralApiKey: mistral.apiKey || undefined,
    },
  })

  if (error) throw new Error(error.message || 'No se pudo conectar con NotCan AI.')
  if (!data?.answer) throw new Error(data?.error || 'NotCan AI no devolvió una respuesta.')

  return {
    answer: String(data.answer),
    model: data.model ? String(data.model) : undefined,
    provider: data.provider ? String(data.provider) : provider,
  }
}

export async function askNotCanAi(request: AiRequest): Promise<AiResponse> {
  const selected = request.provider || getAiProvider()

  if (selected === 'local') {
    try {
      return await askLocal(request)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      throw new Error(`No pude conectar con la IA local. Comprueba que Ollama esté encendido y que la URL/modelo sean correctos. ${message}`)
    }
  }

  if (selected === 'auto') {
    if (localStorage.getItem(LOCAL_URL_KEY)) {
      try {
        return await askLocal(request)
      } catch {
        // Si el equipo local no está disponible, continuamos con Mistral (si está configurado)
        // o con el modo gratuito basado en las fuentes de NotCan.
      }
    }
    return askCloud(request, 'auto')
  }

  return askCloud(request, selected)
}
