import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined

export const isSupabaseConfigured = Boolean(url && anonKey)
export const NOTCAN_PUBLIC_URL = 'https://murcafes94.github.io/NotCan/'

function createNotCanClient(): SupabaseClient | null {
  if (!isSupabaseConfigured) return null

  const client = createClient(url!, anonKey!, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: true,
    },
  })

  const originalSignUp = client.auth.signUp.bind(client.auth)
  client.auth.signUp = ((credentials: Parameters<typeof originalSignUp>[0]) => {
    const existingOptions = 'options' in credentials ? credentials.options : undefined
    return originalSignUp({
      ...credentials,
      options: {
        ...existingOptions,
        emailRedirectTo: NOTCAN_PUBLIC_URL,
      },
    } as Parameters<typeof originalSignUp>[0])
  }) as typeof client.auth.signUp

  return client
}

export const supabase: SupabaseClient | null = createNotCanClient()
