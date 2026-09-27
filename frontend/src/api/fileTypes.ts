export interface FileMetadata {
    id: string
    name: string
    size: number
    mimeType: string
    uploadedBy: string
    status: 'PENDING' | 'COMPLETED' | 'FAILED'
}

export interface Part {
    partNumber: number
    status: 'UPLOADED' | 'UPLOADING' | 'NOT_UPLOADED'
    fingerprint: string
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

export interface FileExistsResponse {
    exists: boolean
    fileId: string
    status: string
}

export interface InitiateMultipartUploadResponse {
    fileId: string
}

export interface GetPresignedUrlForMultipartUploadResponse {
    url: string
}

export interface UploadPartResponse {
    etag: string
}