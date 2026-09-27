import type {AuthState} from "../auth/types.ts";
import * as React from "react";
import {
    completeMultipartUpload,
    fileExists, getParts,
    getPresignedUrlForMultipartUpload,
    initiateMultipartUpload, patchMultipartUpload, requestUploadUrl, uploadFile,
    uploadPart
} from "../api/fileApi.ts";
import {mapConcurrent, toHex} from "../utils.ts";
import {GENERIC_ERROR_MESSAGE} from "../api/common.ts";
import type {Part} from "../api/fileTypes.ts";

interface IUploadModalProps {
    authState: AuthState
    onLogout: () => void
    onClose: () => void
    onUploaded: (fileId?: string) => void,
    resumeFile?: File
    expectedFileId?: string
}

interface FileInput {
    file: File | null
}

export function UploadModal({authState, onLogout, onClose, onUploaded, resumeFile, expectedFileId}: IUploadModalProps) {

    const multipart_chunksize = 5 * 1024 * 1024 // 5MB

    const [fileInputState, setFileInputState] = React.useState<FileInput>({file: resumeFile ?? null})
    const [uploadErrorMessage, setUploadErrorMessage] = React.useState<string | null>(null)
    const [uploadingFile, setUploadingFile] = React.useState(false)
    const [uploadProgress, setUploadProgress] = React.useState<Array<number> | null>(null)
    const fileRef = React.useRef<HTMLInputElement | null>(null)

    const calculateUploadProgress = (numChunks: number, i: number, percent: number) => {
        setUploadProgress(prev => {
            const next = prev ? [...prev] : Array(numChunks).fill(0)
            next[i] = percent
            return next
        })
    }

    const multipartUpload = async (file: File, resumeFileId?: string, isCancelled?: () => boolean): Promise<void> => {
        // TODO: fingerprint can be improved with incremental hashing in order to avoid loading the whole file in memory
        const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
        if (isCancelled?.()) {
            return
        }
        const {exists, fileId, status} = await fileExists(authState, file.name, toHex(fingerprint), onLogout)
        if (isCancelled?.()) {
            return
        }
        const concurrencyLimit = 10
        const numChunks = Math.ceil(file.size / multipart_chunksize)

        if (resumeFileId && (!exists || fileId !== resumeFileId || status !== 'PENDING')) {
            throw new Error('Choose the same file to resume this upload.')
        }

        if (!exists) { // Upload from scratch flow
            setUploadProgress(Array(numChunks).fill(0))
            const {fileId} = await initiateMultipartUpload(authState, file.name, toHex(fingerprint), file.size, file.type, numChunks, onLogout)
            await mapConcurrent(Array.from({length: numChunks}, (_, i) => i), concurrencyLimit, async (i) => {
                const partNumber = i + 1
                const slice = file.slice(i * multipart_chunksize, (i + 1) * multipart_chunksize)
                const {url} = await getPresignedUrlForMultipartUpload(authState, fileId, partNumber, onLogout)
                const {etag} = await uploadPart(url, slice, (percent) => calculateUploadProgress(numChunks, i, percent))
                const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await slice.arrayBuffer())
                await patchMultipartUpload(authState, fileId, partNumber, toHex(fingerprint), etag, onLogout)
            })

            await completeMultipartUpload(authState, fileId, onLogout)

            onUploaded()
            onClose()

        } else if (status === 'COMPLETED') {
            throw new Error('File already exists')

        } else if (status === 'PENDING') { // Resume upload flow
            const parts: Part[] = await getParts(authState, fileId, onLogout)
            const initial = Array(numChunks).fill(0)
            for (const part of parts) {
                if (part.status === 'UPLOADED') {
                    initial[part.partNumber - 1] = 100
                }
            }
            setUploadProgress(initial)
            await mapConcurrent(parts, concurrencyLimit, async (part: Part) => {
                const partNumber = part.partNumber
                if (part.status === 'UPLOADED') {
                    return
                } else {
                    const slice = file.slice((partNumber - 1) * multipart_chunksize, partNumber * multipart_chunksize)
                    const {url} = await getPresignedUrlForMultipartUpload(authState, fileId, partNumber, onLogout)
                    const {etag} = await uploadPart(url, slice, (percent) => calculateUploadProgress(numChunks, partNumber - 1, percent))
                    const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await slice.arrayBuffer())
                    await patchMultipartUpload(authState, fileId, partNumber, toHex(fingerprint), etag, onLogout)
                }
            })

            await completeMultipartUpload(authState, fileId, onLogout)
            onUploaded()
            onClose()

        } else if (status === 'FAILED') {
            throw new Error('Upload failed')
        }
    }

    const multipartUploadRef = React.useRef(multipartUpload)
    multipartUploadRef.current = multipartUpload
    const resumeAttemptRef = React.useRef(0)

    const startResume = (file: File) => {
        if (!expectedFileId) {
            return
        }
        const attempt = ++resumeAttemptRef.current
        setFileInputState({file})
        setUploadingFile(true)
        setUploadProgress(null)
        setUploadErrorMessage(null)
        void (async () => {
            try {
                await multipartUploadRef.current(file, expectedFileId, () => resumeAttemptRef.current !== attempt)
            } catch (error) {
                if (resumeAttemptRef.current !== attempt) {
                    return
                }
                setUploadErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
                setUploadingFile(false)
                setUploadProgress(null)
            }
        })()
    }

    const startResumeRef = React.useRef(startResume)
    startResumeRef.current = startResume

    React.useEffect(() => {
        if (!resumeFile || !expectedFileId) {
            return
        }
        startResumeRef.current(resumeFile)
        return () => {
            resumeAttemptRef.current += 1
        }
    }, [resumeFile, expectedFileId])

    const regularUpload = async (file: File): Promise<void> => {
        const {fileId, presignedUrl} = await requestUploadUrl(authState, file, onLogout)
        await uploadFile(presignedUrl, file, (percent: number) => setUploadProgress([percent]))
        onUploaded(fileId)
        onClose()
    }

    const handleSubmitUpload = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()

        if (!fileInputState.file) {
            return
        }

        setUploadingFile(true)
        setUploadProgress(null)
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
        event.target.value = ''
        if (!selectedFile) {
            return
        }
        if (expectedFileId) {
            startResume(selectedFile)
            return
        }
        setUploadErrorMessage(null)
        setFileInputState({file: selectedFile})
    }


    return (
        <div className="modal-backdrop">
            <div className="modal" role="dialog" aria-modal="true" aria-labelledby="upload-file-title">
                <div className="modal__header">
                    <h3 id="upload-file-title">{expectedFileId ? 'Resume upload' : 'Upload file'}</h3>
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
                                disabled={uploadingFile}
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
                        />
                    </label>

                    {fileInputState.file ? (
                        <div className="file-status">
                            <span className="file-status__label">Selected</span>
                            <strong>{fileInputState.file.name}</strong>
                        </div>
                    ) : null}

                    {uploadingFile && uploadProgress && uploadProgress.length > 0 ? (
                        <div className="upload-progress" role="progressbar" aria-valuemin={0}
                             aria-valuemax={100}
                             aria-valuenow={Math.round(uploadProgress.reduce((a, b) => a + b, 0) / uploadProgress.length)}>
                            <div className="upload-progress__track">
                                <div className="upload-progress__bar"
                                     style={{
                                         width: `${Math.round(uploadProgress.reduce((a, b) => a + b, 0) / uploadProgress.length)
                                         }%`
                                     }}/>
                            </div>
                            <span
                                className="upload-progress__label">{Math.round(uploadProgress.reduce((a, b) => a + b, 0) / uploadProgress.length)
                            }%</span>
                        </div>
                    ) : null}

                    {uploadErrorMessage ? <p className="auth-form__error">{uploadErrorMessage}</p> : null}

                    <div className="modal__actions">
                        <button type="button" className="secondary-button" onClick={onClose}
                                disabled={uploadingFile}>
                            Cancel
                        </button>
                        {expectedFileId ? null : (
                            <button type="submit" className="primary-button"
                                    disabled={!fileInputState.file || uploadingFile}>
                                {uploadingFile ? 'Uploading...' : 'Upload'}
                            </button>
                        )}
                    </div>
                </form>
            </div>
        </div>
    )
}
