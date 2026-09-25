import type {AuthState} from "../auth/types.ts";
import * as React from "react";
import {getFileShares, shareFile, unshareFile} from "../api/fileApi.ts";
import {GENERIC_ERROR_MESSAGE} from "../api/common.ts";
import type {FileMetadata, FileShare} from "../api/fileTypes.ts";
import {useEffect} from "react";

interface IShareModalProps {
    authState: AuthState
    fileToShare: FileMetadata
    onLogout: () => void
    onClose: () => void
}

export function ShareModal({authState, onLogout, fileToShare, onClose}: IShareModalProps) {

    const [shareEmail, setShareEmail] = React.useState('')
    const [sharingFile, setSharingFile] = React.useState(false)
    const [shareErrorMessage, setShareErrorMessage] = React.useState<string | null>(null)
    const [loadingShares, setLoadingShares] = React.useState(false)
    const [shares, setShares] = React.useState<FileShare[]>([])

    useEffect(() => {
        void loadShares(fileToShare.id)
    }, [fileToShare])

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

    const handleSubmitShare = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault()

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
                        <button type="button" className="secondary-button" onClick={onClose}
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
    )
}
