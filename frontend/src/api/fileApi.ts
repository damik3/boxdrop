import type {AuthState} from '../auth/types.ts'
import type {FileMetadata, FileShare, GetDownloadUrlResponse, UploadFileResponse} from './types.ts'
import {parseErrorMessage} from "./common.ts";
import {authorizedFetch} from "./httpClient.ts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export async function getFiles(authState: AuthState, onUnauthorized: () => void): Promise<FileMetadata[]> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files`,
        { method: 'GET' },
        onUnauthorized
    )

    if (response.status === 404) {
        return []
    }

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not load your files.'))
    }

    return await response.json() as Promise<FileMetadata[]>
}

export async function requestUploadUrl(authState: AuthState, file: File, onUnauthorized: () => void): Promise<UploadFileResponse> {
    const fileMetadata = {
        name: file.name,
        size: file.size,
        mimeType: file.type,
    }
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/upload/presigned-url-for-upload`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(fileMetadata),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not start the upload.'))
    }

    return await response.json() as Promise<UploadFileResponse>
}

export function uploadFile(
    url: string,
    file: File,
    onProgress?: (percent: number) => void,
): Promise<void> {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', url)
        if (file.type) {
            xhr.setRequestHeader('Content-Type', file.type)
        }

        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable && onProgress) {
                onProgress(Math.round((event.loaded / event.total) * 100))
            }
        }

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve()
                return
            }
            reject(new Error('Could not upload this file. Try again.'))
        }

        xhr.onerror = () => {
            reject(new Error('Could not upload this file. Try again.'))
        }

        xhr.send(file)
    })
}

export async function getDownloadUrl(authState: AuthState, fileId: string, onUnauthorized: () => void): Promise<GetDownloadUrlResponse> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/download/${fileId}`,
        {
            method: 'GET',
            headers: {
                Accept: 'application/json',
            },
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not download this file.'))
    }

    return await response.json() as Promise<GetDownloadUrlResponse>
}

export async function deleteFile(authState: AuthState, fileId: string, onUnauthorized: () => void): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/${fileId}`,
        {
            method: 'DELETE',
            headers: {
                Accept: 'application/json',
            },
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not delete this file.'))
    }
}

export async function getSharedFiles(authState: AuthState, onUnauthorized: () => void): Promise<FileMetadata[]> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/shared`,
        {
            method: 'GET',
            headers: {
                Accept: 'application/json',
            },
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not load shared files.'))
    }

    return await response.json() as Promise<FileMetadata[]>
}

export async function getFileShares(
    authState: AuthState,
    fileId: string,
    onUnauthorized: () => void,
): Promise<FileShare[]> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/${fileId}/shares`,
        {
            method: 'GET',
            headers: {
                Accept: 'application/json',
            },
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not load who this file is shared with.'))
    }

    return await response.json() as Promise<FileShare[]>
}

export async function shareFile(
    authState: AuthState,
    fileId: string,
    email: string,
    onUnauthorized: () => void,
): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/${fileId}/share`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({email}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not share this file.'))
    }
}

export async function unshareFile(
    authState: AuthState,
    fileId: string,
    email: string,
    onUnauthorized: () => void,
): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/${fileId}/share`,
        {
            method: 'DELETE',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({email}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not remove access.'))
    }
}