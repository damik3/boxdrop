import {useRef, type ChangeEvent, type ReactNode} from "react";
import type {FileMetadata} from "../api/fileTypes.ts";
import {formatFileSize} from "../utils.ts";

function ActionButton({label, tone, onClick, children}: {
    label: string
    tone: 'download' | 'delete' | 'share' | 'resume'
    onClick: () => void
    children: ReactNode
}) {
    return (
        <button
            type="button"
            className={`files-table__action files-table__action--${tone}`}
            aria-label={label}
            data-tooltip={label}
            onClick={onClick}
        >
            {children}
        </button>
    )
}

function DownloadIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 4v10"/>
            <path d="m8 10 4 4 4-4"/>
            <path d="M5 19h14"/>
        </svg>
    )
}

function DeleteIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M4 7h16"/>
            <path d="M9 7V5h6v2"/>
            <path d="M7 7l1 12h8l1-12"/>
        </svg>
    )
}

function CheckIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M5 12.5 10 17.5 19 7"/>
        </svg>
    )
}

function PendingIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M12 4a8 8 0 1 1-8 8"/>
        </svg>
    )
}

function IncompleteIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M9 6v12"/>
            <path d="M15 6v12"/>
        </svg>
    )
}

function ResumeIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M8 5v14l11-7z"/>
        </svg>
    )
}

function FailedIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="8"/>
            <path d="m9 9 6 6"/>
            <path d="m15 9-6 6"/>
        </svg>
    )
}

function FileStatus({status, resumable}: {status: FileMetadata['status'], resumable: boolean}) {
    if (resumable) {
        return (
            <span
                className="files-table__status files-table__status--incomplete"
                role="img"
                aria-label="Not finished"
                data-tooltip="Not finished"
            >
                <IncompleteIcon/>
            </span>
        )
    }

    const label = status === 'COMPLETED' ? 'Completed' : status === 'PENDING' ? 'In progress' : 'Failed'

    return (
        <span
            className={`files-table__status files-table__status--${status.toLowerCase()}`}
            role="img"
            aria-label={label}
            data-tooltip={label}
        >
            {status === 'COMPLETED' ? <CheckIcon/> : status === 'PENDING' ? <PendingIcon/> : <FailedIcon/>}
        </span>
    )
}

function ShareIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="6" cy="12" r="2"/>
            <circle cx="16" cy="7" r="2"/>
            <circle cx="16" cy="17" r="2"/>
            <path d="M8 11.2 14 8.2"/>
            <path d="M8 12.8 14 15.8"/>
        </svg>
    )
}

interface Props {
    title: string
    files: FileMetadata[]
    loadingFiles: boolean
    loadErrorMessage: string | null
    showDelete: boolean
    showShare: boolean
    showUploader: boolean
    handleDownload: (file: FileMetadata) => void
    handleDelete?: (file: FileMetadata) => void
    handleShare?: (file: FileMetadata) => void
    handleResume?: (file: FileMetadata, selected: File) => void
}

export function FilesTable({
                               title,
                               files,
                               loadingFiles,
                               loadErrorMessage,
                               showDelete,
                               showShare,
                               showUploader,
                               handleDownload,
                               handleDelete,
                               handleShare,
                               handleResume,
                           }: Props) {
    const resumeInputRef = useRef<HTMLInputElement | null>(null)
    const resumeTargetRef = useRef<FileMetadata | null>(null)

    const openResumePicker = (file: FileMetadata): void => {
        resumeTargetRef.current = file
        if (resumeInputRef.current) {
            resumeInputRef.current.value = ''
            resumeInputRef.current.click()
        }
    }

    const handleResumeFilePicked = (event: ChangeEvent<HTMLInputElement>): void => {
        const selected = event.target.files?.[0]
        const target = resumeTargetRef.current
        resumeTargetRef.current = null
        if (selected && target) {
            handleResume?.(target, selected)
        }
    }

    return (
        <section className="panel-grid">
            <article className="panel panel--wide">
                <h2>{title}</h2>
                <div className="files-table-container">
                    <table className={showUploader ? 'files-table files-table--with-uploader' : 'files-table'}>
                        <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col">Status</th>
                            <th scope="col">Size</th>
                            {showUploader && <th scope="col">Uploader</th>}
                            <th scope="col">Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {loadingFiles ? (
                                <tr>
                                    <td colSpan={showUploader ? 5 : 4}>Loading files...</td>
                                </tr>
                            ) :
                            null
                        }
                        {
                            !loadingFiles && loadErrorMessage ? (
                                <tr>
                                    <td colSpan={showUploader ? 5 : 4}>{loadErrorMessage}</td>
                                </tr>
                            ) : null
                        }
                        {
                            !loadingFiles && !loadErrorMessage && files.length === 0 ? (
                                <tr>
                                    <td colSpan={showUploader ? 5 : 4}>No files available yet.</td>
                                </tr>
                            ) : null
                        }
                        {
                            files.map((file: FileMetadata) => (
                                <tr key={file.id}>
                                    <td title={file.name}>{file.name}</td>
                                    <td><FileStatus status={file.status} resumable={file.resumable}/></td>
                                    <td>{formatFileSize(file.size)}</td>
                                    {showUploader && (
                                        <td>
                                            <span className="files-table__uploader" data-tooltip={file.uploadedBy}>
                                                <span className="files-table__uploader-text">{file.uploadedBy}</span>
                                            </span>
                                        </td>
                                    )}
                                    <td>
                                        <span className="files-table__actions">
                                            {file.resumable && handleResume ? (
                                                <ActionButton label="Resume" tone="resume" onClick={() => openResumePicker(file)}>
                                                    <ResumeIcon/>
                                                </ActionButton>
                                            ) : file.status === 'COMPLETED' ? (
                                                <ActionButton label="Download" tone="download" onClick={() => handleDownload(file)}>
                                                    <DownloadIcon/>
                                                </ActionButton>
                                            ) : (
                                                <span className="files-table__action files-table__action--spacer" aria-hidden="true"/>
                                            )}
                                            {showDelete && (
                                                <ActionButton label="Delete" tone="delete" onClick={() => handleDelete?.(file)}>
                                                    <DeleteIcon/>
                                                </ActionButton>
                                            )}
                                            {showShare && (
                                                file.status === 'COMPLETED' ? (
                                                    <ActionButton label="Share" tone="share" onClick={() => handleShare?.(file)}>
                                                        <ShareIcon/>
                                                    </ActionButton>
                                                ) : (
                                                    <span className="files-table__action files-table__action--spacer" aria-hidden="true"/>
                                                )
                                            )}
                                        </span>
                                    </td>
                                </tr>
                            ))
                        }
                        </tbody>
                    </table>
                </div>
                {handleResume && (
                    <input
                        ref={resumeInputRef}
                        type="file"
                        style={{display: 'none'}}
                        onChange={handleResumeFilePicked}
                    />
                )}
            </article>
        </section>
    )
}