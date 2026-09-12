export async function parseErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
    const rawBody = await response.text()
    try {
        const parsed = JSON.parse(rawBody) as { error?: string; message?: string }
        return parsed.error ?? parsed.message ?? rawBody ?? fallbackMessage
    } catch {
        return rawBody || fallbackMessage
    }
}