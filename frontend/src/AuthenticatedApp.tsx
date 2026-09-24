import type {AuthState} from './auth/types.ts'
import {useEffect} from 'react'
import * as React from 'react'
import {
    deleteFile,
    getDownloadUrl,
    getFileShares,
    getFiles,
    getSharedFiles,
    requestUploadUrl,
    shareFile,
    unshareFile,
    uploadFile,
    fileExists, initiateMultipartUpload, getPresignedUrlForMultipartUpload, uploadPart, patchMultipartUpload,
    completeMultipartUpload
} from './api/fileApi.ts'
import {GENERIC_ERROR_MESSAGE} from './api/common.ts'
import type {FileMetadata, FileShare} from './api/types.ts'

interface AuthenticatedAppProps {
    authState: AuthState
    onLogout: () => void
}

interface FileInput {
    file: File | null
}

function formatFileSize(sizeInBytes: number): string {
    if (sizeInBytes < 1024) {
        return `${sizeInBytes} B`
    }

    const units = ['KB', 'MB', 'GB', 'TB']
    let value = sizeInBytes / 1024
    let unitIndex = 0

    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024
        unitIndex += 1
    }

    return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

const multipart_chunksize = 10 * 1024 * 1024; // 10MB

export function AuthenticatedApp({authState, onLogout}: AuthenticatedAppProps) {
    const [isUploadModalOpen, setIsUploadModalOpen] = React.useState(false)
    const [fileToShare, setFileToShare] = React.useState<FileMetadata | null>(null)
    const [shareEmail, setShareEmail] = React.useState('')
    const [shares, setShares] = React.useState<FileShare[]>([])
    const [shareErrorMessage, setShareErrorMessage] = React.useState<string | null>(null)
    const [loadingShares, setLoadingShares] = React.useState(false)
    const [sharingFile, setSharingFile] = React.useState(false)
    const [files, setFiles] = React.useState<FileMetadata[]>([])
    const [sharedFiles, setSharedFiles] = React.useState<FileMetadata[]>([])
    const [fileInputState, setFileInputState] = React.useState<FileInput>({file: null})
    const [loadErrorMessage, setLoadErrorMessage] = React.useState<string | null>(null)
    const [sharedLoadErrorMessage, setSharedLoadErrorMessage] = React.useState<string | null>(null)
    const [uploadErrorMessage, setUploadErrorMessage] = React.useState<string | null>(null)
    const [loadingFiles, setLoadingFiles] = React.useState(true)
    const [loadingSharedFiles, setLoadingSharedFiles] = React.useState(true)
    const [uploadingFile, setUploadingFile] = React.useState(false)
    const [uploadProgress, setUploadProgress] = React.useState<number | null>(null)
    const fileRef = React.useRef<HTMLInputElement | null>(null)

    const loadFiles = React.useCallback(async () => {
        setLoadingFiles(true)
        setLoadingSharedFiles(true)
        setLoadErrorMessage(null)
        setSharedLoadErrorMessage(null)

        const [ownedResult, sharedResult] = await Promise.allSettled([
            getFiles(authState, onLogout),
            getSharedFiles(authState, onLogout),
        ])

        if (ownedResult.status === 'fulfilled') {
            setFiles(ownedResult.value)
        } else {
            setLoadErrorMessage(
                ownedResult.reason instanceof Error ? ownedResult.reason.message : GENERIC_ERROR_MESSAGE,
            )
        }
        setLoadingFiles(false)

        if (sharedResult.status === 'fulfilled') {
            setSharedFiles(sharedResult.value)
        } else {
            setSharedLoadErrorMessage(
                sharedResult.reason instanceof Error ? sharedResult.reason.message : GENERIC_ERROR_MESSAGE,
            )
        }
        setLoadingSharedFiles(false)
    }, [authState, onLogout])

    useEffect(() => {
        void loadFiles()
    }, [loadFiles])

    const closeUploadModal = (): void => {
        setIsUploadModalOpen(false)
        setUploadingFile(false)
        setUploadProgress(null)
        setUploadErrorMessage(null)
        setFileInputState({file: null})
        if (fileRef.current) {
            fileRef.current.value = ''
        }
    }

    const pollUntilComplete = async (fileId: string, intervalMs: number = 2000) => {
        const maxTries = 15
        for (let i = 0; i < maxTries; i++) {
            await new Promise(resolve => setTimeout(resolve, intervalMs))
            const latest = await getFiles(authState, onLogout)
            setFiles(latest)
            const status = latest.find(file => file.id === fileId)?.status
            if (status === 'COMPLETED' || status === 'FAILED') {
                return
            }
        }
    }

    function toHex(buffer: ArrayBuffer): string {
        return [...new Uint8Array(buffer)]
            .map((byte) => byte.toString(16).padStart(2, '0'))
            .join('')
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
                // Handle multipart upload
                const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
                const {exists, status} = await fileExists(authState, file.name, toHex(fingerprint), onLogout)
                if (!exists) {
                    // Upload from scratch flow
                    const numChunks = Math.ceil(file.size / multipart_chunksize)
                    const initiateMultipartUploadResponse = await initiateMultipartUpload(authState, file.name, toHex(fingerprint), file.size, file.type, numChunks, onLogout)
                    const fileId = initiateMultipartUploadResponse.fileId
                    const uploadId = initiateMultipartUploadResponse.uploadId

                    const promises = Array.from({length: numChunks}, async (_, i) => {
                        const partNumber = i + 1
                        const slice = file.slice(i * multipart_chunksize, (i + 1) * multipart_chunksize)
                        const {url} = await getPresignedUrlForMultipartUpload(authState, fileId, uploadId, partNumber, onLogout)
                        const {etag} = await uploadPart(url, slice)
                        const fingerprint: ArrayBuffer = await crypto.subtle.digest('SHA-256', await slice.arrayBuffer())
                        await patchMultipartUpload(authState, fileId, uploadId, partNumber, toHex(fingerprint), etag, onLogout)
                    })

                    await Promise.all(promises)
                    await completeMultipartUpload(authState, fileId, onLogout)

                    closeUploadModal()
                    await loadFiles()
                    await pollUntilComplete(fileId)
                } else if (status === 'COMPLETED') {
                    throw new Error('File already exists')
                }
                else if (status === 'PENDING') {
                    throw new Error('Resume not implemented')
                }
                else if (status === 'FAILED') {
                    throw new Error('Upload failed')
                }
            } else {
                const {fileId, presignedUrl} = await requestUploadUrl(authState, file, onLogout)
                await uploadFile(presignedUrl, file, setUploadProgress)
                closeUploadModal()
                await loadFiles()
                await pollUntilComplete(fileId)
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

    const handleDeleteFile = async (file: FileMetadata): Promise<void> => {
        const confirmed = window.confirm(`Are you sure you want to delete "${file.name}"?`)
        if (!confirmed) {
            return
        }

        try {
            await deleteFile(authState, file.id, onLogout)
            await loadFiles()
        } catch (error) {
            setLoadErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        }
    }

    const handleDownloadFile = async (file: FileMetadata): Promise<void> => {
        try {
            const {url} = await getDownloadUrl(authState, file.id, onLogout)
            window.open(url, '_blank', 'noopener,noreferrer')
        } catch (error) {
            setLoadErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        }
    }

    const loadShares = async (fileId: string): Promise<void> => {
        setLoadingShares(true)
        setShareErrorMessage(null)

        try {
            const result = await getFileShares(authState, fileId, onLogout)
            setShares(result)
        } catch (error) {
            setShares([])
            setShareErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        } finally {
            setLoadingShares(false)
        }
    }

    const openShareModal = (file: FileMetadata): void => {
        setFileToShare(file)
        setShareEmail('')
        setShares([])
        setShareErrorMessage(null)
        void loadShares(file.id)
    }

    const closeShareModal = (): void => {
        setFileToShare(null)
        setShareEmail('')
        setShares([])
        setShareErrorMessage(null)
        setSharingFile(false)
    }

    const handleSubmitShare = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()

        if (!fileToShare) {
            return
        }

        const email = shareEmail.trim()
        if (!email) {
            return
        }

        setSharingFile(true)
        setShareErrorMessage(null)

        try {
            await shareFile(authState, fileToShare.id, email, onLogout)
            setShareEmail('')
            await loadShares(fileToShare.id)
        } catch (error) {
            setShareErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        } finally {
            setSharingFile(false)
        }
    }

    const handleUnshare = async (email: string): Promise<void> => {
        if (!fileToShare) {
            return
        }

        const confirmed = window.confirm(`Remove access for ${email}?`)
        if (!confirmed) {
            return
        }

        setShareErrorMessage(null)

        try {
            await unshareFile(authState, fileToShare.id, email, onLogout)
            await loadShares(fileToShare.id)
        } catch (error) {
            setShareErrorMessage(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
        }
    }

    return (
        <main className="app-shell">
            <section className="hero hero--compact">
                <div className="hero__content">
                    <h2>Welcome, {authState.email}</h2>
                    <p className="hero__copy">
                        Take a look at your files.
                    </p>
                </div>
                <div className="hero__actions">
                    <button type="button" className="primary-button" onClick={() => setIsUploadModalOpen(true)}>
                        Upload File
                    </button>
                    <button type="button" className="secondary-button" onClick={onLogout}>
                        Log out
                    </button>
                </div>
            </section>

            {isUploadModalOpen && (
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
                                <button type="button" className="secondary-button" onClick={closeUploadModal}
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
            )}

            {fileToShare && (
                <div className="modal-backdrop">
                    <div className="modal" role="dialog" aria-modal="true" aria-labelledby="share-file-title">
                        <div className="modal__header">
                            <h3 id="share-file-title">Share file</h3>
                            <span className="hero__eyebrow">Share</span>
                        </div>

                        <form className="share-file-form" onSubmit={handleSubmitShare}>
                            <div className="file-status">
                                <span className="file-status__label">File</span>
                                <strong>{fileToShare.name}</strong>
                            </div>

                            <label className="field">
                                <span>Share with</span>
                                <input
                                    type="email"
                                    name="email"
                                    autoComplete="email"
                                    value={shareEmail}
                                    onChange={(event) => {
                                        setShareEmail(event.target.value)
                                        setShareErrorMessage(null)
                                    }}
                                    placeholder="alex@example.com"
                                    required
                                />
                            </label>

                            <div>
                                <span className="share-recipients__heading">People with access</span>
                                {loadingShares ? (
                                    <p className="share-recipients__empty">Loading...</p>
                                ) : null}
                                {!loadingShares && shares.length === 0 && !shareErrorMessage ? (
                                    <p className="share-recipients__empty">Not shared with anyone yet.</p>
                                ) : null}
                                {!loadingShares && shares.length > 0 ? (
                                    <ul className="share-recipients">
                                        {shares.map((share) => (
                                            <li key={share.userId} className="share-recipients__item">
                                                <span>{share.email}</span>
                                                <a
                                                    href="#"
                                                    onClick={(event) => {
                                                        event.preventDefault()
                                                        void handleUnshare(share.email)
                                                    }}
                                                >
                                                    Remove
                                                </a>
                                            </li>
                                        ))}
                                    </ul>
                                ) : null}
                            </div>

                            {shareErrorMessage ? <p className="auth-form__error">{shareErrorMessage}</p> : null}

                            <div className="modal__actions">
                                <button type="button" className="secondary-button" onClick={closeShareModal}
                                        disabled={sharingFile}>
                                    Cancel
                                </button>
                                <button type="submit" className="primary-button"
                                        disabled={!shareEmail.trim() || sharingFile}>
                                    {sharingFile ? 'Sharing...' : 'Share'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            <section className="panel-grid">
                <article className="panel panel--wide">
                    <h2>Files</h2>
                    <div className="files-table-container">
                        <table className="files-table">
                            <thead>
                            <tr>
                                <th scope="col">Name</th>
                                <th scope="col">Status</th>
                                <th scope="col">Size</th>
                                <th scope="col">Action</th>
                            </tr>
                            </thead>
                            <tbody>
                            {loadingFiles ? (
                                <tr>
                                    <td colSpan={4}>Loading files...</td>
                                </tr>
                            ) : null}
                            {!loadingFiles && loadErrorMessage ? (
                                <tr>
                                    <td colSpan={4}>{loadErrorMessage}</td>
                                </tr>
                            ) : null}
                            {!loadingFiles && !loadErrorMessage && files.length === 0 ? (
                                <tr>
                                    <td colSpan={4}>No files available yet.</td>
                                </tr>
                            ) : null}
                            {files.map((file: FileMetadata) => (
                                <tr key={file.id}>
                                    <td>{file.name}</td>
                                    <td>{file.status}</td>
                                    <td>{formatFileSize(file.size)}</td>
                                    <td>
                                        <span style={{
                                            display: 'inline-flex',
                                            gap: '1rem',
                                            justifyContent: 'flex-end',
                                            minWidth: '9rem'
                                        }}>
                                            {file.status === 'COMPLETED' ? (
                                                <a
                                                    href="#"
                                                    onClick={(e) => {
                                                        e.preventDefault()
                                                        void handleDownloadFile(file)
                                                    }}
                                                >
                                                    Download
                                                </a>
                                            ) : (
                                                <span style={{visibility: 'hidden'}}>Download</span>
                                            )}
                                            <a
                                                href="#"
                                                onClick={(e) => {
                                                    e.preventDefault()
                                                    void handleDeleteFile(file)
                                                }}
                                            >
                                                Delete
                                            </a>
                                            {file.status === 'COMPLETED' ? (
                                                <a
                                                    href="#"
                                                    onClick={(e) => {
                                                        e.preventDefault()
                                                        openShareModal(file)
                                                    }}
                                                >
                                                    Share
                                                </a>
                                            ) : (
                                                <span style={{visibility: 'hidden'}}>Share</span>
                                            )}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </article>
            </section>

            <div className="horizontal-divider"></div>

            <section className="panel-grid">
                <article className="panel panel--wide">
                    <h2>Files shared with me</h2>
                    <div className="files-table-container">
                        <table className="files-table">
                            <thead>
                            <tr>
                                <th scope="col">Name</th>
                                <th scope="col">Status</th>
                                <th scope="col">Size</th>
                                <th scope="col">Uploader</th>
                                <th scope="col">Action</th>
                            </tr>
                            </thead>
                            <tbody>
                            {loadingSharedFiles ? (
                                <tr>
                                    <td colSpan={5}>Loading files...</td>
                                </tr>
                            ) : null}
                            {!loadingSharedFiles && sharedLoadErrorMessage ? (
                                <tr>
                                    <td colSpan={5}>{sharedLoadErrorMessage}</td>
                                </tr>
                            ) : null}
                            {!loadingSharedFiles && !sharedLoadErrorMessage && sharedFiles.length === 0 ? (
                                <tr>
                                    <td colSpan={5}>No files have been shared with you yet.</td>
                                </tr>
                            ) : null}
                            {sharedFiles.map((file: FileMetadata) => (
                                <tr key={file.id}>
                                    <td>{file.name}</td>
                                    <td>{file.status}</td>
                                    <td>{formatFileSize(file.size)}</td>
                                    <td>{file.uploadedBy}</td>
                                    <td>
                                        <a
                                            href="#"
                                            onClick={(e) => {
                                                e.preventDefault()
                                                void handleDownloadFile(file)
                                            }}
                                        >
                                            Download
                                        </a>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </article>
            </section>
        </main>
    )
}
