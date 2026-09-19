import type { AuthState } from '../auth/types'
import { refreshAccessToken } from '../auth/authApi'
import { clearAuthState, writeAuthState } from '../auth/authStorage'
import { GENERIC_ERROR_MESSAGE } from './common'

type Unauthorized = () => void

let refreshInFlight: Promise<AuthState | null> | null = null

async function refreshOnce(): Promise<AuthState | null> {
    if (!refreshInFlight) {
        refreshInFlight = refreshAccessToken()
            .then((response) => {
                if (!response) return null
                writeAuthState(response)
                return response
            })
            .finally(() => {
                refreshInFlight = null
            })
    }
    return refreshInFlight
}

export async function authorizedFetch(
    authState: AuthState,
    input: RequestInfo,
    init: RequestInit,
    onUnauthorized: Unauthorized,
): Promise<Response> {
    const withAuthHeader = (state: AuthState): RequestInit => ({
        ...init,
        headers: {
            ...(init.headers ?? {}),
            Authorization: `${state.tokenType} ${state.accessToken}`,
        },
    })

    try {
        let response = await fetch(input, withAuthHeader(authState))

        if (response.status === 401) {
            const refreshed = await refreshOnce()
            if (!refreshed) {
                clearAuthState()
                onUnauthorized()
                return response
            }
            response = await fetch(input, withAuthHeader(refreshed))
        }

        return response
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error(GENERIC_ERROR_MESSAGE)
        }
        throw error
    }
}
