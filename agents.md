# Agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth and health endpoints.
- `design/design.md`: High-level product and system-design target for future iterations; read it before making architectural changes.
- `docker-compose.yml`: Local stack wiring for app services and infrastructure.

## Current implementation status

- Auth is working end-to-end in the UI against the backend JWT endpoints, including access-token refresh via an httpOnly refresh-token cookie.
- The protected file area is wired up in the frontend for listing, uploading, downloading, and deleting files.
- The backend exposes `/api/files` endpoints for listing, presigned upload URLs, upload completion, downloads, and deletes.
- The backend file flow uses presigned upload URLs and a CDN-backed download link instead of a direct upload/download passthrough.
- The backend also exposes `/api/user` for fetching the current authenticated user's profile (id, email); the frontend calls this on load via `frontend/src/api/userApi.ts`.
- The design doc still reflects the broader system-design target; the implementation is now closer to that shape than before.

## Frontend conventions

- API base URL comes from `VITE_API_BASE_URL` and defaults to `http://localhost:8080/api`.
- Auth state (`accessToken` plus `tokenType`) is persisted in local storage via `frontend/src/auth/authStorage.ts`; protected API calls send `Authorization: <tokenType> <accessToken>`.
- The refresh token itself is never held in JS/localStorage — it lives in an httpOnly, secure cookie set by the backend (`/api/auth/login`, `/register`, `/refresh`) and is only usable via `/api/auth/refresh` and `/api/auth/logout`.
- `frontend/src/api/httpClient.ts` centralizes authorized requests: on a `401`, it transparently calls `/api/auth/refresh` (de-duped via an in-flight promise), retries the original request once with the new access token, and clears auth state + calls `onUnauthorized` if refresh fails.
- Match existing React patterns in `App.tsx`, `AuthenticatedApp.tsx`, and `frontend/src/auth/*`: local component state, explicit async error handling, and simple presentational CSS in `App.css`.
- The authenticated shell currently owns file actions directly; keep API errors visible rather than silently mocking missing backend behavior.

## Backend conventions

- API routes are namespaced under `/api`.
- Security is stateless JWT auth (`SecurityConfig`); only `/error`, `/actuator/health`, `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout` are public — everything else (including `/api/user` and `/api/files/**`) requires a valid bearer access token.
- Refresh tokens are managed server-side (`RefreshTokenService`, `RefreshTokenRepository`, `RefreshToken` entity) and delivered to the client only as an httpOnly `refresh_token` cookie scoped to `/api/auth`; `AuthController` handles register/login/refresh/logout and cookie lifecycle.
- Keep file and user endpoints aligned with the protected frontend calls and the longer-term design in `design/design.md`.

## Working guidance for future agents

- Read the design doc before making architectural changes; this repo is a learning project and intentionally incomplete.
- Prefer surgical changes over broad scaffolding rewrites.
- Do not silently mock around missing backend behavior unless the user explicitly asks for it.
- If you change frontend or backend contracts, update both sides or clearly preserve failure visibility.
