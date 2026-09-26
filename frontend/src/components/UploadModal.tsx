import type {AuthState} from "../auth/types.ts";
import * as React from "react";
import {
    completeMultipartUpload,
    fileExists,
    getPresignedUrlForMultipartUpload,
    initiateMultipartUpload, patchMultipartUpload, requestUploadUrl, uploadFile,
    uploadPart
} from "../api/fileApi.ts";
import {mapConcurrent, toHex} from "../utils.ts";
import {GENERIC_ERROR_MESSAGE} from "../api/common.ts";

interface IUploadModalProps {
    authState: AuthState
    onLogout: () => void
    onClose: () => void
    onUploaded: (fileId?: string) => void,
}

interface FileInput {
    file: File | null
}

export function UploadModal({authState, onLogout, onClose, onUploaded}: IUploadModalProps) {

    const multipart_chunksize = 5 * 1024 * 1024 // 5MB

    const [fileInputState, setFileInputState] = React.useState<FileInput>({file: null})
    const [uploadErrorMessage, setUploadErrorMessage] = React.useState<string | null>(null)
    const [uploadingFile, setUploadingFile] = React.useState(false)
    const [uploadProgress, setUploadProgress] = React.useState<number | null>(null)
    const fileRef = React.useRef<HTMLInputElement | null>(null)


    const multipartUpload = async (file: File): Promise<void> => {
        // TODO: fingerprint can be improved with incremental hashing in order to avoid loading the whole file in memory
        const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
        const {exists, status} = await fileExists(authState, file.name, toHex(fingerprint), onLogout)
        if (!exists) {
            // Upload from scratch flow
            const numChunks = Math.ceil(file.size / multipart_chunksize)
            const {fileId} = await initiateMultipartUpload(authState, file.name, toHex(fingerprint), file.size, file.type, numChunks, onLogout)
            const concurrencyLimit = 10
            await mapConcurrent(Array.from({length: numChunks}, (_, i) => i), concurrencyLimit, async (i) => {
                const partNumber = i + 1
                const slice = file.slice(i * multipart_chunksize, (i + 1) * multipart_chunksize)
                const {url} = await getPresignedUrlForMultipartUpload(authState, fileId, partNumber, onLogout)
                const {etag} = await uploadPart(url, slice)
                const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await slice.arrayBuffer())
                await patchMultipartUpload(authState, fileId, partNumber, toHex(fingerprint), etag, onLogout)
            })

            await completeMultipartUpload(authState, fileId, onLogout)

            onUploaded()
            onClose()
        } else if (status === 'COMPLETED') {
            throw new Error('File already exists')
        } else if (status === 'PENDING') {
            throw new Error('Resume not implemented')
        } else if (status === 'FAILED') {
            throw new Error('Upload failed')
        }
    }

    const regularUpload = async (file: File): Promise<void> => {
        const {fileId, presignedUrl} = await requestUploadUrl(authState, file, onLogout)
        await uploadFile(presignedUrl, file, setUploadProgress)
        onUploaded(fileId)
        onClose()
    }

    const handleSubmitUpload = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()

        if (!fileInputState.file) {
            return
        }

        setUploadingFile(true)
        setUploadProgress(0)
        setUploadErrorMessage(null)

        const file = fileInputState.file
        try {
            if (file.size > multipart_chunksize) {
                await multipartUpload(file)
            } else {
                await regularUpload(file)
            }
        } catch (error) {
            setUploadErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
            setUploadingFile(false)
            setUploadProgress(null)
        }
    }

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
        const selectedFile = event.target.files?.[0] ?? null
        setUploadErrorMessage(null)
        setFileInputState({file: selectedFile})
    }


    return (
        <div className="modal-backdrop">
            <div className="modal" role="dialog" aria-modal="true" aria-labelledby="upload-file-title">
                <div className="modal__header">
                    <h3 id="upload-file-title">Upload file</h3>
                    <span className="hero__eyebrow">Transfer</span>
                </div>

                <form className="upload-file-form" onSubmit={handleSubmitUpload}>
                    <label className="field">
                        <span>Select a file</span>

                        <div className="input-group">
                            <input
                                type="text"
                                value={fileInputState.file ? fileInputState.file.name : 'No file selected'}
                                readOnly
                            />
                            <button
                                type="button"
                                className="primary-button"
                                onClick={() => fileRef.current?.click()}
                            >
                                Choose File
                            </button>
                        </div>

                        <input
                            type="file"
                            name="file"
                            style={{display: 'none'}}
                            ref={fileRef}
                            onChange={handleFileChange}
                            required
                        />
                    </label>

                    {fileInputState.file ? (
                        <div className="file-status">
                            <span className="file-status__label">Selected</span>
                            <strong>{fileInputState.file.name}</strong>
                        </div>
                    ) : null}

                    {uploadingFile && uploadProgress !== null ? (
                        <div className="upload-progress" role="progressbar" aria-valuemin={0}
                             aria-valuemax={100} aria-valuenow={uploadProgress}>
                            <div className="upload-progress__track">
                                <div className="upload-progress__bar" style={{width: `${uploadProgress}%`}}/>
                            </div>
                            <span className="upload-progress__label">{uploadProgress}%</span>
                        </div>
                    ) : null}

                    {uploadErrorMessage ? <p className="auth-form__error">{uploadErrorMessage}</p> : null}

                    <div className="modal__actions">
                        <button type="button" className="secondary-button" onClick={onClose}
                                disabled={uploadingFile}>
                            Cancel
                        </button>
                        <button type="submit" className="primary-button"
                                disabled={!fileInputState.file || uploadingFile}>
                            {uploadingFile ? 'Uploading...' : 'Upload'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}
