const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export interface UploadResponse {
  id: number
  originalUrl: string
}

export interface ProcessResponse {
  id: number
  processedUrl: string
}

export async function uploadImage(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${API_BASE_URL}/api/images`, {
    method: 'POST',
    body: formData,
  })
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res))
  }
  return res.json()
}

export async function processImage(id: number): Promise<ProcessResponse> {
  const res = await fetch(`${API_BASE_URL}/api/images/${id}/process`, {
    method: 'POST',
  })
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res))
  }
  return res.json()
}

export async function deleteImage(id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/api/images/${id}`, {
    method: 'DELETE',
  })
  if (!res.ok && res.status !== 404) {
    throw new Error(await extractErrorMessage(res))
  }
}

export function originalImageUrl(id: number): string {
  return `${API_BASE_URL}/api/images/${id}/original`
}

export function processedImageUrl(id: number): string {
  return `${API_BASE_URL}/api/images/${id}/processed`
}

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const data = await res.json()
    return data.message ?? `Request failed with status ${res.status}`
  } catch {
    return `Request failed with status ${res.status}`
  }
}
