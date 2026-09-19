export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Try again.'

export async function parseErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
    if (response.status >= 500) {
        return GENERIC_ERROR_MESSAGE
    }

    const rawBody = await response.text()
    try {
        const parsed = JSON.parse(rawBody) as { code?: string; message?: string }
        if (parsed.message) {
            return parsed.message
        }
    } catch {
        // Body is not JSON — use the fallback for expected client errors.
    }

    if (response.status >= 400 && response.status < 500) {
        return fallbackMessage
    }

    return GENERIC_ERROR_MESSAGE
}
