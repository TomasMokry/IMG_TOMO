interface Props {
  originalUrl: string
  processedUrl: string | null
  processing: boolean
}

export function ImageCompare({ originalUrl, processedUrl, processing }: Props) {
  return (
    <div className="compare">
      <figure className="compare__panel">
        <figcaption>Original</figcaption>
        <div className="compare__frame">
          <img src={originalUrl} alt="Original product" />
        </div>
      </figure>
      <figure className="compare__panel">
        <figcaption>Background removed</figcaption>
        <div className="compare__frame compare__frame--checkerboard">
          {processing && <div className="compare__spinner" aria-label="Processing" />}
          {!processing && processedUrl && (
            <img src={processedUrl} alt="Product with background removed" />
          )}
          {!processing && !processedUrl && (
            <p className="compare__placeholder">Not processed yet</p>
          )}
        </div>
      </figure>
    </div>
  )
}
