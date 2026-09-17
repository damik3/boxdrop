# Agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth, file metadata, and S3/SQS integration.
- `design/design.md`: High-level product and system-design target for future iterations; read it before making architectural changes.
- `docker-compose.yml`: Local stack — MongoDB, MinIO, ElasticMQ, MinIO→SQS webhook bridge (`infra/`), and the backend. Frontend compose service is commented out.

## Current implementation status

- Auth works end-to-end, including access-token refresh via an httpOnly refresh-token cookie.
- Protected UI supports listing, uploading, downloading, and deleting files.
- File APIs (`/api/files`):
  - `GET /` — list `PENDING` and `COMPLETED` files for the current user
  - `POST /upload/presigned-url-for-upload` — create `PENDING` metadata, return `{ fileId, presignedUrl }`
  - `GET /download/{fileId}` — return a short-lived presigned GET URL (not a CDN URL)
  - `DELETE /{fileId}` — delete object + metadata
- There is **no** client-facing upload-complete endpoint. Client PUTs to the presigned URL; MinIO notifies a webhook; `sqs-bridge` forwards to ElasticMQ; `S3ObjectCreatedListener` polls SQS and `FileService.completeByStorageKey` marks metadata `COMPLETED`.
- After PUT, the UI reloads then polls `GET /api/files` until that file is `COMPLETED` (`AuthenticatedApp.pollUntilComplete`).
- Uploads are validated server-side: max 50MB; `image/png`, `image/jpeg`, `application/pdf`.
- Metadata lives in MongoDB (`file_metadata`) with `PENDING | COMPLETED | FAILED`.
- `/api/user` returns `{ id, email }`; frontend loads it on auth via `frontend/src/api/userApi.ts`.
- Sharing, sync, and 50GB files are still design-doc targets, not implemented.

## Frontend conventions

- API base URL: `VITE_API_BASE_URL`, default `http://localhost:8080/api`.
- Persist `accessToken` + `tokenType` in local storage (`frontend/src/auth/authStorage.ts`); send `Authorization: <tokenType> <accessToken>`.
- Refresh token is httpOnly, never in JS; cookie scoped to `/api/auth`.
- `frontend/src/api/httpClient.ts`: on `401`, de-duped `/api/auth/refresh`, retry once, else clear auth + `onUnauthorized`.
- File client: `frontend/src/api/fileApi.ts` — `requestUploadUrl`, direct `PUT` via `uploadFile`, `getDownloadUrl`, `deleteFile`.
- Match `App.tsx`, `AuthenticatedApp.tsx`, `frontend/src/auth/*`: local state, visible API errors, CSS in `App.css`.

## Backend conventions

- Routes under `/api`. Public: `/error`, `/actuator/health`, `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`. `/api/user` and `/api/files/**` need a bearer access token.
- JWT is stateless (`SecurityConfig`). Refresh tokens are server-side (`RefreshTokenService` / Mongo) and set as httpOnly `refresh_token` by `AuthController`.
- Storage: AWS S3 SDK against MinIO locally (`S3Service`, `StorageProperties`). SQS via `SqsProperties` + scheduled `S3ObjectCreatedListener`.
- Keep contracts aligned with the frontend; read `design/design.md` before architectural changes.

## Working guidance for future agents

- Learning project; intentionally incomplete. Prefer surgical changes.
- Do not silently mock missing backend behavior unless asked.
- If you change frontend or backend contracts, update both sides or keep failures visible.
