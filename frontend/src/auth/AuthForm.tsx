import { useState } from 'react'
import type { FormEvent } from 'react'
import type { AuthFormValues, AuthMode } from './types'

interface AuthFormProps {
  mode: AuthMode
  busy: boolean
  errorMessage: string | null
  onSubmit: (values: AuthFormValues) => Promise<void>
  onModeChange: (mode: AuthMode) => void
}

export function AuthForm({ mode, busy, errorMessage, onSubmit, onModeChange }: AuthFormProps) {
  const [values, setValues] = useState<AuthFormValues>({
    email: '',
    password: '',
  })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await onSubmit(values)
  }

  return (
    <section className="auth-card">
      <div className="auth-card__header">
        <span className="hero__eyebrow">Auth scaffold</span>
        <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
        <p>
          Use the backend JWT endpoints to simulate multiple users and sharing flows.
        </p>
      </div>

      <div className="auth-toggle" role="tablist" aria-label="Authentication mode">
        <button
          type="button"
          className={mode === 'login' ? 'auth-toggle__button auth-toggle__button--active' : 'auth-toggle__button'}
          onClick={() => onModeChange('login')}
        >
          Login
        </button>
        <button
          type="button"
          className={mode === 'register' ? 'auth-toggle__button auth-toggle__button--active' : 'auth-toggle__button'}
          onClick={() => onModeChange('register')}
        >
          Register
        </button>
      </div>

      <form className="auth-form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Email</span>
          <input
            type="email"
            name="email"
            autoComplete="email"
            value={values.email}
            onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))}
            placeholder="alex@example.com"
            required
          />
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            name="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={values.password}
            onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
            placeholder="At least 8 characters"
            minLength={8}
            required
          />
        </label>

        {errorMessage ? <p className="auth-form__error">{errorMessage}</p> : null}

        <button type="submit" className="primary-button" disabled={busy}>
          {busy ? 'Submitting...' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
      </form>
    </section>
  )
}
