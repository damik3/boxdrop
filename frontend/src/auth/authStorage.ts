import type { AuthState } from './types'

const AUTH_STORAGE_KEY = 'dropbox-clone.auth'

export function readAuthState(): AuthState | null {
  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthState
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function writeAuthState(authState: AuthState): void {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authState))
}

export function clearAuthState(): void {
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}
