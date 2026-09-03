import type {FileMetadata} from "./types.ts";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

async function post(): Promise<FileMetadata[]> {
    const response = await fetch(`${API_BASE_URL}/file/all`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
    })

    if (!response.ok) {
        const errorMessage = await response.text()
        throw new Error(errorMessage || 'Authentication request failed')
    }

    return await response.json() as Promise<FileMetadata[]>
}

export async function getFiles(): Promise<FileMetadata[]> {
    // return post()
     const files = [
        {
            id: 1,
            name: "file1.txt",
            size: 123,
            mimeType: "text/plain",
            uploadedBy: "mimomiko",
            url: "http://localhost:5173/"
        },
        {
            id: 2,
            name: "file2.txt",
            size: 456,
            mimeType: "text/plain",
            uploadedBy: "mike",
            url: "https://www.youtube.com/"
        },
    ]
     return Promise.resolve(files)
}