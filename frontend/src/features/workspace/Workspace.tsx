import { useCallback, useMemo, useState } from 'react'
import { api } from '../../api/client'
import { ApiError } from '../../api/client'
import type { SessionView } from '../../api/types'
import { filterKey, type FilterState } from '../../lib/filters'
import { formatBytes, formatCount } from '../../lib/format'
import { useFacets, useInfiniteLogs } from '../../hooks/useLogData'
import { useSession } from '../../hooks/useSession'
import type { Theme } from '../../hooks/usePreferences'
import type { TimeZoneMode } from '../../lib/time'
import { FilterBar } from '../filters/FilterBar'
import { LogDetail } from '../logtable/LogDetail'
import { ATTRIBUTE_COLUMN_PREFIX, columnLabel } from '../logtable/columns'
import { LogTable } from '../logtable/LogTable'
import { Timeline } from '../timeline/Timeline'

interface Props {
  sessionId: string
  filter: FilterState
  entry: number | null
  tz: TimeZoneMode
  theme: Theme
  onFilterChange: (updater: (previous: FilterState) => FilterState) => void
  onSelectEntry: (seq: number | null) => void
  onToggleTz: () => void
  onToggleTheme: () => void
  onOpenAnother: () => void
}

export function Workspace({
  sessionId,
  filter,
  entry,
  tz,
  theme,
  onFilterChange,
  onSelectEntry,
  onToggleTz,
  onToggleTheme,
  onOpenAnother,
}: Props) {
  const session = useSession(sessionId)
  const status = session.data?.status
  const facets = useFacets(sessionId, filter, status)
  const logs = useInfiniteLogs(sessionId, filter, status)
  const [columns, setColumns] = useState<string[]>(['logger'])
  const [wrap, setWrap] = useState(false)

  const rows = useMemo(() => logs.data?.pages.flatMap((page) => page.items) ?? [], [logs.data])
  const { fetchNextPage } = logs
  const loadMore = useCallback(() => void fetchNextPage(), [fetchNextPage])
  const closeEntry = useCallback(() => onSelectEntry(null), [onSelectEntry])

  if (session.isError) {
    const gone = session.error instanceof ApiError && session.error.status === 404
    return (
      <main className="mx-auto flex h-full max-w-lg flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">{gone ? 'This session is no longer available' : 'Could not load the session'}</h1>
        <p className="text-ink-2">
          {gone
            ? 'Sessions expire after a period of inactivity and are lost when the server restarts. Open the file again to continue.'
            : (session.error as Error).message}
        </p>
        <button type="button" onClick={onOpenAnother} className="rounded-lg bg-accent px-4 py-2 font-medium text-white">
          Open a file
        </button>
      </main>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <Header
        session={session.data}
        filter={filter}
        sessionId={sessionId}
        tz={tz}
        theme={theme}
        onToggleTz={onToggleTz}
        onToggleTheme={onToggleTheme}
        onOpenAnother={onOpenAnother}
        onShowUnparsed={() => onFilterChange((previous) => ({ ...previous, levels: ['UNKNOWN'] }))}
      />

      <FilterBar filter={filter} facets={facets.data} tz={tz} onChange={onFilterChange} />

      {facets.data?.histogram && (
        <Timeline
          histogram={facets.data.histogram}
          from={filter.from}
          to={filter.to}
          tz={tz}
          onRangeChange={(from, to) => onFilterChange((previous) => ({ ...previous, from, to }))}
        />
      )}

      <div className="flex items-center gap-4 border-b border-line bg-surface px-4 py-1.5 text-xs text-ink-2">
        <span aria-live="polite">
          <strong className="text-ink tabular-nums">{formatCount(facets.data?.total ?? 0)}</strong> matching entries
        </span>
        <label className="inline-flex items-center gap-1.5">
          <input type="checkbox" checked={wrap} onChange={(event) => setWrap(event.target.checked)} />
          Wrap messages
        </label>
        <ColumnPicker
          columns={columns}
          attributeKeys={facets.data?.attributeKeys ?? []}
          onChange={setColumns}
        />
        <span className="ml-auto hidden text-muted md:inline">j / k or ↑ / ↓ to move · Esc to close details</span>
      </div>

      <LogTable
        rows={rows}
        columns={columns}
        wrap={wrap}
        tz={tz}
        selectedSeq={entry}
        loading={logs.isLoading}
        hasNextPage={logs.hasNextPage}
        isFetchingNextPage={logs.isFetchingNextPage}
        resetKey={filterKey(filter)}
        onSelect={onSelectEntry}
        onLoadMore={loadMore}
      />

      {entry !== null && (
        <LogDetail sessionId={sessionId} seq={entry} onClose={closeEntry} onFilter={onFilterChange} />
      )}
    </div>
  )
}

interface HeaderProps {
  session: SessionView | undefined
  filter: FilterState
  sessionId: string
  tz: TimeZoneMode
  theme: Theme
  onToggleTz: () => void
  onToggleTheme: () => void
  onOpenAnother: () => void
  onShowUnparsed: () => void
}

function Header({
  session,
  filter,
  sessionId,
  tz,
  theme,
  onToggleTz,
  onToggleTheme,
  onOpenAnother,
  onShowUnparsed,
}: HeaderProps) {
  const [copied, setCopied] = useState(false)
  const ingesting = session?.status === 'INGESTING'
  const progress = session && session.bytesTotal > 0 ? session.bytesRead / session.bytesTotal : 0

  return (
    <header className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-line bg-surface px-4 py-2">
      <h1 className="text-base font-semibold">LogJsonReader</h1>
      {session && (
        <p className="flex min-w-0 flex-wrap items-center gap-x-3 text-sm text-ink-2">
          <span className="max-w-72 truncate font-medium text-ink" title={session.name}>
            {session.name}
          </span>
          {session.format && <span className="rounded border border-line px-1.5 text-xs">{session.format}</span>}
          <span className="tabular-nums">{formatCount(session.entries)} entries</span>
          {session.bytesTotal > 0 && <span className="hidden sm:inline">{formatBytes(session.bytesTotal)}</span>}
          {session.parseErrors > 0 && (
            <button
              type="button"
              onClick={onShowUnparsed}
              className="underline decoration-dotted underline-offset-2"
              title="Show only the lines that were not valid JSON"
            >
              {formatCount(session.parseErrors)} lines were not valid JSON
            </button>
          )}
        </p>
      )}

      {ingesting && (
        <div className="flex items-center gap-2 text-xs text-ink-2" role="status" aria-live="polite">
          Loading {Math.round(progress * 100)}%
          <div className="h-1.5 w-32 overflow-hidden rounded-full bg-line">
            <div className="h-full bg-accent transition-[width]" style={{ width: `${progress * 100}%` }} />
          </div>
        </div>
      )}
      {session?.status === 'FAILED' && (
        <p role="alert" className="text-sm" style={{ color: 'var(--lvl-error)' }}>
          Loading failed: {session.error}
        </p>
      )}

      <nav className="ml-auto flex items-center gap-2 text-sm" aria-label="Actions">
        <button
          type="button"
          className="rounded-md border border-line px-2.5 py-1 hover:bg-hover"
          onClick={() => {
            void navigator.clipboard.writeText(window.location.href).then(() => {
              setCopied(true)
              setTimeout(() => setCopied(false), 1500)
            })
          }}
          title="Copy a link to this exact view (works for anyone who can reach this server)"
        >
          {copied ? 'Link copied' : 'Share link'}
        </button>
        <details className="relative">
          <summary className="cursor-pointer list-none rounded-md border border-line px-2.5 py-1 hover:bg-hover">
            Export ▾
          </summary>
          <div className="absolute right-0 z-20 mt-1 flex w-56 flex-col rounded-md border border-line bg-surface p-1 shadow-md">
            <a className="rounded px-2 py-1.5 hover:bg-hover" href={api.exportUrl(sessionId, filter, 'ndjson')} download>
              Filtered view as NDJSON
            </a>
            <a className="rounded px-2 py-1.5 hover:bg-hover" href={api.exportUrl(sessionId, filter, 'csv')} download>
              Filtered view as CSV
            </a>
          </div>
        </details>
        <button type="button" className="rounded-md border border-line px-2.5 py-1 hover:bg-hover" onClick={onToggleTz}>
          {tz === 'utc' ? 'UTC' : 'Local time'}
        </button>
        <button
          type="button"
          className="rounded-md border border-line px-2.5 py-1 hover:bg-hover"
          onClick={onToggleTheme}
          aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
        >
          {theme === 'dark' ? 'Light' : 'Dark'}
        </button>
        <button type="button" className="rounded-md border border-line px-2.5 py-1 hover:bg-hover" onClick={onOpenAnother}>
          Open another file
        </button>
      </nav>
    </header>
  )
}

function ColumnPicker({
  columns,
  attributeKeys,
  onChange,
}: {
  columns: string[]
  attributeKeys: string[]
  onChange: (columns: string[]) => void
}) {
  const options = ['logger', 'thread', 'trace', ...attributeKeys.map((key) => ATTRIBUTE_COLUMN_PREFIX + key)]
  const toggle = (column: string) =>
    onChange(columns.includes(column) ? columns.filter((c) => c !== column) : [...columns, column])
  return (
    <details className="relative">
      <summary className="cursor-pointer list-none rounded border border-line px-2 py-0.5 hover:bg-hover">Columns ▾</summary>
      <ul className="absolute left-0 z-20 mt-1 max-h-72 w-52 overflow-auto rounded-md border border-line bg-surface p-1 shadow-md">
        {options.map((column) => (
          <li key={column}>
            <label className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 hover:bg-hover">
              <input type="checkbox" checked={columns.includes(column)} onChange={() => toggle(column)} />
              <span className="truncate">{columnLabel(column)}</span>
            </label>
          </li>
        ))}
      </ul>
    </details>
  )
}
