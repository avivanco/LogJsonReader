import { useVirtualizer } from '@tanstack/react-virtual'
import { useEffect, useRef, type KeyboardEvent } from 'react'
import type { LogSummary } from '../../api/types'
import { LevelBadge } from '../../components/LevelBadge'
import { shortLogger } from '../../lib/format'
import { formatTimestamp, type TimeZoneMode } from '../../lib/time'
import { ATTRIBUTE_COLUMN_PREFIX, columnLabel } from './columns'

interface Props {
  rows: LogSummary[]
  /** Optional columns between the level and the message: 'logger', 'thread', 'trace' or 'attr:<name>'. */
  columns: string[]
  wrap: boolean
  tz: TimeZoneMode
  selectedSeq: number | null
  loading: boolean
  hasNextPage: boolean
  isFetchingNextPage: boolean
  /** Changes whenever the filter does, so the list scrolls back to the top. */
  resetKey: string
  onSelect: (seq: number) => void
  onLoadMore: () => void
}

const ESTIMATED_ROW = 28
const TIME_WIDTH = 176
const LEVEL_WIDTH = 84
const COLUMN_WIDTH = 180
const MESSAGE_MIN_WIDTH = 320

function cellValue(row: LogSummary, column: string): string {
  switch (column) {
    case 'logger':
      return row.logger ?? ''
    case 'thread':
      return row.thread ?? ''
    case 'trace':
      return row.traceId ?? ''
    default:
      return row.attributes[column.slice(ATTRIBUTE_COLUMN_PREFIX.length)] ?? ''
  }
}

export function LogTable({
  rows,
  columns,
  wrap,
  tz,
  selectedSeq,
  loading,
  hasNextPage,
  isFetchingNextPage,
  resetKey,
  onSelect,
  onLoadMore,
}: Props) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const template = `${TIME_WIDTH}px ${LEVEL_WIDTH}px ${columns.map(() => `${COLUMN_WIDTH}px`).join(' ')} minmax(${MESSAGE_MIN_WIDTH}px, 1fr)`
  const minWidth = TIME_WIDTH + LEVEL_WIDTH + columns.length * COLUMN_WIDTH + MESSAGE_MIN_WIDTH

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ESTIMATED_ROW,
    overscan: 14,
    getItemKey: (index) => rows[index]?.seq ?? index,
  })
  const items = virtualizer.getVirtualItems()
  const lastIndex = items.length > 0 ? items[items.length - 1].index : -1

  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage && lastIndex >= rows.length - 25) onLoadMore()
  }, [hasNextPage, isFetchingNextPage, lastIndex, rows.length, onLoadMore])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0 })
  }, [resetKey])

  // Wrapping changes row heights; drop cached measurements so rows re-measure.
  useEffect(() => {
    virtualizer.measure()
  }, [wrap, columns.length, virtualizer])

  function onKeyDown(event: KeyboardEvent) {
    const move = event.key === 'j' || event.key === 'ArrowDown' ? 1 : event.key === 'k' || event.key === 'ArrowUp' ? -1 : 0
    if (move === 0 || rows.length === 0) return
    event.preventDefault()
    const current = rows.findIndex((row) => row.seq === selectedSeq)
    const next = Math.min(rows.length - 1, Math.max(0, current === -1 ? 0 : current + move))
    onSelect(rows[next].seq)
    virtualizer.scrollToIndex(next, { align: 'auto' })
  }

  return (
    <div
      ref={scrollRef}
      role="grid"
      aria-label="Log entries"
      aria-rowcount={rows.length}
      tabIndex={0}
      onKeyDown={onKeyDown}
      className="relative min-h-0 flex-1 overflow-auto bg-surface"
    >
      <div style={{ minWidth }}>
        <div
          role="row"
          className="sticky top-0 z-10 grid border-b border-line bg-surface text-xs font-semibold uppercase tracking-wide text-ink-2"
          style={{ gridTemplateColumns: template }}
        >
          <div role="columnheader" className="px-3 py-2">
            Time ({tz === 'utc' ? 'UTC' : 'local'})
          </div>
          <div role="columnheader" className="px-3 py-2">
            Level
          </div>
          {columns.map((column) => (
            <div key={column} role="columnheader" className="truncate px-3 py-2" title={columnLabel(column)}>
              {columnLabel(column)}
            </div>
          ))}
          <div role="columnheader" className="px-3 py-2">
            Message
          </div>
        </div>

        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {items.map((item) => {
            const row = rows[item.index]
            if (!row) return null
            const selected = row.seq === selectedSeq
            return (
              <div
                key={item.key}
                ref={virtualizer.measureElement}
                data-index={item.index}
                role="row"
                aria-selected={selected}
                onClick={() => onSelect(row.seq)}
                className={`absolute left-0 grid w-full cursor-pointer items-start border-b border-line/60 text-[13px] hover:bg-hover ${
                  selected ? 'bg-selected' : ''
                }`}
                style={{ transform: `translateY(${item.start}px)`, gridTemplateColumns: template, minHeight: ESTIMATED_ROW }}
              >
                <div role="gridcell" className="px-3 py-1 font-mono text-xs whitespace-nowrap text-ink-2">
                  {formatTimestamp(row.timestamp, tz)}
                </div>
                <div role="gridcell" className="px-3 py-1">
                  <LevelBadge level={row.level} />
                </div>
                {columns.map((column) => {
                  const value = cellValue(row, column)
                  return (
                    <div
                      key={column}
                      role="gridcell"
                      className="truncate px-3 py-1 text-ink-2"
                      title={value}
                    >
                      {column === 'logger' ? shortLogger(value) : value}
                    </div>
                  )
                })}
                <div
                  role="gridcell"
                  className={`px-3 py-1 font-mono text-xs ${wrap ? 'break-words whitespace-pre-wrap' : 'truncate'}`}
                  title={wrap ? undefined : (row.message ?? '')}
                >
                  {row.message}
                  {row.hasStackTrace && (
                    <span className="ml-2 rounded border border-line px-1 font-sans text-[10px] text-ink-2 uppercase">
                      stack trace
                    </span>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {loading && rows.length === 0 && <p className="p-6 text-sm text-muted">Loading entries…</p>}
        {!loading && rows.length === 0 && (
          <p className="p-6 text-sm text-muted">No entries match the current filters.</p>
        )}
        {isFetchingNextPage && <p className="p-3 text-center text-xs text-muted">Loading more…</p>}
      </div>
    </div>
  )
}
