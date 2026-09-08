# Agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth and health endpoints.
- `design/design.md`: High-level product and system-design target for future iterations; read it before making architectural changes.
- `docker-compose.yml`: Local stack wiring for app services and infrastructure.

## Current implementation status

- Auth is working end-to-end in the UI against the backend JWT endpoints.
- The protected file area is now wired up in the frontend for listing, uploading, downloading, and deleting files.
- The backend already exposes `/api/files` endpoints for listing, presigned upload URLs, upload completion, downloads, and deletes.
- The backend file flow uses presigned upload URLs and a CDN-backed download link instead of a direct upload/download passthrough.
- The design doc still reflects the broader system-design target; the implementation is now closer to that shape than before.

## Frontend conventions

- API base URL comes from `VITE_API_BASE_URL` and defaults to `http://localhost:8080/api`.
- Auth state is stored in local storage and includes `accessToken` plus `tokenType`; protected API calls should send `Authorization: <tokenType> <accessToken>`.
- Match existing React patterns in `App.tsx`, `AuthenticatedApp.tsx`, and `frontend/src/auth/*`: local component state, explicit async error handling, and simple presentational CSS in `App.css`.
- The authenticated shell currently owns file actions directly; keep API errors visible rather than silently mocking missing backend behavior.

## Backend conventions

- API routes are namespaced under `/api`.
- Security is stateless JWT auth; only health and auth endpoints are public.
- Keep file endpoints aligned with the protected frontend calls and the longer-term design in `design/design.md`.

## Working guidance for future agents

- Read the design doc before making architectural changes; this repo is a learning project and intentionally incomplete.
- Prefer surgical changes over broad scaffolding rewrites.
- Do not silently mock around missing backend behavior unless the user explicitly asks for it.
- If you change frontend or backend contracts, update both sides or clearly preserve failure visibility.
