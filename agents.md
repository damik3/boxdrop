# Agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth, file metadata, and S3/SQS integration.
- `design/design.md`: High-level product and system-design target for future iterations; read it before making architectural changes.
- `docker-compose.yml`: Local stack — MongoDB, MinIO, ElasticMQ, MinIO→SQS webhook bridge (`infra/`), and the backend. Frontend compose service is commented out.

## Current implementation status

- Auth works end-to-end, including access-token refresh via an httpOnly refresh-token cookie.
- Protected UI supports listing, uploading, downloading, deleting, and sharing files.
- File APIs (`/api/files`):
  - `GET /` — list `PENDING`, `COMPLETED`, and `FAILED` files owned by the current user (no download URL on the list DTO)
  - `POST /upload/presigned-url` — create `PENDING` metadata, return `{ fileId, presignedUrl }`
  - `GET /download/{fileId}` — return a short-lived presigned GET URL for the owner or a user the file was shared with (not a CDN URL)
  - `DELETE /{fileId}` — owner deletes object, `shared_file` rows, and metadata
  - `GET /shared` — `COMPLETED` files shared with the current user
  - `GET /{fileId}/shares` — owner lists recipients `{ userId, email }[]`
  - `POST /{fileId}/share` — owner shares a `COMPLETED` file by email `{ email }`; idempotent; rejects self-share
  - `DELETE /{fileId}/share` — owner revokes access by email `{ email }`
- There is **no** client-facing upload-complete endpoint. Client PUTs to the presigned URL; MinIO notifies a webhook; `sqs-bridge` forwards to ElasticMQ; `S3ObjectCreatedListener` polls SQS and `FileService.completeByStorageKey` marks metadata `COMPLETED`.
- `PendingUploadSweeper` runs every 60s: `PENDING` older than 15 minutes with an S3 object → `COMPLETED` (missed SQS); otherwise → `FAILED`. Late SQS can still move `FAILED` → `COMPLETED`. Existing rows without `createdAt` are ignored.
- After PUT, the UI shows upload progress, reloads, then polls `GET /api/files` until that file is `COMPLETED` or `FAILED` (`AuthenticatedApp.pollUntilComplete`).
- Uploads are validated server-side: max 50MB; `image/png`, `image/jpeg`, `application/pdf`.
- Metadata lives in MongoDB (`file_metadata`) with `PENDING | COMPLETED | FAILED`.
- `/api/user` returns `{ id, email }`; frontend loads it on auth via `frontend/src/api/userApi.ts`.
- Sharing is implemented (normalized `shared_file` collection, share-by-email, recipient download). Sync and 50GB files are still design-doc targets.

## Frontend conventions

- API base URL: `VITE_API_BASE_URL`, default `http://localhost:8080/api`.
- Persist `accessToken` + `tokenType` in local storage (`frontend/src/auth/authStorage.ts`); send `Authorization: <tokenType> <accessToken>`.
- Refresh token is httpOnly, never in JS; cookie scoped to `/api/auth`.
- `frontend/src/api/httpClient.ts`: on `401`, de-duped `/api/auth/refresh`, retry once, else clear auth + `onUnauthorized`.
- File client: `frontend/src/api/fileApi.ts` — `requestUploadUrl`, direct `PUT` via `uploadFile`, `getDownloadUrl`, `deleteFile`, `getSharedFiles`, `getFileShares`, `shareFile`, `unshareFile`.
- Match `App.tsx`, `AuthenticatedApp.tsx`, `frontend/src/auth/*`: local state, visible API errors, CSS in `App.css`.

## Backend conventions

- Routes under `/api`. Public: `/error`, `/actuator/health`, `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`. `/api/user` and `/api/files/**` need a bearer access token.
- JWT is stateless (`SecurityConfig`). Refresh tokens are server-side (`RefreshTokenService` / Mongo) and set as httpOnly `refresh_token` by `AuthController`.
- API errors return `{ "code": "USER_NOT_FOUND", "message": "No account exists for that email." }`. The UI shows `message` for 4xx and a generic line for 5xx / network failures (`parseErrorMessage` in `frontend/src/api/common.ts`).
- Storage: AWS S3 SDK against MinIO locally (`S3Service`, `StorageProperties`). SQS via `SqsProperties` + scheduled `S3ObjectCreatedListener`. Stale pending uploads via `PendingUploadSweeper`.
- Keep contracts aligned with the frontend; read `design/design.md` before architectural changes.

## Working guidance for future agents

- Learning project; intentionally incomplete. Prefer surgical changes.
- Do not silently mock missing backend behavior unless asked.
- If you change frontend or backend contracts, update both sides or keep failures visible.
