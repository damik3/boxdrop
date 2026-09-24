import type {AuthState} from '../auth/types.ts'
import type {
    FileExistsResponse, FileMetadata, FileShare, GetDownloadUrlResponse, GetPresignedUrlForMultipartUploadResponse,
    InitiateMultipartUploadResponse, UploadFileResponse, UploadPartResponse
} from './types.ts'
import {parseErrorMessage} from "./common.ts";
import {authorizedFetch} from "./httpClient.ts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export async function getFiles(authState: AuthState, onUnauthorized: () => void): Promise<FileMetadata[]> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files`,
        {method: 'GET'},
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
        `${API_BASE_URL}/files/upload/presigned-url`,
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

export function uploadPart(
    url: string,
    file: Blob,
): Promise<UploadPartResponse> {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', url)

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                const etag = xhr.getResponseHeader('ETag') ?? ''
                resolve({ etag })
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

export async function fileExists(
    authState: AuthState,
    filename: string,
    fingerprint: string,
    onUnauthorized: () => void,
): Promise<FileExistsResponse> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/exists`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({filename, fingerprint}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not check if file exists.'))
    }

    return await response.json() as Promise<FileExistsResponse>
}

export async function initiateMultipartUpload(
    authState: AuthState,
    filename: string,
    fingerprint: string,
    size: number,
    mimeType: string,
    numChunks: number,
    onUnauthorized: () => void,
): Promise<InitiateMultipartUploadResponse> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/multipart-upload`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({filename, fingerprint, size, mimeType, numChunks}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not initiate multipart upload.'))
    }

    return await response.json() as Promise<InitiateMultipartUploadResponse>
}

export async function getPresignedUrlForMultipartUpload(
    authState: AuthState,
    fileId: string,
    uploadId: string,
    partNumber: number,
    onUnauthorized: () => void,
): Promise<GetPresignedUrlForMultipartUploadResponse> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/multipart-upload/presigned-url`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({fileId, uploadId, partNumber}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not get presigned URL for multipart upload.'))
    }

    return await response.json() as Promise<GetPresignedUrlForMultipartUploadResponse>
}

export async function patchMultipartUpload(
    authState: AuthState,
    fileId: string,
    uploadId: string,
    partNumber: number,
    fingerprint: string,
    etag: string,
    onUnauthorized: () => void,
): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/multipart-upload`,
        {
            method: 'PATCH',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({fileId, uploadId, partNumber, fingerprint, etag}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not patch multipart upload.'))
    }
}

export async function completeMultipartUpload(
    authState: AuthState,
    fileId: string,
    onUnauthorized: () => void,
): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/multipart-upload/complete`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({fileId}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Could not complete multipart upload.'))
    }
}