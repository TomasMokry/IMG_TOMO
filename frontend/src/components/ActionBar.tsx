interface Props {
  hasImage: boolean
  hasProcessed: boolean
  isBusy: boolean
  onProcess: () => void
  onClear: () => void
  downloadUrl: string | null
}

export function ActionBar({
  hasImage,
  hasProcessed,
  isBusy,
  onProcess,
  onClear,
  downloadUrl,
}: Props) {
  return (
    <div className="action-bar">
      <button type="button" className="button" onClick={onProcess} disabled={!hasImage || isBusy}>
        {isBusy ? 'Processing…' : hasProcessed ? 'Run Again' : 'Remove Background'}
      </button>
      <a
        className={`button button--secondary${!downloadUrl ? ' button--disabled' : ''}`}
        href={downloadUrl ?? undefined}
        download="product-no-bg.png"
        aria-disabled={!downloadUrl}
        onClick={(e) => {
          if (!downloadUrl) e.preventDefault()
        }}
      >
        Download PNG
      </a>
      <button
        type="button"
        className="button button--ghost"
        onClick={onClear}
        disabled={!hasImage || isBusy}
      >
        Clear
      </button>
    </div>
  )
}
