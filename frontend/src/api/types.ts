export interface FileMetadata {
    id: string
    name: string
    size: number
    mimeType: string
    uploadedBy: string
    status: 'PENDING' | 'COMPLETED' | 'FAILED'
}

export interface UploadFileResponse {
    fileId: string
    presignedUrl: string
}

export interface GetDownloadUrlResponse {
    url: string
}

export interface FileShare {
    userId: string
    email: string
}

export interface User {
    id: string
    email: string
}