import {
  getAiProvider,
  getLocalAiConfig,
  setAiProvider,
  setLocalAiConfig,
  type AiProvider,
} from './lib/ai'

const providerLabels: Record<AiProvider, string> = {
  auto: 'Automático',
  local: 'Local (Ollama)',
  deepseek: 'DeepSeek',
  gemini: 'Gemini',
  openai: 'OpenAI',
}

function buildProviderPanel() {
  const host = document.querySelector<HTMLElement>('.ai-main-card')
  if (!host || host.querySelector('.ai-provider-panel')) return

  const panel = document.createElement('section')
  panel.className = 'ai-provider-panel'

  const heading = document.createElement('div')
  heading.className = 'ai-provider-heading'
  heading.innerHTML = '<div><strong>Proveedor de IA</strong><small>Elige qué motor usa NotCan AI.</small></div>'

  const select = document.createElement('select')
  select.className = 'ai-provider-select'
  ;(['auto', 'local', 'deepseek', 'gemini', 'openai'] as AiProvider[]).forEach((provider) => {
    const option = document.createElement('option')
    option.value = provider
    option.textContent = providerLabels[provider]
    select.appendChild(option)
  })
  select.value = getAiProvider()
  heading.appendChild(select)
  panel.appendChild(heading)

  const help = document.createElement('p')
  help.className = 'ai-provider-help'
  panel.appendChild(help)

  const localSettings = document.createElement('div')
  localSettings.className = 'ai-local-settings'
  const config = getLocalAiConfig()
  localSettings.innerHTML = `
    <label><span>URL de Ollama</span><input class="ai-local-url" value="${config.url}" placeholder="http://127.0.0.1:11434"></label>
    <label><span>Modelo local</span><input class="ai-local-model" value="${config.model}" placeholder="qwen3:1.7b"></label>
    <button type="button" class="secondary ai-local-save">Guardar conexión local</button>
    <small>Para usar la IA local desde otra tablet, Ollama debe estar accesible en la misma red y permitir conexiones desde el navegador.</small>
  `
  panel.appendChild(localSettings)

  function refreshProviderUi() {
    const provider = select.value as AiProvider
    localSettings.classList.toggle('visible', provider === 'local' || provider === 'auto')
    help.textContent = provider === 'auto'
      ? 'Automático intenta la IA local si configuraste una URL; si no está disponible, usa DeepSeek, Gemini u OpenAI según las claves configuradas.'
      : provider === 'local'
        ? 'Las consultas se procesan en tu propio equipo mediante Ollama. No necesitan una clave cloud.'
        : `${providerLabels[provider]} queda fijado como proveedor para las próximas consultas.`
  }

  select.addEventListener('change', () => {
    setAiProvider(select.value as AiProvider)
    refreshProviderUi()
  })

  localSettings.querySelector<HTMLButtonElement>('.ai-local-save')?.addEventListener('click', () => {
    const url = localSettings.querySelector<HTMLInputElement>('.ai-local-url')?.value || ''
    const model = localSettings.querySelector<HTMLInputElement>('.ai-local-model')?.value || ''
    setLocalAiConfig(url, model)
    const button = localSettings.querySelector<HTMLButtonElement>('.ai-local-save')
    if (button) {
      const original = button.textContent
      button.textContent = '✓ Guardado'
      window.setTimeout(() => { button.textContent = original }, 1400)
    }
  })

  refreshProviderUi()

  const welcome = host.querySelector('.ai-welcome-row')
  if (welcome?.nextSibling) host.insertBefore(panel, welcome.nextSibling)
  else host.prepend(panel)
}

const observer = new MutationObserver(buildProviderPanel)
observer.observe(document.documentElement, { childList: true, subtree: true })
buildProviderPanel()
