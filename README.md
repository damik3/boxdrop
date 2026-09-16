# Dropbox Clone

Initial scaffolding for a Dropbox-clone learning project built with Spring Boot, React, MongoDB, MinIO, and Docker Compose.

## Services
- `backend`: Spring Boot API
- `frontend`: React + Vite application
- `mongodb`: metadata store
- `minio`: S3-compatible object storage
- `elasticmq`: SQS-compatible queue for object-created events
- `sqs-bridge`: MinIO webhook → ElasticMQ (MinIO has no native SQS target)
- `minio-init`: creates the app bucket and registers PUT notifications

PUT to the bucket is forwarded as an S3-style event JSON message onto queue `s3-object-created` (`http://localhost:9324/000000000000/s3-object-created` from the host).

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
