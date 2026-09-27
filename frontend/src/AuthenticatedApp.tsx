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
import {ShareModal} from "./components/ShareModal.tsx";
import {UploadModal} from "./components/UploadModal.tsx";
import {FilesTable} from "./components/FilesTable.tsx";

interface AuthenticatedAppProps {
    authState: AuthState
    onLogout: () => void
}

export function AuthenticatedApp({authState, onLogout}: AuthenticatedAppProps) {
    const [files, setFiles] = React.useState<FileMetadata[]>([])
    const [loadingFiles, setLoadingFiles] = React.useState(true)
    const [loadErrorMessage, setLoadErrorMessage] = React.useState<string | null>(null)

    const [sharedFiles, setSharedFiles] = React.useState<FileMetadata[]>([])
    const [loadingSharedFiles, setLoadingSharedFiles] = React.useState(true)
    const [sharedLoadErrorMessage, setSharedLoadErrorMessage] = React.useState<string | null>(null)

    const [isUploadModalOpen, setIsUploadModalOpen] = React.useState(false)
    const [fileToShare, setFileToShare] = React.useState<FileMetadata | null>(null)

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

    const handleDownloadFile = async (
        file: FileMetadata,
        setError: (message: string | null) => void
    ): Promise<void> => {
        try {
            const {url} = await getDownloadUrl(authState, file.id, onLogout)
            window.open(url, '_blank', 'noopener,noreferrer')
        } catch (error) {
            setError(error instanceof Error ? error.message : GENERIC_ERROR_MESSAGE)
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

            <FilesTable title={"Files"}
                        files={files}
                        loadingFiles={loadingFiles}
                        loadErrorMessage={loadErrorMessage}
                        showDelete={true}
                        showShare={true}
                        showUploader={false}
                        handleDownload={(file) => handleDownloadFile(file, setLoadErrorMessage)}
                        handleDelete={handleDeleteFile}
                        handleShare={(file) => setFileToShare(file)}/>

            <div className="horizontal-divider"></div>

            <FilesTable title={"Files shared with me"}
                        files={sharedFiles}
                        loadingFiles={loadingSharedFiles}
                        loadErrorMessage={sharedLoadErrorMessage}
                        showDelete={false}
                        showShare={false}
                        showUploader={true}
                        handleDownload={(file) => handleDownloadFile(file, setSharedLoadErrorMessage)}
            />

        </main>
    )
}
