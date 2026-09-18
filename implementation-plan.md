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

- [ ] `POST /api/images/{id}/process` — loads original, calls `BackgroundRemovalService`, stores/overwrites `processedBytes`, returns `{processedUrl}`
- [ ] `GET /api/images/{id}/processed` — stream processed bytes (404 if not yet processed)
- [ ] Confirm re-running `process` on the same id overwrites the previous result (supports "run again")
- [ ] CORS config via `ALLOWED_ORIGIN` env var
- [ ] Basic error handling: invalid file type/size → 4xx with message; missing id → 404

## Phase 4 — Frontend: scaffolding & API client

- [ ] `api.ts` — fetch wrappers: `uploadImage`, `processImage`, `deleteImage`, URL helpers for original/processed
- [ ] `.env` with `VITE_API_BASE_URL`
- [ ] Basic app shell/layout, no styling yet

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

- [ ] `backend/Dockerfile` (multi-stage Maven build; download the ONNX model during build)
- [ ] Railway: create backend service from Dockerfile, add MySQL plugin, set datasource env vars + `ALLOWED_ORIGIN`
- [ ] Vercel: import `frontend/`, set `VITE_API_BASE_URL` to the Railway backend's public URL
- [ ] Deployed smoke test: repeat the Phase 7 manual pass against the live URLs

## Phase 9 — Documentation

- [ ] `README.md`: local dev setup, env vars, deploy steps for both services
