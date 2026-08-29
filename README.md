# Dropbox Clone

Initial scaffolding for a Dropbox-clone learning project built with Spring Boot, React, MongoDB, MinIO, and Docker Compose.

## Services
- `backend`: Spring Boot API
- `frontend`: React + Vite application
- `mongodb`: metadata store
- `minio`: S3-compatible object storage

## Local development
### Run with Docker Compose
```bash
docker compose up --build
```

### Run services individually
```bash
cd backend
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

## Environment
Copy the example files if you want to override defaults:

- `backend/.env.example`
- `frontend/.env.example`

## Initial scope
- Upload and download flows
- Multipart upload groundwork
- Sharing groundwork
- MongoDB metadata storage
- MinIO object storage integration points
