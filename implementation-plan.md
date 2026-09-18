# Implementation Plan

Reference: `project-scope.md` (requirements/decisions), `tech-stack.md` (architecture).

## Phase 0 — Project scaffolding
- [x] Init git repo at `E:\IMG`, add `.gitignore` (Java/Maven, Node, IDE, `.env`)
- [x] Create `backend/` (Maven, Spring Boot 4.1.1 starter: web, data-jpa, mysql driver) and `frontend/` (Vite + React + TypeScript) folder skeletons
- [x] Verify both scaffolds build/run locally with placeholder content ("Hello World" endpoint / default Vite page)

## Phase 1 — Backend: data layer & upload
- [ ] `ImageEntity` (id, originalFilename, contentType, originalBytes `LONGBLOB`, processedBytes `LONGBLOB` nullable, createdAt, updatedAt)
- [ ] `ImageRepository` (Spring Data JPA)
- [ ] Local MySQL (or Docker `mysql` container) for dev; `application.yml` datasource config via env vars
- [ ] `POST /api/images` — multipart upload endpoint: validate content-type (jpg/png/webp) and size (≤10MB), persist row, return `{id, originalUrl}`
- [ ] `GET /api/images/{id}/original` — stream bytes with correct `Content-Type`
- [ ] `DELETE /api/images/{id}` — remove row
- [ ] Manual test with `curl`/Postman: upload, fetch original, delete

## Phase 2 — Backend: background removal model
- [ ] Add ONNX Runtime dependency (`com.microsoft.onnxruntime:onnxruntime`)
- [ ] Download `model_quantized.onnx` (BRIA RMBG-1.4) and confirm it loads via a small standalone test/main method
- [ ] `OnnxModelConfig` — load `OrtSession` once at startup as a singleton bean
- [ ] `BackgroundRemovalService`:
  - [ ] Preprocess: decode image, resize to 1024x1024 bilinear, normalize, build input tensor
  - [ ] Run inference
  - [ ] Postprocess: resize mask back to original dimensions, build ARGB image with alpha channel, encode PNG
- [ ] Unit/manual test: run the service against a sample product photo saved to disk, visually confirm transparent background

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
