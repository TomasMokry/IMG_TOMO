# Tech Stack

## Overview

```
[React/Vite SPA on Vercel] --HTTPS--> [Spring Boot API on Railway] --JDBC--> [MySQL on Railway]
                                              |
                                       ONNX Runtime (Java)
                                       model_quantized.onnx
```

## Background removal model

- **Model**: BRIA `RMBG-1.4` (IS-Net based), from `briaai/RMBG-1.4` on Hugging Face.
- **Variant**: quantized ONNX export, `model_quantized.onnx` (~44MB) — chosen over the full-precision (176MB) and fp16 (88MB) exports to keep the deployed footprint as small as possible while keeping quality reasonable for product photos on plain backgrounds.
- **Input**: 1024x1024, bilinear resize, normalized `(pixel/255 - 0.5) / 1.0`.
- **Output**: single-channel mask, resized back to the original image's dimensions and used as the alpha channel over the original RGB to produce a transparent PNG.
- **License note**: RMBG-1.4 is licensed for non-commercial/personal use. Fine for this personal tool; would need a different license/model if ever repurposed commercially.

## Backend

- **Language/framework**: Java + Spring Boot.
- **Inference runtime**: ONNX Runtime Java API (`com.microsoft.onnxruntime:onnxruntime`) — no Python dependency. Model session loaded once at startup as a singleton.
- **Persistence**: MySQL (Railway plugin), via Spring Data JPA. Single table storing original and processed image bytes as `LONGBLOB`, plus filename/content-type/timestamps.
- **Deployment**: Docker image on Railway. The Dockerfile downloads `model_quantized.onnx` from Hugging Face at build time and bakes it into the image (keeps the git repo free of large binaries).
- **API surface**:
  - `POST /api/images` — upload original, returns id + URL
  - `POST /api/images/{id}/process` — run/re-run background removal, returns processed URL
  - `GET /api/images/{id}/original` / `GET /api/images/{id}/processed` — stream bytes
  - `DELETE /api/images/{id}` — clear/reset

## Frontend

- **Framework**: React + Vite (plain SPA, no Next.js/meta-framework needed since all processing happens server-side).
- **Deployment**: Vercel, static build output.
- **Key UI pieces**: upload/dropzone, side-by-side original vs. processed comparison (checkerboard behind the transparent result), action bar (Remove Background / Run Again / Clear / Download PNG).
- No auth, no state management library — local component state is sufficient for this scope.

## Deployment summary

- **Vercel**: hosts the frontend; env var `VITE_API_BASE_URL` points at the Railway backend.
- **Railway**: hosts the Spring Boot backend (Docker) and the MySQL plugin; env var `ALLOWED_ORIGIN` set to the Vercel URL for CORS.
