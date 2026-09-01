import type { AuthFormValues, AuthResponse } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

async function postAuth(path: 'login' | 'register', values: AuthFormValues): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(values),
  })

  if (!response.ok) {
    const errorMessage = await response.text()
    throw new Error(errorMessage || 'Authentication request failed')
  }

  return response.json() as Promise<AuthResponse>
}

export function login(values: AuthFormValues): Promise<AuthResponse> {
  return postAuth('login', values)
}

export function register(values: AuthFormValues): Promise<AuthResponse> {
  return postAuth('register', values)
}
