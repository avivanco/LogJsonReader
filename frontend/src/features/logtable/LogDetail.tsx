import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { LogEntry } from '../../api/types'
import { LevelBadge } from '../../components/LevelBadge'
import type { FilterState } from '../../lib/filters'
import { useEntry } from '../../hooks/useLogData'

interface Props {
  sessionId: string
  seq: number
  onClose: () => void
  onFilter: (updater: (previous: FilterState) => FilterState) => void
}

/** Side drawer with everything about one entry. All log content is rendered as text, never as HTML. */
export function LogDetail({ sessionId, seq, onClose, onFilter }: Props) {
  const entry = useEntry(sessionId, seq)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <aside
      aria-label={`Entry ${seq}`}
      className="fixed inset-y-0 right-0 z-30 flex w-[min(680px,100vw)] flex-col border-l border-line bg-surface shadow-2xl"
    >
      <header className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          {entry.data && <LevelBadge level={entry.data.level} />}
          <h2 className="truncate text-sm font-semibold">Line {seq}</h2>
        </div>
        <div className="flex items-center gap-2">
          <CopyButton label="Copy link" text={window.location.href} />
          <button
            type="button"
            onClick={onClose}
            aria-label="Close details"
            className="rounded-md border border-line px-2.5 py-1 text-sm hover:bg-hover"
          >
            Close (Esc)
          </button>
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-auto px-4 py-3">
        {entry.isLoading && <p className="text-sm text-muted">Loading…</p>}
        {entry.isError && <p className="text-sm">Could not load this entry.</p>}
        {entry.data && <EntryBody entry={entry.data} onFilter={onFilter} />}
      </div>
    </aside>
  )
}

function EntryBody({ entry, onFilter }: { entry: LogEntry; onFilter: Props['onFilter'] }) {
  const pretty = useMemo(() => prettyJson(entry.raw), [entry.raw])
  const attributes = Object.entries(entry.attributes)

  return (
    <div className="flex flex-col gap-5">
      <dl className="grid grid-cols-[110px_1fr] gap-x-3 gap-y-2 text-sm">
        <Field label="Timestamp">
          <span className="font-mono text-xs break-all">{entry.timestamp}</span>
        </Field>
        <Field
          label="Logger"
          value={entry.logger}
          action={entry.logger ? { label: 'Only this logger', run: () => onFilter((f) => ({ ...f, loggers: [entry.logger as string] })) } : undefined}
        />
        <Field
          label="Thread"
          value={entry.thread}
          action={entry.thread ? { label: 'Only this thread', run: () => onFilter((f) => ({ ...f, threads: [entry.thread as string] })) } : undefined}
        />
        <Field
          label="Trace ID"
          value={entry.traceId}
          mono
          action={entry.traceId ? { label: 'Show whole trace', run: () => onFilter((f) => ({ ...f, traceId: entry.traceId as string })) } : undefined}
        />
        <Field label="Span ID" value={entry.spanId} mono />
        {attributes.map(([name, value]) => (
          <Field
            key={name}
            label={name}
            value={value}
            action={{ label: `Only ${name} = ${value}`, run: () => onFilter((f) => ({ ...f, attrs: { ...f.attrs, [name]: value } })) }}
          />
        ))}
      </dl>

      <section>
        <h3 className="mb-1 text-xs font-semibold tracking-wide text-ink-2 uppercase">Message</h3>
        <p className="rounded-md border border-line bg-page p-3 font-mono text-xs break-words whitespace-pre-wrap">
          {entry.message}
        </p>
      </section>

      {entry.stackTrace && (
        <details open className="rounded-md border border-line">
          <summary className="cursor-pointer px-3 py-2 text-xs font-semibold tracking-wide text-ink-2 uppercase">
            Stack trace
          </summary>
          <pre className="max-h-96 overflow-auto border-t border-line bg-page p-3 font-mono text-xs">{entry.stackTrace}</pre>
        </details>
      )}

      <section>
        <div className="mb-1 flex items-center justify-between">
          <h3 className="text-xs font-semibold tracking-wide text-ink-2 uppercase">Raw JSON</h3>
          <CopyButton label="Copy" text={pretty} />
        </div>
        <pre className="max-h-96 overflow-auto rounded-md border border-line bg-page p-3 font-mono text-xs">{pretty}</pre>
      </section>
    </div>
  )
}

interface FieldProps {
  label: string
  value?: string | null
  mono?: boolean
  action?: { label: string; run: () => void }
  children?: ReactNode
}

function Field({ label, value, mono, action, children }: FieldProps) {
  if (!children && !value) return null
  return (
    <>
      <dt className="truncate text-ink-2" title={label}>
        {label}
      </dt>
      <dd className="flex items-start justify-between gap-2">
        {children ?? <span className={`break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</span>}
        {action && (
          <button
            type="button"
            onClick={action.run}
            title={action.label}
            aria-label={action.label}
            className="shrink-0 rounded border border-line px-1.5 py-0.5 text-xs hover:bg-hover"
          >
            Filter
          </button>
        )}
      </dd>
    </>
  )
}

function CopyButton({ label, text }: { label: string; text: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        })
      }}
      className="rounded-md border border-line px-2.5 py-1 text-xs hover:bg-hover"
    >
      {copied ? 'Copied' : label}
    </button>
  )
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}
