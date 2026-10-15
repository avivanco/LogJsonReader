import type { Level } from '../api/types'
import { isLevel } from './levels'

export type SortDirection = 'asc' | 'desc'

/** Everything the user can narrow the log down with. Serialized to the URL and to API query parameters. */
export interface FilterState {
  levels: Level[]
  from?: string
  to?: string
  q: string
  loggers: string[]
  threads: string[]
  traceId?: string
  attrs: Record<string, string>
  sort: SortDirection
}

export const EMPTY_FILTER: FilterState = {
  levels: [],
  q: '',
  loggers: [],
  threads: [],
  attrs: {},
  sort: 'asc',
}

const ATTR_PREFIX = 'attr.'

/** Query parameters understood by the backend (`/logs`, `/facets`, `/export`). Sort is only sent when requested. */
export function filterToParams(filter: FilterState, options: { includeSort?: boolean } = {}): URLSearchParams {
  const params = new URLSearchParams()
  filter.levels.forEach((level) => params.append('level', level))
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  if (filter.q.trim()) params.set('q', filter.q.trim())
  filter.loggers.forEach((logger) => params.append('logger', logger))
  filter.threads.forEach((thread) => params.append('thread', thread))
  if (filter.traceId) params.set('traceId', filter.traceId)
  Object.entries(filter.attrs).forEach(([key, value]) => params.set(ATTR_PREFIX + key, value))
  if (options.includeSort) params.set('sort', filter.sort)
  return params
}

/** Inverse of {@link filterToParams}; unknown or malformed values are ignored. */
export function filterFromParams(params: URLSearchParams): FilterState {
  const attrs: Record<string, string> = {}
  params.forEach((value, key) => {
    if (key.startsWith(ATTR_PREFIX) && key.length > ATTR_PREFIX.length) {
      attrs[key.slice(ATTR_PREFIX.length)] = value
    }
  })
  return {
    levels: params.getAll('level').filter(isLevel),
    from: validDate(params.get('from')),
    to: validDate(params.get('to')),
    q: params.get('q') ?? '',
    loggers: params.getAll('logger'),
    threads: params.getAll('thread'),
    traceId: params.get('traceId') || undefined,
    attrs,
    sort: params.get('sort') === 'desc' ? 'desc' : 'asc',
  }
}

export function filterKey(filter: FilterState): string {
  return filterToParams(filter, { includeSort: true }).toString()
}

export function hasActiveFilters(filter: FilterState): boolean {
  return (
    filter.levels.length > 0 ||
    !!filter.from ||
    !!filter.to ||
    filter.q.trim() !== '' ||
    filter.loggers.length > 0 ||
    filter.threads.length > 0 ||
    !!filter.traceId ||
    Object.keys(filter.attrs).length > 0
  )
}

function validDate(value: string | null): string | undefined {
  return value && !Number.isNaN(Date.parse(value)) ? value : undefined
}
