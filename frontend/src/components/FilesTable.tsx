import type {FileMetadata} from "../api/fileTypes.ts";
import {formatFileSize} from "../utils.ts";

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
                               handleShare
                           }: Props) {

    return (
        <section className="panel-grid">
            <article className="panel panel--wide">
                <h2>{title}</h2>
                <div className="files-table-container">
                    <table className="files-table">
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
                                    <td>{file.name}</td>
                                    <td>{file.status}</td>
                                    <td>{formatFileSize(file.size)}</td>
                                    {showUploader && <td>{file.uploadedBy}</td>}
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
                                                        handleDownload(file)
                                                    }}
                                                >
                                                    Download
                                                </a>
                                            ) : (
                                                <span style={{visibility: 'hidden'}}>Download</span>
                                            )}
                                            {showDelete && (<a
                                                href="#"
                                                onClick={(e) => {
                                                    e.preventDefault()
                                                    handleDelete && handleDelete(file)
                                                }}
                                            >
                                                Delete
                                            </a>)}
                                            {showShare && (
                                                file.status === 'COMPLETED' ? (
                                                    <a href="#" onClick={(event) => {
                                                        event.preventDefault();
                                                        handleShare && handleShare(file)
                                                    }}>
                                                        Share
                                                    </a>
                                                ) : (
                                                    <span style={{visibility: 'hidden'}}>Share</span>
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
            </article>
        </section>
    )
}