export interface FileMetadata {
    id: number
    name: string
    size: number
    mimeType: string
    uploadedBy: string
    url: string
}

export interface UploadFileResponse {
    presignedUrl: string
}
