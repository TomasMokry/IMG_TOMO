## Problem

I have images of product with simple colour backgroud. I need to be able load the image into app and be able to remove the simple background and save the image in png format. And keep the original saved as well.

## Solution

Build a simple app that use AI to auto select the bacground of the product image. Should have simple modern UI.

## Features

- simple modern UI where i can load the image
- button for removing the product image background
- see the original and updated image next to each other to be able compare the result.
- Be able to run the backround removal again if not happy with the result
- Need to use vercel and railway for deployment since I have subscription there.
- Buttons to clear the images so I can start again with new image

## Decisions

- Background removal runs self-hosted (no per-call API costs), not via a third-party API.
- No user accounts/login — single-user personal tool.
- Original and processed images are persisted in MySQL (as blobs), not just kept in browser memory. Persistence is for reliability only — no history/gallery view, the UI only ever shows the current image pair.
- "Clear" resets the UI and deletes the stored image pair, so no history accumulates.
- Uploads: common formats (JPG/PNG/WebP), capped around 10MB. No forced resizing of the stored original (only resized internally for model input).
- Model choice prioritizes small footprint over maximum quality ("reasonable quality" is enough) — see tech-stack.md.

See `tech-stack.md` for the concrete technology choices and architecture.
