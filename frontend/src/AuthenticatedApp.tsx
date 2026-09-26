import type {AuthState} from './auth/types.ts'
import {useEffect} from 'react'
import * as React from 'react'
import {
    deleteFile,
    getDownloadUrl,
    getFiles,
    getSharedFiles,
} from './api/fileApi.ts'
import {GENERIC_ERROR_MESSAGE} from './api/common.ts'
import type {FileMetadata} from './api/fileTypes.ts'
import {formatFileSize} from "./utils.ts";
import {ShareModal} from "./components/ShareModal.tsx";
import {UploadModal} from "./components/UploadModal.tsx";

interface AuthenticatedAppProps {
    authState: AuthState
    onLogout: () => void
}

export function AuthenticatedApp({authState, onLogout}: AuthenticatedAppProps) {
    const [isUploadModalOpen, setIsUploadModalOpen] = React.useState(false)
    const [fileToShare, setFileToShare] = React.useState<FileMetadata | null>(null)
    const [files, setFiles] = React.useState<FileMetadata[]>([])
    const [sharedFiles, setSharedFiles] = React.useState<FileMetadata[]>([])
    const [loadErrorMessage, setLoadErrorMessage] = React.useState<string | null>(null)
    const [sharedLoadErrorMessage, setSharedLoadErrorMessage] = React.useState<string | null>(null)
    const [loadingFiles, setLoadingFiles] = React.useState(true)
    const [loadingSharedFiles, setLoadingSharedFiles] = React.useState(true)

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
                <UploadModal
                    authState={authState}
                    onLogout={onLogout}
                    onClose={() => {
                        setIsUploadModalOpen(false)
                    }}
                    onUploaded={(fileId) => {
                        setIsUploadModalOpen(false)
                        void (async () => {
                            await loadFiles()
                            if (fileId) {
                                await pollUntilComplete(fileId)
                            }
                        })()
                    }}
                />
            )}

            {fileToShare && (
                <ShareModal
                    authState={authState}
                    fileToShare={fileToShare}
                    onLogout={onLogout}
                    onClose={() => {
                        setFileToShare(null)
                    }}
                />
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
                                ) :
                                null
                            }
                            {
                                !loadingFiles && loadErrorMessage ? (
                                    <tr>
                                        <td colSpan={4}>{loadErrorMessage}</td>
                                    </tr>
                                ) : null
                            }
                            {
                                !loadingFiles && !loadErrorMessage && files.length === 0 ? (
                                    <tr>
                                        <td colSpan={4}>No files available yet.</td>
                                    </tr>
                                ) : null
                            }
                            {
                                files.map((file: FileMetadata) => (
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
                                                        setFileToShare(file)
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
                                ))
                            }
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
