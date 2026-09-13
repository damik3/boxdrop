import {useMemo, useState} from 'react'
import {clearAuthState, readAuthState, writeAuthState} from './authStorage'
import {login, logoutRequest, register} from './authApi'
import type {AuthFormValues, AuthMode, AuthState} from './types'

interface UseAuthResult {
    authState: AuthState | null
    authenticate: (mode: AuthMode, values: AuthFormValues) => Promise<void>
    logout: () => Promise<void>
}

export function useAuth(): UseAuthResult {
    const [authState, setAuthState] = useState<AuthState | null>(() => readAuthState())

    const value = useMemo<UseAuthResult>(() => ({
        authState,
        async authenticate(mode, values) {
            const response = mode === 'login' ? await login(values) : await register(values)
            writeAuthState(response)
            setAuthState(response)
        },
        async logout() {
            try {
                await logoutRequest()
            } finally {
                clearAuthState()
                setAuthState(null)
            }
        },
    }), [authState])

    return value
}
