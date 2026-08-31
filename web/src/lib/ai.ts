import { supabase } from './supabase'

export type AiProvider = 'tunot' | 'local'

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

export function getAiProvider(): AiProvider {
  return localStorage.getItem(PROVIDER_KEY) === 'local' ? 'local' : 'tunot'
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

  return `Eres TuNot, el asistente académico de NotCan.\nReglas: responde en español claro; no inventes autores, citas, páginas ni referencias; si hay material de NotCan, priorízalo; distingue material proporcionado de conocimiento general; conserva literalmente los textos entre comillas cuando debas citarlos.\nTarea: ${modeInstruction}\n\nConsulta del estudiante:\n${request.prompt}${context ? `\n\nMaterial disponible en NotCan:\n${context}` : ''}`
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

function cleanText(value: string) {
  return value.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}

function sourceSentences(request: AiRequest) {
  return (request.context || []).flatMap((item) => cleanText(item.body)
    .split(/(?<=[.!?])\s+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length >= 35 && sentence.length <= 700)
    .map((sentence) => ({ sentence, title: item.title, subject: item.subject })))
}

function fallbackTuNot(request: AiRequest): AiResponse {
  const rows = sourceSentences(request)
  if (!rows.length) {
    return {
      answer: 'TuNot está disponible sin APIs de pago, pero necesita apuntes o materiales de NotCan para responder en este modo gratuito. Activa “Usar mis apuntes como contexto” o añade material a la materia.',
      model: 'TuNot gratuito · navegador',
      provider: 'tunot',
    }
  }

  if (request.mode === 'questions') {
    const answer = rows.slice(0, 6).map((row, index) =>
      `${index + 1}. Pregunta: ¿Qué explica el material sobre este punto?\n   Respuesta: ${row.sentence}`,
    ).join('\n\n')
    return { answer, model: 'TuNot gratuito · navegador', provider: 'tunot' }
  }

  if (request.mode === 'concept-map') {
    const nodes = rows.slice(0, 7).map((row, index) =>
      `${index === rows.length - 1 ? '└' : '├'}─ ${row.subject || row.title}: ${row.sentence}`,
    ).join('\n')
    return {
      answer: `NOTCAN_MAP\nConcepto central: ${request.prompt.slice(0, 120)}\n${nodes}`,
      model: 'TuNot gratuito · navegador',
      provider: 'tunot',
    }
  }

  const selected = rows.slice(0, request.mode === 'summary' ? 8 : 6)
  const heading = request.mode === 'summary'
    ? 'Resumen de TuNot basado en tus materiales:'
    : 'Según tus materiales de NotCan:'
  return {
    answer: `${heading}\n\n${selected.map((row) => `• ${row.sentence}`).join('\n\n')}`,
    model: 'TuNot gratuito · navegador',
    provider: 'tunot',
  }
}

async function askTuNot(request: AiRequest): Promise<AiResponse> {
  if (!supabase) return fallbackTuNot(request)

  const { data: sessionData } = await supabase.auth.getSession()
  if (!sessionData.session) return fallbackTuNot(request)

  try {
    const { data, error } = await supabase.functions.invoke('notcan-ai', {
      body: { ...request, provider: 'tunot' },
    })

    if (error) throw error
    if (!data?.answer) throw new Error(data?.error || 'TuNot no devolvió una respuesta.')

    return {
      answer: String(data.answer),
      model: data.model ? String(data.model) : 'TuNot gratuito',
      provider: 'tunot',
    }
  } catch {
    return fallbackTuNot(request)
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

  return askTuNot(request)
}
