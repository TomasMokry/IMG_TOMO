import { useRef, useState, type ChangeEvent, type DragEvent } from 'react'

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE_BYTES = 10 * 1024 * 1024

interface Props {
  disabled?: boolean
  onFileSelected: (file: File) => void
  onError: (message: string) => void
}

export function UploadDropzone({ disabled, onFileSelected, onError }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [isDragging, setIsDragging] = useState(false)

  function validateAndEmit(file: File | undefined) {
    if (!file) return
    if (!ALLOWED_TYPES.includes(file.type)) {
      onError(`Unsupported file type: ${file.type || 'unknown'}. Allowed: JPG, PNG, WebP`)
      return
    }
    if (file.size > MAX_SIZE_BYTES) {
      onError('File exceeds the 10MB size limit')
      return
    }
    onFileSelected(file)
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(false)
    if (disabled) return
    validateAndEmit(e.dataTransfer.files[0])
  }

  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    validateAndEmit(e.target.files?.[0])
    e.target.value = ''
  }

  return (
    <div
      className={`dropzone${isDragging ? ' dropzone--dragging' : ''}${disabled ? ' dropzone--disabled' : ''}`}
      onClick={() => !disabled && inputRef.current?.click()}
      onKeyDown={(e) => {
        if (!disabled && (e.key === 'Enter' || e.key === ' ')) inputRef.current?.click()
      }}
      onDragOver={(e) => {
        e.preventDefault()
        if (!disabled) setIsDragging(true)
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      role="button"
      tabIndex={0}
      aria-disabled={disabled}
    >
      <input
        ref={inputRef}
        type="file"
        accept={ALLOWED_TYPES.join(',')}
        hidden
        onChange={handleChange}
        disabled={disabled}
      />
      <p className="dropzone__title">Drag &amp; drop a product photo here</p>
      <p className="dropzone__subtitle">or click to browse — JPG, PNG or WebP, up to 10MB</p>
    </div>
  )
}
