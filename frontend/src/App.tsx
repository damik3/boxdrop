import {useEffect, useState} from 'react'
import './App.css'
import {AuthenticatedApp} from './AuthenticatedApp'
import {AuthForm} from './auth/AuthForm'
import {useAuth} from './auth/useAuth'
import type {AuthMode} from './auth/types'
import {getUser} from "./api/userApi.ts";
import type {User} from "./api/types.ts";

function App() {
    const {authState, authenticate, logout} = useAuth()
    const [mode, setMode] = useState<AuthMode>('login')
    const [busy, setBusy] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [user, setUser] = useState<User | null>(null)

    useEffect(() => {
        if (!authState) {
            setUser(null)
            return
        }
        getUser(authState)
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
    }, [authState])

    if (authState && user) {
        return <AuthenticatedApp authState={authState} onLogout={logout}/>
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
