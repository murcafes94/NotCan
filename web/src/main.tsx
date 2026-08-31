import React from 'react'
import ReactDOM from 'react-dom/client'
import { registerSW } from 'virtual:pwa-register'
import App from './App'
import './password-recovery'
import './ai-provider-ui'
import './styles.css'
import './editor-account.css'
import './features.css'
import './academic-theme.css'
import './tablet-responsive.css'
import './rich-editor-ai.css'
import './ai-provider-ui.css'

registerSW({ immediate: true })

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
