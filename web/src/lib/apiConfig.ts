const GROQ_API_SESSION_KEY = 'notcan-groq-api-key-session'
const GROQ_MODEL_KEY = 'notcan-groq-model'

export function getGroqConfig() {
  return {
    apiKey: sessionStorage.getItem(GROQ_API_SESSION_KEY) || '',
    model: localStorage.getItem(GROQ_MODEL_KEY) || 'whisper-large-v3',
  }
}

export function setGroqConfig(apiKey?: string, model?: string) {
  if (apiKey !== undefined) {
    const clean = apiKey.trim()
    if (clean) sessionStorage.setItem(GROQ_API_SESSION_KEY, clean)
    else sessionStorage.removeItem(GROQ_API_SESSION_KEY)
  }
  if (model !== undefined) {
    localStorage.setItem(GROQ_MODEL_KEY, model.trim() || 'whisper-large-v3')
  }
}

export function clearGroqSessionKey() {
  sessionStorage.removeItem(GROQ_API_SESSION_KEY)
}
