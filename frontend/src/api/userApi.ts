import {parseErrorMessage} from "./common.ts";
import type {User} from "./types.ts";
import type {AuthState} from "../auth/types.ts";
import {authorizedFetch} from "./httpClient.ts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export async function getUser(authState: AuthState, onUnauthorized: () => void) {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/user`,
        {method: 'GET'},
        onUnauthorized
    )

    if (response.status === 401) {
        return undefined
    }

    if (response.status >= 400 && response.status < 500) {
        console.error(response.statusText)
        return undefined
    }

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Failed to load user'))
    }

    return await response.json() as Promise<User>
}