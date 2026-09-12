export interface FileMetadata {
    id: string
    name: string
    size: number
    mimeType: string
    uploadedBy: string
    url: string
}

export interface UploadFileResponse {
    fileId: string
    presignedUrl: string
}

export interface GetDownloadUrlResponse {
    url: string
}

export interface User {
    id: string
    email: string
}