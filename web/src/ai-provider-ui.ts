import {
  getAiProvider,
  getLocalAiConfig,
  setAiProvider,
  setLocalAiConfig,
  type AiProvider,
} from './lib/ai'

const providerLabels: Record<AiProvider, string> = {
  tunot: 'TuNot (gratis)',
  local: 'Local (Ollama)',
}

function replaceExactText(root: ParentNode, from: string, to: string) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const nodes: Text[] = []
  while (walker.nextNode()) nodes.push(walker.currentNode as Text)
  for (const node of nodes) {
    if (node.nodeValue?.includes(from)) node.nodeValue = node.nodeValue.replaceAll(from, to)
  }
}

function brandTuNot() {
  replaceExactText(document.body, 'NotCan AI', 'TuNot')

  document.querySelectorAll<HTMLElement>('.settings-list article').forEach((article) => {
    if (article.querySelector('strong')?.textContent?.trim() !== 'TuNot') return
    const description = article.querySelector('p')
    const status = article.querySelector<HTMLElement>('.status-pill')
    if (description) description.textContent = 'TuNot usa tus apuntes y el backend gratuito de Supabase. No requiere Gemini, OpenAI ni otra API de pago.'
    if (status) status.textContent = 'Gratis'
  })
}

function buildProviderPanel() {
  brandTuNot()

  const host = document.querySelector<HTMLElement>('.ai-main-card')
  if (!host || host.querySelector('.ai-provider-panel')) return

  const panel = document.createElement('section')
  panel.className = 'ai-provider-panel'

  const heading = document.createElement('div')
  heading.className = 'ai-provider-heading'
  heading.innerHTML = '<div><strong>Motor de TuNot</strong><small>La opción principal no necesita APIs de pago.</small></div>'

  const select = document.createElement('select')
  select.className = 'ai-provider-select'
  ;(['tunot', 'local'] as AiProvider[]).forEach((provider) => {
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
    <small>Ollama es opcional. TuNot funciona sin él usando tus fuentes y Supabase dentro del plan gratuito.</small>
  `
  panel.appendChild(localSettings)

  function refreshProviderUi() {
    const provider = select.value as AiProvider
    localSettings.classList.toggle('visible', provider === 'local')
    help.textContent = provider === 'tunot'
      ? 'TuNot prioriza tus apuntes y materiales. Si el servidor no está disponible, conserva una respuesta básica basada en las fuentes desde el navegador.'
      : 'Las consultas se procesan en tu propio equipo mediante Ollama. No necesitan una clave cloud.'
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
