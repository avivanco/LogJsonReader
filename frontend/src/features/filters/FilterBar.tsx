import { useEffect, useRef, useState } from 'react'
import type { Facets, Level } from '../../api/types'
import { LevelSwatch } from '../../components/LevelBadge'
import { EMPTY_FILTER, hasActiveFilters, type FilterState } from '../../lib/filters'
import { formatCount } from '../../lib/format'
import { LEVELS } from '../../lib/levels'
import { fromInputValue, toInputValue, type TimeZoneMode } from '../../lib/time'

interface Props {
  filter: FilterState
  facets: Facets | undefined
  tz: TimeZoneMode
  onChange: (updater: (previous: FilterState) => FilterState) => void
}

const QUICK_RANGES = [
  { label: 'Last 5 min', ms: 5 * 60_000 },
  { label: 'Last hour', ms: 3_600_000 },
  { label: 'Last 6 h', ms: 6 * 3_600_000 },
]

export function FilterBar({ filter, facets, tz, onChange }: Props) {
  const searchRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(filter.q)
  const [syncedQuery, setSyncedQuery] = useState(filter.q)

  // Keep the box in sync when the filter changes elsewhere (clear all, shared link, history navigation).
  if (filter.q !== syncedQuery) {
    setSyncedQuery(filter.q)
    setText(filter.q)
  }

  // Debounce typing so every keystroke does not hit the server.
  useEffect(() => {
    if (text === filter.q) return
    const timer = setTimeout(() => onChange((previous) => ({ ...previous, q: text })), 300)
    return () => clearTimeout(timer)
  }, [text, filter.q, onChange])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement
      if (event.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) {
        event.preventDefault()
        searchRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const toggleLevel = (level: Level) =>
    onChange((previous) => ({
      ...previous,
      levels: previous.levels.includes(level)
        ? previous.levels.filter((current) => current !== level)
        : [...previous.levels, level],
    }))

  const end = facets?.histogram?.end
  const applyQuickRange = (ms: number) => {
    if (!end) return
    const to = new Date(end)
    onChange((previous) => ({ ...previous, from: new Date(to.getTime() - ms).toISOString(), to: to.toISOString() }))
  }

  const visibleLevels = LEVELS.filter((level) => level !== 'UNKNOWN' || (facets?.levelCounts.UNKNOWN ?? 0) > 0)

  return (
    <section aria-label="Filters" className="flex flex-col gap-2 border-b border-line bg-surface px-4 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Severity" className="flex flex-wrap gap-1.5">
          {visibleLevels.map((level) => {
            const selected = filter.levels.includes(level)
            const count = facets?.levelCounts[level] ?? 0
            return (
              <button
                key={level}
                type="button"
                aria-pressed={selected}
                onClick={() => toggleLevel(level)}
                className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
                  selected ? 'border-accent bg-selected' : 'border-line hover:bg-hover'
                } ${count === 0 && !selected ? 'opacity-50' : ''}`}
              >
                <LevelSwatch level={level} />
                {level}
                <span className="font-normal text-ink-2 tabular-nums">{formatCount(count)}</span>
              </button>
            )
          })}
        </div>

        <div className="ml-auto flex items-center gap-2">
          <label className="sr-only" htmlFor="search">
            Search
          </label>
          <input
            id="search"
            ref={searchRef}
            type="search"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Search text…  ( / )"
            className="w-64 rounded-md border border-line bg-page px-3 py-1.5 text-sm"
          />
          <label className="sr-only" htmlFor="sort">
            Order
          </label>
          <select
            id="sort"
            value={filter.sort}
            onChange={(event) =>
              onChange((previous) => ({ ...previous, sort: event.target.value === 'desc' ? 'desc' : 'asc' }))
            }
            className="rounded-md border border-line bg-page px-2 py-1.5 text-sm"
          >
            <option value="asc">Oldest first</option>
            <option value="desc">Newest first</option>
          </select>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-ink-2">From</span>
        <input
          type="datetime-local"
          step={1}
          aria-label={`From (${tz === 'utc' ? 'UTC' : 'local time'})`}
          value={toInputValue(filter.from, tz)}
          onChange={(event) => onChange((previous) => ({ ...previous, from: fromInputValue(event.target.value, tz) }))}
          className="rounded-md border border-line bg-page px-2 py-1"
        />
        <span className="text-ink-2">to</span>
        <input
          type="datetime-local"
          step={1}
          aria-label={`To (${tz === 'utc' ? 'UTC' : 'local time'})`}
          value={toInputValue(filter.to, tz)}
          onChange={(event) => onChange((previous) => ({ ...previous, to: fromInputValue(event.target.value, tz) }))}
          className="rounded-md border border-line bg-page px-2 py-1"
        />
        <span className="text-xs text-muted">{tz === 'utc' ? 'UTC' : 'local time'}</span>
        {QUICK_RANGES.map((range) => (
          <button
            key={range.label}
            type="button"
            disabled={!end}
            onClick={() => applyQuickRange(range.ms)}
            className="rounded-md border border-line px-2 py-1 text-xs hover:bg-hover disabled:opacity-40"
          >
            {range.label}
          </button>
        ))}
        <span className="text-xs text-muted">(relative to the end of the log)</span>

        {hasActiveFilters(filter) && (
          <button
            type="button"
            onClick={() => onChange((previous) => ({ ...EMPTY_FILTER, sort: previous.sort }))}
            className="ml-auto rounded-md border border-line px-2.5 py-1 text-xs font-medium hover:bg-hover"
          >
            Clear all filters
          </button>
        )}
      </div>

      <ActiveChips filter={filter} onChange={onChange} />
    </section>
  )
}

function ActiveChips({ filter, onChange }: Pick<Props, 'filter' | 'onChange'>) {
  const chips: { key: string; label: string; remove: (previous: FilterState) => FilterState }[] = [
    ...filter.loggers.map((logger) => ({
      key: `logger:${logger}`,
      label: `logger: ${logger}`,
      remove: (previous: FilterState) => ({ ...previous, loggers: previous.loggers.filter((v) => v !== logger) }),
    })),
    ...filter.threads.map((thread) => ({
      key: `thread:${thread}`,
      label: `thread: ${thread}`,
      remove: (previous: FilterState) => ({ ...previous, threads: previous.threads.filter((v) => v !== thread) }),
    })),
    ...(filter.traceId
      ? [{ key: 'trace', label: `trace: ${filter.traceId}`, remove: (p: FilterState) => ({ ...p, traceId: undefined }) }]
      : []),
    ...Object.entries(filter.attrs).map(([name, value]) => ({
      key: `attr:${name}`,
      label: `${name} = ${value}`,
      remove: (previous: FilterState) => {
        const { [name]: _removed, ...rest } = previous.attrs
        return { ...previous, attrs: rest }
      },
    })),
  ]
  if (chips.length === 0) return null
  return (
    <ul className="flex flex-wrap gap-1.5" aria-label="Active filters">
      {chips.map((chip) => (
        <li
          key={chip.key}
          className="inline-flex max-w-full items-center gap-1 rounded-full border border-line bg-page py-0.5 pr-1 pl-2.5 text-xs"
        >
          <span className="truncate" title={chip.label}>
            {chip.label}
          </span>
          <button
            type="button"
            aria-label={`Remove filter ${chip.label}`}
            onClick={() => onChange(chip.remove)}
            className="rounded-full px-1.5 text-ink-2 hover:bg-hover"
          >
            ×
          </button>
        </li>
      ))}
    </ul>
  )
}
