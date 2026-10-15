import { describe, expect, it } from 'vitest'
import { parseLocation, stateToSearch } from '../hooks/useAppState'
import { EMPTY_FILTER, filterFromParams, filterKey, filterToParams, hasActiveFilters, type FilterState } from './filters'

const full: FilterState = {
  levels: ['ERROR', 'WARN'],
  from: '2026-10-15T00:00:00.000Z',
  to: '2026-10-15T01:00:00.000Z',
  q: 'timeout',
  loggers: ['com.acme.Service'],
  threads: ['worker-1', 'worker-2'],
  traceId: 'abc123',
  attrs: { username: 'alice', 'tenant.id': '7' },
  sort: 'desc',
}

describe('filter <-> query parameters', () => {
  it('round-trips every criterion', () => {
    const params = filterToParams(full, { includeSort: true })
    expect(filterFromParams(new URLSearchParams(params.toString()))).toEqual(full)
  })

  it('maps to the parameter names the backend expects', () => {
    const params = filterToParams(full)
    expect(params.getAll('level')).toEqual(['ERROR', 'WARN'])
    expect(params.get('q')).toBe('timeout')
    expect(params.getAll('thread')).toEqual(['worker-1', 'worker-2'])
    expect(params.get('traceId')).toBe('abc123')
    expect(params.get('attr.username')).toBe('alice')
    expect(params.has('sort')).toBe(false)
  })

  it('ignores unknown levels and invalid dates in shared links', () => {
    const filter = filterFromParams(new URLSearchParams('level=LOUD&level=INFO&from=yesterday&sort=sideways'))
    expect(filter.levels).toEqual(['INFO'])
    expect(filter.from).toBeUndefined()
    expect(filter.sort).toBe('asc')
  })

  it('trims whitespace-only search text out of the request', () => {
    expect(filterToParams({ ...EMPTY_FILTER, q: '   ' }).has('q')).toBe(false)
    expect(hasActiveFilters({ ...EMPTY_FILTER, q: '   ' })).toBe(false)
  })

  it('reports active filters and produces distinct cache keys', () => {
    expect(hasActiveFilters(EMPTY_FILTER)).toBe(false)
    expect(hasActiveFilters(full)).toBe(true)
    expect(filterKey(full)).not.toBe(filterKey({ ...full, sort: 'asc' }))
  })
})

describe('app state <-> location', () => {
  it('keeps the session, filters and open entry in the URL', () => {
    const search = stateToSearch({ sessionId: 'sess-1', filter: full, entry: 42 })
    expect(parseLocation(search)).toEqual({ sessionId: 'sess-1', filter: full, entry: 42 })
  })

  it('produces an empty query string for the landing page', () => {
    expect(stateToSearch({ sessionId: null, filter: EMPTY_FILTER, entry: null })).toBe('')
  })

  it('ignores a malformed entry number', () => {
    expect(parseLocation('?s=x&e=abc').entry).toBeNull()
    expect(parseLocation('?s=x&e=-3').entry).toBeNull()
  })
})
