import type {AuthState} from '../auth/types.ts'
import type {FileMetadata, GetDownloadUrlResponse, UploadFileResponse} from './types.ts'
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
        throw new Error(await parseErrorMessage(response, 'Failed to load files'))
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
        throw new Error(await parseErrorMessage(response, 'File upload failed'))
    }

    return await response.json() as Promise<UploadFileResponse>
}

export async function uploadFile(url: string, file: File ): Promise<void> {
    const response = await fetch(
        url,
        {
            method: 'PUT',
            body: file
        }
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'uploadFile failed'))
    }
}

export async function markUploadCompleted(authState: AuthState, fileId: string, onUnauthorized: () => void): Promise<void> {
    const response = await authorizedFetch(
        authState,
        `${API_BASE_URL}/files/upload/mark-upload-completed`,
        {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({fileId: fileId}),
        },
        onUnauthorized
    )

    if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'markUploadCompleted failed'))
    }
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
        throw new Error(await parseErrorMessage(response, 'getDownloadUrl failed'))
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
        throw new Error(await parseErrorMessage(response, 'deleteFile failed'))
    }
}