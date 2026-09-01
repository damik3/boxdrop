import { useState } from 'react'
import './App.css'
import { AuthenticatedApp } from './auth/AuthenticatedApp'
import { AuthForm } from './auth/AuthForm'
import { useAuth } from './auth/useAuth'
import type { AuthMode } from './auth/types'

function App() {
  const { authState, authenticate, logout } = useAuth()
  const [mode, setMode] = useState<AuthMode>('login')
  const [busy, setBusy] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (authState) {
    return <AuthenticatedApp authState={authState} onLogout={logout} />
  }

  async function handleSubmit(currentMode: AuthMode, values: { email: string; password: string }) {
    setBusy(true)
    setErrorMessage(null)

    try {
      await authenticate(currentMode, values)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Authentication failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="app-shell auth-layout">
      <section className="hero">
        <span className="hero__eyebrow">Dropbox clone learning project</span>
        <h1>Auth-first frontend scaffold for file ownership and sharing.</h1>
        <p className="hero__copy">
          This screen is a minimal shell for registration and login against the Spring Boot JWT
          backend. Once authenticated, the app switches into a protected workspace shell that will
          host the file browser, uploads, downloads, and sharing flows.
        </p>
      </section>

      <AuthForm
        mode={mode}
        busy={busy}
        errorMessage={errorMessage}
        onSubmit={(values) => handleSubmit(mode, values)}
        onModeChange={setMode}
      />
    </main>
  )
}

export default App
