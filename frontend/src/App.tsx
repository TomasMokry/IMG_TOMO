import { useRef, useState } from 'react'
import './App.css'
import { UploadDropzone } from './components/UploadDropzone'
import { ImageCompare } from './components/ImageCompare'
import { ActionBar } from './components/ActionBar'
import { deleteImage, processImage, processedImageUrl, uploadImage } from './api'

type Status = 'idle' | 'uploading' | 'ready' | 'processing'

function App() {
  const [status, setStatus] = useState<Status>('idle')
  const [imageId, setImageId] = useState<number | null>(null)
  const [originalUrl, setOriginalUrl] = useState<string | null>(null)
  const [processedUrl, setProcessedUrl] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const objectUrlsRef = useRef<string[]>([])

  function trackObjectUrl(url: string) {
    objectUrlsRef.current.push(url)
    return url
  }

  function revokeTrackedUrls() {
    objectUrlsRef.current.forEach((url) => URL.revokeObjectURL(url))
    objectUrlsRef.current = []
  }

  async function handleFileSelected(file: File) {
    setErrorMessage(null)
    setStatus('uploading')
    try {
      const { id } = await uploadImage(file)
      setImageId(id)
      setOriginalUrl(trackObjectUrl(URL.createObjectURL(file)))
      setProcessedUrl(null)
      setStatus('ready')
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Upload failed')
      setStatus('idle')
    }
  }

  async function handleProcess() {
    if (imageId === null) return
    setErrorMessage(null)
    setStatus('processing')
    try {
      await processImage(imageId)
      const res = await fetch(processedImageUrl(imageId))
      if (!res.ok) throw new Error('Failed to load the processed image')
      const blob = await res.blob()
      setProcessedUrl(trackObjectUrl(URL.createObjectURL(blob)))
      setStatus('ready')
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Background removal failed')
      setStatus('ready')
    }
  }

  async function handleClear() {
    const idToDelete = imageId
    revokeTrackedUrls()
    setImageId(null)
    setOriginalUrl(null)
    setProcessedUrl(null)
    setErrorMessage(null)
    setStatus('idle')
    if (idToDelete !== null) {
      try {
        await deleteImage(idToDelete)
      } catch {
        // Deletion is best-effort cleanup; the UI has already reset regardless.
      }
    }
  }

  return (
    <div className="app">
      <header className="app__header">
        <h1>Product Background Remover</h1>
        <p>Upload a product photo, remove its background, and download a transparent PNG.</p>
      </header>

      {errorMessage && (
        <div className="banner banner--error" role="alert">
          {errorMessage}
        </div>
      )}

      {!originalUrl && (
        <UploadDropzone
          disabled={status === 'uploading'}
          onFileSelected={handleFileSelected}
          onError={setErrorMessage}
        />
      )}

      {originalUrl && (
        <>
          <ImageCompare
            originalUrl={originalUrl}
            processedUrl={processedUrl}
            processing={status === 'processing'}
          />
          <ActionBar
            hasImage
            hasProcessed={processedUrl !== null}
            isBusy={status === 'processing' || status === 'uploading'}
            onProcess={handleProcess}
            onClear={handleClear}
            downloadUrl={processedUrl}
          />
        </>
      )}
    </div>
  )
}

export default App
