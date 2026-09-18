# Implementation Plan

Reference: `project-scope.md` (requirements/decisions), `tech-stack.md` (architecture).

## Phase 0 — Project scaffolding

- [ ] Init git repo at `E:\IMG`, add `.gitignore` (Java/Maven, Node, IDE, `.env`)
- [ ] Create `backend/` (Maven, Spring Boot starter: web, data-jpa, mysql driver) and `frontend/` (Vite + React + TypeScript) folder skeletons
- [ ] Verify both scaffolds build/run locally with placeholder content ("Hello World" endpoint / default Vite page)

## Phase 1 — Backend: data layer & upload

- [x] `ImageEntity` (id, originalFilename, contentType, originalBytes `LONGBLOB`, processedBytes `LONGBLOB` nullable, createdAt, updatedAt)
- [x] `ImageRepository` (Spring Data JPA)
- [x] Local MySQL for dev; `application.properties` datasource config via env vars (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`), with a gitignored `application-local.properties` for local values
- [x] `POST /api/images` — multipart upload endpoint: validate content-type (jpg/png/webp) and size (≤10MB), persist row, return `{id, originalUrl}`
- [x] `GET /api/images/{id}/original` — stream bytes with correct `Content-Type`
- [x] `DELETE /api/images/{id}` — remove row
- [x] Manual test with `curl`: upload, fetch original, fetch missing (404), delete, fetch after delete (404), upload with disallowed type (400) — all passed against local MySQL

## Phase 2 — Backend: background removal model

- [x] Add ONNX Runtime dependency (`com.microsoft.onnxruntime:onnxruntime` 1.30.0)
- [x] Download `model_quantized.onnx` (BRIA RMBG-1.4, 44.4MB) to `backend/src/main/resources/model/` and confirm it loads
- [x] `OnnxModelConfig` — load `OrtSession` once at startup as a singleton bean (from classpath bytes, no temp file)
- [x] `BackgroundRemovalService`:
  - [x] Preprocess: decode image, resize to 1024x1024 bilinear, normalize `(pixel/255 - 0.5)/1.0`, build NCHW input tensor
  - [x] Run inference
  - [x] Postprocess: min-max normalize mask, resize back to original dimensions, build ARGB image with alpha channel, encode PNG
- [x] Manual test (`BackgroundRemovalServiceManualTest`, env-var gated): ran against a sample product photo, verified via pixel inspection — background alpha=0, product pixel alpha=255 with original color preserved

## Phase 3 — Backend: wire processing into the API

- [x] `POST /api/images/{id}/process` — loads original, calls `BackgroundRemovalService`, stores/overwrites `processedBytes`, returns `{processedUrl}`
- [x] `GET /api/images/{id}/processed` — stream processed bytes (404 if not yet processed)
- [x] Confirm re-running `process` on the same id overwrites the previous result (supports "run again") — verified byte-identical re-run output
- [x] CORS config via `ALLOWED_ORIGIN` env var (`CorsConfig`, default `http://localhost:5173`) — verified via preflight request
- [x] Basic error handling: invalid file type/size → 4xx with message; missing id → 404 (process on missing id, get processed before processing)

## Phase 4 — Frontend: scaffolding & API client

- [x] `api.ts` — fetch wrappers: `uploadImage`, `processImage`, `deleteImage`, URL helpers for original/processed
- [x] `.env` (+ committed `.env.example`) with `VITE_API_BASE_URL=http://localhost:8080`
- [x] Basic app shell/layout, no styling yet — stripped Vite/React demo boilerplate, removed unused demo assets, minimal `index.css`/`App.css` reset
- [x] Verified: `tsc -b` + `npm run build` succeed, dev server serves the shell at `http://localhost:5173` (HTTP 200)

## Phase 5 — Frontend: core UI

- [ ] `UploadDropzone` — file picker + drag/drop, client-side type/size validation
- [ ] `ImageCompare` — original (left) vs processed (right), checkerboard background behind transparent result
- [ ] `ActionBar` — Remove Background / Run Again / Clear / Download PNG, correct enabled/disabled states per app state (`idle → uploaded → processing → done`)
- [ ] Wire state machine in `App.tsx`: upload → process → display → run again / clear
- [ ] "Download PNG" via anchor `download` attribute on processed image

## Phase 6 — Styling & polish

- [ ] Simple modern styling pass (layout, spacing, colors, loading/processing indicator)
- [ ] Empty/error states (upload failure, processing failure) surfaced in UI
- [ ] Responsive check at a reasonable minimum width

## Phase 7 — Local end-to-end verification

- [ ] Run backend + MySQL + frontend together locally
- [ ] Full manual pass: upload → remove background → compare → run again → clear → repeat with a second image → download PNG and confirm transparency in an image viewer

## Phase 8 — Deployment

- [x] `backend/Dockerfile` (multi-stage Maven build; downloads the ONNX model during build) + `.dockerignore`
- [x] `server.port=${PORT:8080}` / `server.address=0.0.0.0` added — Railway assigns `PORT` dynamically, app was hardcoded to 8080 before
- [x] Verified locally: `mvnw package` produces the exact jar the Dockerfile expects (`backend-0.0.1-SNAPSHOT.jar`), and running it standalone with `PORT=9090` set correctly bound Tomcat to 9090 (not 8080) — Docker itself isn't installed on this machine so the image build couldn't be tested end-to-end, but the two riskiest steps (packaging, port binding) are confirmed
- [ ] Railway: set the backend service's **Root Directory to `backend`** (required — repo is a monorepo, Railway only auto-detects `Dockerfile` at the root of the *service's* source directory), add MySQL plugin, set env vars (see below)
- [ ] Vercel: import `frontend/`, set **Root Directory to `frontend`**, set `VITE_API_BASE_URL` to the Railway backend's public URL
- [ ] Deployed smoke test: repeat the Phase 7 manual pass against the live URLs

Railway backend env vars to set (Variables tab, after adding the MySQL plugin to the same project):
```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
ALLOWED_ORIGIN=<your Vercel frontend URL, once known>
```

## Phase 9 — Documentation

- [ ] `README.md`: local dev setup, env vars, deploy steps for both services
