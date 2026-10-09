# Boxdrop

Boxdrop is a learning project similar to dropbox. It uses a React/Vite frontend and a Spring Boot backend. You can register, upload, download, delete, and share files. MongoDB stores metadata; MinIO stores files; ElasticMQ handles object-created notifications.

## Run locally

You need Docker with Compose and Node.js/npm. From the repository root, create a `.env` file for Docker Compose. Generate a local JWT secret with `openssl rand -base64 32` and paste its output into `APP_AUTH_JWT_SECRET`:

```dotenv
SPRING_MONGODB_URI=mongodb://mongodb:27017/dropbox_clone
APP_STORAGE_ENDPOINT=http://minio:9000
APP_STORAGE_PUBLIC_ENDPOINT=http://localhost:9000
APP_STORAGE_ACCESS_KEY=minioadmin
APP_STORAGE_SECRET_KEY=minioadmin
APP_STORAGE_BUCKET=dropbox-clone
APP_STORAGE_REGION=us-east-1
APP_AUTH_JWT_SECRET=<paste generated secret here>
APP_AUTH_JWT_TTL=3600
APP_AUTH_REFRESH_TTL_DAYS=30
APP_FRONTEND_ORIGIN=http://localhost:5173
APP_SQS_ENDPOINT=http://elasticmq:9324
APP_SQS_QUEUE_URL=http://elasticmq:9324/000000000000/s3-object-created
APP_SQS_ACCESS_KEY=x
APP_SQS_SECRET_KEY=x
```

Start the backend and its dependencies:

```bash
docker compose up --build
```

In a second terminal, start the frontend:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173 and register an account. The API is at http://localhost:8080/api; the MinIO console is at http://localhost:9001 (local credentials: `minioadmin` / `minioadmin`). To stop the stack, run `docker compose down` from the repository root.

The frontend defaults to `http://localhost:8080/api`; set `VITE_API_BASE_URL` in `frontend/.env` only if your API runs elsewhere. The values above are for local development only. See [the design document](design/design.md) for the longer-term system design.
