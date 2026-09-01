export type AuthMode = 'login' | 'register'

export interface AuthFormValues {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: string
  email: string
}

export interface AuthState {
  accessToken: string
  tokenType: string
  expiresIn: number
  userId: string
  email: string
}
