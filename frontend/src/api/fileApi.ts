import type { AuthState } from '../auth/types.ts'
import type { FileMetadata } from './types.ts'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

async function parseErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  const errorMessage = await response.text()
  return errorMessage || fallbackMessage
}

export async function getFiles(authState: AuthState): Promise<FileMetadata[]> {
  const response = await fetch(`${API_BASE_URL}/files`, {
    method: 'GET',
    headers: {
      Authorization: `${authState.tokenType} ${authState.accessToken}`,
    },
  })

  if (response.status === 404) {
    return []
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'Failed to load files'))
  }

  return response.json() as Promise<FileMetadata[]>
}

export async function uploadFile(authState: AuthState, file: File): Promise<void> {
  const fileMetadata = {
    name: file.name,
    size: file.size,
    mimeType: file.type,
  }
  const response = await fetch(`${API_BASE_URL}/files`, {
    method: 'POST',
    headers: {
      Authorization: `${authState.tokenType} ${authState.accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(fileMetadata),
  })

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'File upload failed'))
  }
}
