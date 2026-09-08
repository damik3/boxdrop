import type { AuthState } from '../auth/types.ts'
import type {FileMetadata, GetDownloadUrlResponse, UploadFileResponse} from './types.ts'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

async function parseErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  const rawBody = await response.text()
  try {
    const parsed = JSON.parse(rawBody) as { error?: string; message?: string }
    return parsed.error ?? parsed.message ?? rawBody ?? fallbackMessage
  } catch {
    return rawBody || fallbackMessage
  }
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

export async function requestUploadUrl(authState: AuthState, file: File): Promise<UploadFileResponse> {
  const fileMetadata = {
    name: file.name,
    size: file.size,
    mimeType: file.type,
  }
  const response = await fetch(`${API_BASE_URL}/files/upload/presigned-url-for-upload`, {
    method: 'POST',
    headers: {
      Authorization: `${authState.tokenType} ${authState.accessToken}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(fileMetadata),
  })

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'File upload failed'))
  }

  return response.json() as Promise<UploadFileResponse>
}

export async function uploadFile(url: string, file: File): Promise<void> {
  const response = await fetch(url,
      {
        method: 'PUT',
        body: file
      }
  )

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'uploadFile failed'))
  }
}

export async function markUploadCompleted(authState: AuthState, fileId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/files/upload/mark-upload-completed`,
      {
        method: 'POST',
        headers: {
          Authorization: `${authState.tokenType} ${authState.accessToken}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({fileId: fileId}),
      }
  )

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'markUploadCompleted failed'))
  }
}

export async function getDownloadUrl(authState: AuthState, fileId: string): Promise<GetDownloadUrlResponse> {
  const response = await fetch(`${API_BASE_URL}/files/download/${fileId}`,
      {
        method: 'GET',
        headers: {
          Authorization: `${authState.tokenType} ${authState.accessToken}`,
          Accept: 'application/json',
        },
      }
  )

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'getDownloadUrl failed'))
  }

  return response.json() as Promise<GetDownloadUrlResponse>
}

export async function deleteFile(authState: AuthState, fileId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/files/${fileId}`,
      {
        method: 'DELETE',
        headers: {
          Authorization: `${authState.tokenType} ${authState.accessToken}`,
          Accept: 'application/json',
        },
      }
  )

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response, 'getDownloadUrl failed'))
  }
}