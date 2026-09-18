# Product Background Remover

A small personal tool to remove the background from product photos (plain/simple backgrounds) and export a transparent PNG, while keeping the original. See `project-scope.md` for requirements and `tech-stack.md` for the full architecture rationale.

## Architecture

```
[React/Vite SPA on Vercel] --HTTPS--> [Spring Boot API on Railway] --JDBC--> [MySQL on Railway]
                                              |
                                       ONNX Runtime (Java)
                                       BRIA RMBG-1.4 (quantized ONNX)
```

- **Backend**: Java 17 + Spring Boot 4.1, `backend/`
- **Frontend**: React + TypeScript + Vite, `frontend/`
- **Background removal**: self-hosted BRIA RMBG-1.4 model (quantized ONNX, ~44MB), run via ONNX Runtime's Java API — no external API calls, no Python
- **Storage**: MySQL, original + processed image bytes stored as `LONGBLOB`

## Prerequisites

- JDK 17+ (project was built against Zulu/Temurin 17)
- Maven is **not** required globally — the project uses the Maven Wrapper (`mvnw`/`mvnw.cmd`), which only needs `JAVA_HOME` pointing at a JDK 17+
- Node.js 18+ / npm
- A MySQL server for local development (or point at a remote one)

## Backend — local setup

1. Copy your local datasource credentials into `backend/src/main/resources/application-local.properties` (gitignored):
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/your_db_name
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```
2. Run with the `local` profile active:
   ```bash
   cd backend
   ./mvnw "-Dspring-boot.run.profiles=local" spring-boot:run
   ```
3. The API is served at `http://localhost:8080`.

The first run downloads/loads `com.microsoft.onnxruntime:onnxruntime` from Maven Central. The ONNX model file itself (`backend/src/main/resources/model/model_quantized.onnx`, ~44MB) is **not** committed to git — download it once with:
```bash
curl -L -o backend/src/main/resources/model/model_quantized.onnx \
  https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model_quantized.onnx
```
(The Docker build does this automatically for deployment — see below.)

### Running tests

```bash
cd backend
./mvnw test
```

23 tests, using an in-memory H2 database (MySQL-compatible mode) — no real MySQL or Docker needed to run them. The `BackgroundRemovalServiceTest` and `ImageApiEndToEndTest` use the real ONNX model, so they require the model file above to be present locally (they skip gracefully otherwise where possible).

## Frontend — local setup

```bash
cd frontend
npm install
npm run dev
```

Served at `http://localhost:5173`. Configure the backend URL via `frontend/.env`:
```
VITE_API_BASE_URL=http://localhost:8080
```
(`.env.example` documents this; `.env` itself is gitignored.)

## Environment variables

**Backend** (`backend/src/main/resources/application.properties`):

| Variable | Purpose | Local dev | Production (Railway) |
|---|---|---|---|
| `DB_URL` | JDBC URL | via `application-local.properties` | Railway MySQL plugin reference |
| `DB_USERNAME` | DB user | via `application-local.properties` | Railway MySQL plugin reference |
| `DB_PASSWORD` | DB password | via `application-local.properties` | Railway MySQL plugin reference |
| `PORT` | HTTP port | defaults to 8080 | injected by Railway |
| `ALLOWED_ORIGIN` | CORS allowed origin | defaults to `http://localhost:5173` | set to the Vercel frontend URL |

**Frontend** (`frontend/.env`):

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Base URL of the backend API |

## API reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/images` | Upload an image (multipart, field `file`; jpg/png/webp, ≤10MB). Returns `{id, originalUrl}` |
| `GET` | `/api/images/{id}/original` | Stream the original image bytes |
| `POST` | `/api/images/{id}/process` | Run (or re-run) background removal. Returns `{id, processedUrl}` |
| `GET` | `/api/images/{id}/processed` | Stream the processed PNG (404 if not processed yet) |
| `DELETE` | `/api/images/{id}` | Delete the stored image pair |

## Deployment

### Backend → Railway

1. Create a service from this repo, **set Root Directory to `backend`** (required — this is a monorepo, and Railway only auto-detects the `Dockerfile` at the root of the service's source directory)
2. Add Railway's MySQL plugin to the same project
3. Set service variables:
   ```
   DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   ALLOWED_ORIGIN=<your Vercel frontend URL, e.g. https://your-app.vercel.app>
   ```
4. Under Networking → Generate Service Domain, enter port `8080`
5. `backend/Dockerfile` handles the rest: multi-stage Maven build, downloads the ONNX model during the build, packages a slim JRE runtime image

### Frontend → Vercel

1. Import this repo, **set Root Directory to `frontend`**
2. Framework preset: Vite
3. Set env var `VITE_API_BASE_URL` to the Railway backend's public domain
4. Redeploy after setting/changing env vars — Vite bakes them in at build time, so a redeploy is required for changes to take effect

### Gotchas hit during setup (kept here so they don't repeat)

- Railway builds from the **repo root** unless Root Directory is set per-service — in a monorepo this means Railway can't find `pom.xml`/`Dockerfile` unless you point it at `backend/`
- Railway assigns a dynamic port via the `PORT` env var — the app must bind to it (`server.port=${PORT:8080}`), not a hardcoded port
- Vercel's per-deployment preview URL (e.g. `https://app-<hash>-<team>.vercel.app`) is **not** the same origin as the stable production URL (`https://app.vercel.app`) — CORS's `ALLOWED_ORIGIN` must match exactly what the browser sends, so use the stable production URL

## Project structure

```
backend/     Spring Boot API (Java 17, Maven)
frontend/    React + Vite SPA
project-scope.md          requirements & decisions
tech-stack.md             architecture rationale
implementation-plan.md    phase-by-phase build log
```
