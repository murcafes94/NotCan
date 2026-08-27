import { supabase } from './supabase'

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
}

export type AiResponse = {
  answer: string
  model?: string
}

export async function askNotCanAi(request: AiRequest): Promise<AiResponse> {
  if (!supabase) throw new Error('Supabase no está configurado.')

  const { data: sessionData } = await supabase.auth.getSession()
  if (!sessionData.session) throw new Error('Inicia sesión para usar NotCan AI.')

  const { data, error } = await supabase.functions.invoke('notcan-ai', {
    body: request,
  })

  if (error) {
    const message = error.message || 'No se pudo conectar con NotCan AI.'
    throw new Error(message)
  }

  if (!data?.answer) {
    throw new Error(data?.error || 'NotCan AI no devolvió una respuesta.')
  }

  return {
    answer: String(data.answer),
    model: data.model ? String(data.model) : undefined,
  }
}
