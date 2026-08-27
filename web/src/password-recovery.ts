import { supabase } from './lib/supabase'

const APP_URL = `${window.location.origin}${window.location.pathname}`

async function requestPasswordReset() {
  if (!supabase) {
    window.alert('La sincronización todavía no está configurada.')
    return
  }

  const emailInput = document.querySelector<HTMLInputElement>('.auth-form input[type="email"]')
  const email = emailInput?.value.trim() || window.prompt('Escribe el correo de tu cuenta NotCan:')?.trim() || ''
  if (!email) return

  const { error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: APP_URL,
  })

  if (error) {
    window.alert(`No se pudo enviar el correo de recuperación: ${error.message}`)
    return
  }

  window.alert('Te enviamos un correo para restablecer tu contraseña. Revisa también Spam o Correo no deseado.')
}

async function completePasswordRecovery() {
  if (!supabase) return

  const first = window.prompt('Escribe tu nueva contraseña para NotCan. Debe tener al menos 8 caracteres:')
  if (!first) return
  if (first.length < 8) {
    window.alert('La contraseña debe tener al menos 8 caracteres.')
    return
  }

  const second = window.prompt('Repite la nueva contraseña:')
  if (second !== first) {
    window.alert('Las contraseñas no coinciden. Vuelve a abrir el enlace de recuperación e inténtalo otra vez.')
    return
  }

  const { error } = await supabase.auth.updateUser({ password: first })
  if (error) {
    window.alert(`No se pudo cambiar la contraseña: ${error.message}`)
    return
  }

  window.history.replaceState({}, document.title, APP_URL)
  window.alert('Contraseña actualizada. Tu sesión de NotCan ya está iniciada.')
}

function addForgotPasswordButton() {
  const form = document.querySelector<HTMLFormElement>('.auth-form')
  if (!form || document.querySelector('.forgot-password-button')) return

  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'text-button forgot-password-button'
  button.textContent = 'Olvidé mi contraseña'
  button.addEventListener('click', () => void requestPasswordReset())
  form.insertAdjacentElement('afterend', button)
}

const observer = new MutationObserver(addForgotPasswordButton)
observer.observe(document.documentElement, { childList: true, subtree: true })
addForgotPasswordButton()

supabase?.auth.onAuthStateChange((event) => {
  if (event === 'PASSWORD_RECOVERY') {
    window.setTimeout(() => void completePasswordRecovery(), 250)
  }
})
