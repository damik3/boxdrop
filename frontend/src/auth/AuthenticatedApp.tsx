import type { AuthState } from './types'

interface AuthenticatedAppProps {
  authState: AuthState
  onLogout: () => void
}

const nextSteps = [
  'Create file and folder browsing views',
  'Attach the JWT bearer token to protected API calls',
  'Add upload, download, and sharing flows',
]

export function AuthenticatedApp({ authState, onLogout }: AuthenticatedAppProps) {
  return (
    <main className="app-shell">
      <section className="hero hero--compact">
        <div>
          <span className="hero__eyebrow">Authenticated session</span>
          <h1>Welcome, {authState.email}</h1>
          <p className="hero__copy">
            The frontend now has a local auth state, persisted JWT token, and a protected shell
            ready for file APIs.
          </p>
        </div>
        <button type="button" className="secondary-button" onClick={onLogout}>
          Log out
        </button>
      </section>

      <section className="panel-grid">
        <article className="panel">
          <h2>Session</h2>
          <dl className="stack-list">
            <div>
              <dt>User ID</dt>
              <dd>{authState.userId}</dd>
            </div>
            <div>
              <dt>Token type</dt>
              <dd>{authState.tokenType}</dd>
            </div>
            <div>
              <dt>Expires in</dt>
              <dd>{authState.expiresIn}s</dd>
            </div>
          </dl>
        </article>

        <article className="panel panel--wide">
          <h2>Next frontend steps</h2>
          <ol>
            {nextSteps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        </article>
      </section>
    </main>
  )
}
