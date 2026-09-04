# Agent onboarding

## Project shape

- `frontend/`: React + Vite UI for auth, file browsing, and upload flows.
- `backend/`: Spring Boot API with JWT auth and health endpoints.
- `design/design.md`: High-level product and system-design target for future iterations.
- `docker-compose.yml`: Local stack wiring for app services and infrastructure.

## Current implementation status

- Auth is working end-to-end in the UI against the backend JWT endpoints.
- The frontend file area is still work in progress.
- The frontend now sends authenticated `GET /api/files` and `POST /api/files` requests.
- The backend does **not** implement file endpoints yet, so frontend file actions may surface backend errors until that lands.
- The design doc points toward a future presigned-upload flow, but the current frontend integration is a simpler direct backend call for now.

## Frontend conventions

- API base URL comes from `VITE_API_BASE_URL` and defaults to `http://localhost:8080/api`.
- Auth state is stored in local storage and includes `accessToken` plus `tokenType`; protected API calls should send `Authorization: <tokenType> <accessToken>`.
- Match existing React patterns in `App.tsx`, `AuthenticatedApp.tsx`, and `frontend/src/auth/*`: local component state, explicit async error handling, and simple presentational CSS in `App.css`.

## Backend conventions

- API routes are namespaced under `/api`.
- Security is stateless JWT auth; only health and auth endpoints are currently public.
- If you add file endpoints later, keep them aligned with the protected frontend calls and the longer-term design in `design/design.md`.

## Working guidance for future agents

- Read the design doc before making architectural changes; this repo is a learning project and intentionally incomplete.
- Prefer surgical changes over broad scaffolding rewrites.
- Do not silently mock around missing backend behavior unless the user explicitly asks for it.
- If you change frontend or backend contracts, update both sides or clearly preserve failure visibility.
