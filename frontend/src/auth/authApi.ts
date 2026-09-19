import type { AuthFormValues, AuthResponse } from './types'
import { parseErrorMessage, GENERIC_ERROR_MESSAGE } from '../api/common.ts'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

async function postAuth(path: 'login' | 'register', values: AuthFormValues): Promise<AuthResponse> {
  const fallback = path === 'login' ? 'Could not sign in.' : 'Could not create your account.'
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/auth/${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(values),
    })
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error(GENERIC_ERROR_MESSAGE)
    }
    throw error
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, fallback))
  }

  return response.json() as Promise<AuthResponse>
}

export function login(values: AuthFormValues): Promise<AuthResponse> {
  return postAuth('login', values)
}

export function register(values: AuthFormValues): Promise<AuthResponse> {
  return postAuth('register', values)
}

export async function refreshAccessToken(): Promise<AuthResponse | null> {
  const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })

  if (!response.ok) {
    return null
  }

  return response.json() as Promise<AuthResponse>
}

export async function logoutRequest(): Promise<void> {
  await fetch(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  })
}