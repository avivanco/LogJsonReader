import { useQuery } from '@tanstack/react-query'
import { useRef, useState, type DragEvent } from 'react'
import { api } from '../../api/client'
import type { SessionView } from '../../api/types'
import { formatBytes } from '../../lib/format'

interface Props {
  onOpened: (session: SessionView) => void
}

/** Landing view: drop a file, pick one, or (when the server allows it) open a file from the server's log folder. */
export function UploadPanel({ onOpened }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const config = useQuery({ queryKey: ['config'], queryFn: api.config, staleTime: Infinity })
  const files = useQuery({
    queryKey: ['files'],
    queryFn: api.files,
    enabled: config.data?.pathSourceEnabled === true,
  })

  const busy = progress !== null

  async function upload(file: File) {
    setError(null)
    setProgress(0)
    try {
      onOpened(await api.uploadFile(file, setProgress))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Upload failed')
      setProgress(null)
    }
  }

  async function openServerFile(path: string) {
    setError(null)
    try {
      onOpened(await api.openPath(path))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not open the file')
    }
  }

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    const file = event.dataTransfer.files[0]
    if (file && !busy) void upload(file)
  }

  return (
    <main className="mx-auto flex h-full max-w-3xl flex-col justify-center gap-8 px-6 py-10">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">LogJsonReader</h1>
        <p className="mt-2 text-ink-2">
          Open a JSON log file (one JSON object per line) and explore it: filter by severity, time range, logger, user
          or any field, follow a trace, and share the view with a link.
        </p>
      </header>

      <div
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`flex flex-col items-center gap-3 rounded-xl border-2 border-dashed px-6 py-14 text-center transition-colors ${
          dragging ? 'border-accent bg-selected' : 'border-axis bg-surface'
        }`}
      >
        {busy ? (
          <div className="w-full max-w-sm" role="status" aria-live="polite">
            <p className="mb-2 text-sm text-ink-2">Uploading… {Math.round((progress ?? 0) * 100)}%</p>
            <div className="h-2 overflow-hidden rounded-full bg-line">
              <div className="h-full bg-accent transition-[width]" style={{ width: `${(progress ?? 0) * 100}%` }} />
            </div>
          </div>
        ) : (
          <>
            <p className="text-lg font-medium">Drop a log file here</p>
            <p className="text-sm text-muted">.log, .json, .jsonl, .ndjson — large files are fine</p>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="mt-2 rounded-lg bg-accent px-4 py-2 font-medium text-white hover:opacity-90"
            >
              Choose file
            </button>
            <input
              ref={inputRef}
              type="file"
              className="hidden"
              aria-label="Log file"
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) void upload(file)
                event.target.value = ''
              }}
            />
          </>
        )}
      </div>

      {error && (
        <p role="alert" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm">
          <span className="font-medium" style={{ color: 'var(--lvl-error)' }}>
            Error:
          </span>{' '}
          {error}
        </p>
      )}

      {config.data?.pathSourceEnabled && (
        <section aria-labelledby="server-files">
          <h2 id="server-files" className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink-2">
            Files on the server
          </h2>
          {files.isLoading && <p className="text-sm text-muted">Loading…</p>}
          {files.data?.length === 0 && <p className="text-sm text-muted">No log files found in the allowed folders.</p>}
          <ul className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
            {files.data?.map((file) => (
              <li key={file.path} className="flex items-center justify-between gap-4 px-4 py-2">
                <div className="min-w-0">
                  <p className="truncate font-mono text-sm" title={file.path}>
                    {file.path}
                  </p>
                  <p className="text-xs text-muted">
                    {formatBytes(file.size)} · {new Date(file.modified).toLocaleString()}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void openServerFile(file.path)}
                  className="shrink-0 rounded-md border border-line px-3 py-1 text-sm hover:bg-hover"
                >
                  Open
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}
