import type { AuthState } from './auth/types.ts'
import {useEffect} from "react";
import {getFiles} from "./api/fileApi.ts";
import type {FileMetadata} from "./api/types.ts";
import * as React from "react";

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
  const [fileInputState, setFileInputState] = React.useState<FileInput>({file: null});
  const fileRef = React.useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    const loadFiles = async () => {
      const result = await getFiles()
      setFiles(result)
    }
    void loadFiles()
  }, [])

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault()

    if (!fileInputState.file) {
      return
    }

    console.log('File to be uploaded', fileInputState.file.name)
    setIsModalOpen(false)
  }

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const selectedFile = event.target.files?.[0] ?? null
    setFileInputState({ file: selectedFile })
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

              <div className="modal__actions">
                <button type="button" className="secondary-button" onClick={() => setIsModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="primary-button" disabled={!fileInputState.file}>
                  Upload
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
                {files.map((file: FileMetadata) => (
                  <tr key={file.url}>
                    <td>{file.name}</td>
                    <td>{file.size}</td>
                    <td>{file.uploadedBy}</td>
                    <td>
                      <a href={file.url} target="_blank" rel="noopener noreferrer">Download</a>
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
