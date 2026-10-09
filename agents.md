# Boxdrop agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth, file metadata, and S3/SQS integration.
- `design/design.md`: High-level product and system-design target for future iterations; read it before making architectural changes.
- `docker-compose.yml`: Local stack — MongoDB, MinIO, ElasticMQ, MinIO→SQS webhook bridge (`infra/`), and the backend. Frontend compose service is commented out.

## Running locally

- Follow `README.md` for the required root `.env` values and startup steps. Compose reads the root `.env`; `backend/.env.example` is not a complete Compose configuration and uses host-facing endpoints.
- Run `docker compose up --build` from the repository root for the backend and infrastructure. Run `npm ci && npm run dev` in `frontend/` separately (the Compose frontend service is disabled).
- Backend-to-service URLs use Compose hostnames (`mongodb`, `minio`, `elasticmq`); `APP_STORAGE_PUBLIC_ENDPOINT` must use `http://localhost:9000` so presigned URLs work in the browser. Allow `http://localhost:5173` via `APP_FRONTEND_ORIGIN`.
- Generate a local Base64 JWT signing key with `openssl rand -base64 32`. The frontend defaults to `http://localhost:8080/api`; the UI runs at `http://localhost:5173`.

## Current implementation status

- Auth works end-to-end, including access-token refresh via an httpOnly refresh-token cookie.
- Protected UI supports listing, uploading, downloading, deleting, and sharing files.
- File APIs (`/api/files`):
  - `GET /` — list `PENDING`, `COMPLETED`, and `FAILED` files owned by the current user (no download URL on the list DTO; `uploadedBy` is the owner user id; `resumable` is true for a `PENDING` multipart upload)
  - `POST /upload/presigned-url` — single-shot upload (files ≤ 5MB): create `PENDING` metadata, return `{ fileId, presignedUrl }`
  - `POST /exists` — `{ filename, fingerprint }` → `{ exists, fileId?, status? }` for this user's file with that name and SHA-256 fingerprint
  - `POST /multipart-upload` — files > 5MB: create `PENDING` metadata with `NOT_UPLOADED` chunks, start an S3 multipart upload, return `{ fileId }`
  - `POST /multipart-upload/presigned-url` — `{ fileId, partNumber }` → `{ url }` (15-minute part URL)
  - `PATCH /multipart-upload` — `{ fileId, partNumber, fingerprint, etag }`; `ListParts` must already contain that ETag before the chunk is marked `UPLOADED`
  - `POST /multipart-upload/complete` — verify every chunk against `ListParts`, call S3 `CompleteMultipartUpload`, mark metadata `COMPLETED` (idempotent if already `COMPLETED`)
  - `GET /download/{fileId}` — short-lived presigned GET URL for the owner or a user the file was shared with (a presigned S3 URL, not a CDN URL)
  - `DELETE /{fileId}` — owner deletes the object, aborts an in-progress multipart upload, `shared_file` rows, and metadata
  - `GET /shared` — `COMPLETED` files shared with the current user; `uploadedBy` is the uploader's email
  - `GET /{fileId}/shares` — owner lists recipients `{ userId, email }[]`
  - `POST /{fileId}/share` — owner shares a `COMPLETED` file by email `{ email }`; idempotent; rejects self-share
  - `DELETE /{fileId}/share` — owner revokes access by email `{ email }`
- `POST /multipart-upload/parts` is commented out. A `PENDING` match is rejected in the UI ("Resume not implemented"). A `COMPLETED` fingerprint match is a duplicate; `FAILED` is rejected.
- Single-shot uploads finish when MinIO notifies the webhook, `sqs-bridge` forwards to ElasticMQ, and `S3ObjectCreatedListener` calls `FileService.completeByStorageKey`. Multipart uploads finish when the client calls `POST /multipart-upload/complete`.
- `PendingUploadSweeper` runs every 60s: `PENDING` older than 15 minutes with an S3 object → `COMPLETED` (missed SQS); otherwise → `FAILED`. Late SQS can still move `FAILED` → `COMPLETED`. Existing rows without `createdAt` are ignored. An unfinished multipart upload has no assembled object, so the sweeper marks it `FAILED`.
- Files ≤ 5MB: the UI shows XHR progress, then `AuthenticatedApp.pollUntilComplete` polls `GET /api/files` until that file is `COMPLETED` or `FAILED`. Files > 5MB: `UploadModal` chunks at 5MB, uploads up to 10 parts at a time, completes, then reloads the list without polling.
- Uploads are validated server-side: max 50GB; `image/png`, `image/jpeg`, `application/pdf`. Single-shot presigned PUT URLs last 10 minutes.
- Metadata lives in MongoDB (`file_metadata`) with `PENDING | COMPLETED | FAILED`, plus `fingerprint`, `s3UploadId`, and `fileChunks` (`partNumber`, `fingerprint`, `etag`, `NOT_UPLOADED | UPLOADED`) for multipart uploads.
- `/api/user` returns `{ id, email }`; frontend loads it on auth via `frontend/src/api/userApi.ts`.
- Sharing is implemented (normalized `shared_file` collection, share-by-email, recipient download). Sync, resumable uploads, and CDN downloads are still design-doc targets.

## Frontend conventions

- API base URL: `VITE_API_BASE_URL`, default `http://localhost:8080/api`.
- Persist `accessToken` + `tokenType` in local storage (`frontend/src/auth/authStorage.ts`); send `Authorization: <tokenType> <accessToken>`.
- Refresh token is httpOnly, never in JS; cookie scoped to `/api/auth`.
- `frontend/src/api/httpClient.ts`: on `401`, de-duped `/api/auth/refresh`, retry once, else clear auth + `onUnauthorized`.
- File client: `frontend/src/api/fileApi.ts` — `requestUploadUrl`, direct `PUT` via `uploadFile`, `fileExists`, `initiateMultipartUpload`, `getPresignedUrlForMultipartUpload`, `uploadPart`, `patchMultipartUpload`, `completeMultipartUpload`, `getDownloadUrl`, `deleteFile`, `getSharedFiles`, `getFileShares`, `shareFile`, `unshareFile`.
- UI: `AuthenticatedApp.tsx` owns the lists and completion polling; upload, share, and tables live in `components/UploadModal.tsx`, `components/ShareModal.tsx`, and `components/FilesTable.tsx`. Local state, visible API errors, CSS in `App.css`. Fingerprints are SHA-256 hex via `toHex` in `frontend/src/utils.ts`.

## Backend conventions

- Routes under `/api`. Public: `/error`, `/actuator/health`, `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`. `/api/user` and `/api/files/**` need a bearer access token.
- JWT is stateless (`SecurityConfig`). Refresh tokens are server-side (`RefreshTokenService` / Mongo) and set as httpOnly `refresh_token` by `AuthController`.
- API errors return `{ "code": "USER_NOT_FOUND", "message": "No account exists for that email." }`. The UI shows `message` for 4xx and a generic line for 5xx / network failures (`parseErrorMessage` in `frontend/src/api/common.ts`).
- Storage: AWS S3 SDK against MinIO locally (`S3Service`, `StorageProperties`) — single PUT plus multipart create, presign, list, complete, and abort. SQS via `SqsProperties` + scheduled `S3ObjectCreatedListener`. Stale pending uploads via `PendingUploadSweeper`.
- Keep contracts aligned with the frontend; read `design/design.md` before architectural changes.
