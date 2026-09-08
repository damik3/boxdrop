import type { AuthState } from './auth/types.ts'
import { useEffect } from 'react'
import * as React from 'react'
import {deleteFile, getDownloadUrl, getFiles, markUploadCompleted, requestUploadUrl, uploadFile} from './api/fileApi.ts'
import type { FileMetadata } from './api/types.ts'

interface AuthenticatedAppProps {
  authState: AuthState
  onLogout: () => void
}

interface FileInput {
  file: File | null
}

export function AuthenticatedApp({ authState, onLogout }: AuthenticatedAppProps) {
  const [isModalOpen, setIsModalOpen] = React.useState(false)
  const [files, setFiles] = React.useState<FileMetadata[]>([])
  const [fileInputState, setFileInputState] = React.useState<FileInput>({ file: null })
  const [loadErrorMessage, setLoadErrorMessage] = React.useState<string | null>(null)
  const [uploadErrorMessage, setUploadErrorMessage] = React.useState<string | null>(null)
  const [loadingFiles, setLoadingFiles] = React.useState(true)
  const [uploadingFile, setUploadingFile] = React.useState(false)
  const fileRef = React.useRef<HTMLInputElement | null>(null)

  const loadFiles = React.useCallback(async () => {
    setLoadingFiles(true)
    setLoadErrorMessage(null)

    try {
      const result = await getFiles(authState)
      setFiles(result)
    } catch (error) {
      setLoadErrorMessage(error instanceof Error ? error.message : 'Failed to load files')
    } finally {
      setLoadingFiles(false)
    }
  }, [authState])

  useEffect(() => {
    void loadFiles()
  }, [loadFiles])

  const closeUploadModal = (): void => {
    setIsModalOpen(false)
    setUploadingFile(false)
    setUploadErrorMessage(null)
    setFileInputState({ file: null })
    if (fileRef.current) {
      fileRef.current.value = ''
    }
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault()

    if (!fileInputState.file) {
      return
    }

    setUploadingFile(true)
    setUploadErrorMessage(null)

    try {
      const { fileId, presignedUrl } = await requestUploadUrl(authState, fileInputState.file)
      await uploadFile(presignedUrl, fileInputState.file)
      await markUploadCompleted(authState, fileId)
      closeUploadModal()
      await loadFiles()
    } catch (error) {
      setUploadErrorMessage(error instanceof Error ? error.message : 'File upload failed')
      setUploadingFile(false)
    }
  }

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const selectedFile = event.target.files?.[0] ?? null
    setUploadErrorMessage(null)
    setFileInputState({ file: selectedFile })
  }

  const handleDeleteFile = async (file: FileMetadata): Promise<void> => {
    const confirmed = window.confirm(`Are you sure you want to delete "${file.name}"?`)
    if (!confirmed) {
      return
    }

    try {
      await deleteFile(authState, file.id)
      await loadFiles()
    } catch (error) {
      setLoadErrorMessage(error instanceof Error ? error.message : 'Failed to delete file')
    }
  }

  return (
    <main className="app-shell">
      <section className="hero hero--compact">
        <div className="hero__content">
          <span className="hero__eyebrow">Authenticated session</span>
          <h1>Welcome, {authState.email}</h1>
          <p className="hero__copy">
            Take a look at your files.
          </p>
        </div>
        <div className="hero__actions">
          <button type="button" className="primary-button" onClick={() => setIsModalOpen(true)}>
            Upload File
          </button>
          <button type="button" className="secondary-button" onClick={onLogout}>
            Log out
          </button>
        </div>
      </section>

      {isModalOpen && (
        <div className="modal-backdrop">
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="upload-file-title">
            <div className="modal__header">
              <div>
                <span className="hero__eyebrow">Transfer</span>
                <h3 id="upload-file-title">Upload file</h3>
              </div>
            </div>

            <form className="upload-file-form" onSubmit={handleSubmit}>
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
                  style={{ display: 'none' }}
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

              {uploadErrorMessage ? <p className="auth-form__error">{uploadErrorMessage}</p> : null}

              <div className="modal__actions">
                <button type="button" className="secondary-button" onClick={closeUploadModal} disabled={uploadingFile}>
                  Cancel
                </button>
                <button type="submit" className="primary-button" disabled={!fileInputState.file || uploadingFile}>
                  {uploadingFile ? 'Uploading...' : 'Upload'}
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
                  <th scope="col">Size</th>
                  <th scope="col">Uploader</th>
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
                    <td>{file.size}</td>
                    <td>{file.uploadedBy}</td>
                    <td style={{display: 'flex', justifyContent: 'space-between'}}>
                      <a
                          href="#"
                          onClick={async (e) => {
                            e.preventDefault()
                            const { url } = await getDownloadUrl(authState, file.id)
                            window.open(url, '_blank', 'noopener,noreferrer')
                          }}
                      >
                        Download
                      </a>
                      <a
                          href="#"
                          onClick={(e) => {
                            e.preventDefault()
                            void handleDeleteFile(file)
                          }}
                      >
                          Delete
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
