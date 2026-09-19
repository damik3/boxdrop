import {useEffect, useState} from 'react'
import './App.css'
import {AuthenticatedApp} from './AuthenticatedApp'
import {AuthForm} from './auth/AuthForm'
import {useAuth} from './auth/useAuth'
import type {AuthMode} from './auth/types'
import {getUser} from "./api/userApi.ts";
import {GENERIC_ERROR_MESSAGE} from "./api/common.ts";
import type {User} from "./api/types.ts";

function App() {
    const {authState, authenticate, logout} = useAuth()
    const [mode, setMode] = useState<AuthMode>('login')
    const [busy, setBusy] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [user, setUser] = useState<User | null>(null)
    const [authLoading, setAuthLoading] = useState<boolean>(() => Boolean(authState))

    useEffect(() => {
        if (!authState) {
            setAuthLoading(false)
            setUser(null)
            return
        }
        setAuthLoading(true)
        getUser(authState, logout)
            .then(user => {
                if (user) {
                    setUser(user)
                } else {
                    setUser(null)
                    logout()
                }
            })
            .catch((ex) => {
                setUser(null)
                logout()
                console.error(ex)
            })
            .finally(() => {
                setAuthLoading(false)
            })
    }, [authState])

    if (authState && user) {
        return <AuthenticatedApp authState={authState} onLogout={logout}/>
    }

    if (authLoading) {
        return (
            <main className="app-shell loading-layout">
                <div className="loading-card" role="status" aria-live="polite">
                    <div className="loading-spinner" aria-hidden="true"/>
                    <p>Loading your account...</p>
                </div>
            </main>
        )
    }

    async function handleSubmit(currentMode: AuthMode, values: { email: string; password: string }) {
        setBusy(true)
        setErrorMessage(null)

        try {
            await authenticate(currentMode, values)
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        } finally {
            setBusy(false)
        }
    }

    return (
        <main className="app-shell auth-layout">
            <section className="hero">
                <h1>Boxdrop</h1>
                <p className="hero__copy">
                    Your personal cloud storage for files and documents.
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
