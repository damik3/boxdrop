import {parseErrorMessage} from "./common.ts";
import type {User} from "./types.ts";
import type {AuthState} from "../auth/types.ts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/user'

export async function getUser(authState: AuthState) {
    const response = await fetch(`${API_BASE_URL}`, {
        method: 'GET',
        headers: {
            Authorization: `${authState.tokenType} ${authState.accessToken}`,
        },
    })

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

    return response.json() as Promise<User>
}