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

const providerLabels: Record<AiProvider, string> = {
  auto: 'Automático',
  local: 'Local (Ollama)',
  mistral: 'Mistral · TuNot',
  free: 'Gratuito · mis fuentes',
}

function buildProviderPanel() {
  const host = document.querySelector<HTMLElement>('.ai-main-card')
  if (!host || host.querySelector('.ai-provider-panel')) return

  const panel = document.createElement('section')
  panel.className = 'ai-provider-panel'

  const heading = document.createElement('div')
  heading.className = 'ai-provider-heading'
  heading.innerHTML = '<div><strong>Motor de TuNot</strong><small>Usa Mistral, Ollama local o el modo gratuito basado en tus fuentes.</small></div>'

  const select = document.createElement('select')
  select.className = 'ai-provider-select'
  ;(['auto', 'mistral', 'local', 'free'] as AiProvider[]).forEach((provider) => {
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

  const mistralSettings = document.createElement('div')
  mistralSettings.className = 'ai-local-settings ai-mistral-settings'
  const mistral = getMistralConfig()
  mistralSettings.innerHTML = `
    <label><span>Agent ID de Mistral</span><input class="ai-mistral-agent" value="${mistral.agentId}" placeholder="ag:..."></label>
    <label><span>API key de Mistral</span><input class="ai-mistral-key" type="password" value="" placeholder="Se guarda solo mientras esta pestaña/sesión siga abierta"></label>
    <div class="ai-provider-actions">
      <button type="button" class="secondary ai-mistral-save">Guardar para esta sesión</button>
      <button type="button" class="secondary ai-mistral-clear">Borrar clave</button>
    </div>
    <small>La API key no se guarda de forma permanente en el navegador: se envía por HTTPS a la función autenticada de NotCan únicamente para realizar la consulta.</small>
  `
  panel.appendChild(mistralSettings)

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
    mistralSettings.classList.toggle('visible', provider === 'mistral' || provider === 'auto')
    help.textContent = provider === 'auto'
      ? 'Automático prueba Ollama si configuraste una URL; después usa Mistral si hay credenciales en esta sesión y, como respaldo, el modo gratuito basado en tus apuntes.'
      : provider === 'local'
        ? 'Las consultas se procesan en tu propio equipo mediante Ollama.'
        : provider === 'mistral'
          ? 'Usa el mismo motor TuNot/Mistral de la APK. Debes indicar tu Agent ID y una API key para esta sesión.'
          : 'No usa una API de IA externa: extrae y organiza información de los apuntes que hayas enviado como contexto.'
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

  mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-save')?.addEventListener('click', () => {
    const agentId = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-agent')?.value || ''
    const key = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')?.value || ''
    setMistralConfig(agentId, key || undefined)
    const button = mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-save')
    if (button) {
      const original = button.textContent
      button.textContent = key || getMistralConfig().apiKey ? '✓ Listo' : 'Agent ID guardado'
      window.setTimeout(() => { button.textContent = original }, 1400)
    }
    const keyInput = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')
    if (keyInput) keyInput.value = ''
  })

  mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-clear')?.addEventListener('click', () => {
    clearMistralSessionKey()
    const input = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')
    if (input) input.value = ''
  })

  refreshProviderUi()

  const welcome = host.querySelector('.ai-welcome-row')
  if (welcome?.nextSibling) host.insertBefore(panel, welcome.nextSibling)
  else host.prepend(panel)
}

const observer = new MutationObserver(buildProviderPanel)
observer.observe(document.documentElement, { childList: true, subtree: true })
buildProviderPanel()
