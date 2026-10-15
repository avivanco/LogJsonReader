import { useCallback, useEffect, useRef, useState } from 'react'
import { EMPTY_FILTER, filterFromParams, filterToParams, type FilterState } from '../lib/filters'

/**
 * Everything that identifies "what the user is looking at": the loaded session, the filters, and the open entry.
 * It lives in the URL so any view can be shared as a link and survives a reload.
 */
export interface AppState {
  sessionId: string | null
  filter: FilterState
  entry: number | null
}

export const EMPTY_STATE: AppState = { sessionId: null, filter: EMPTY_FILTER, entry: null }

const SESSION_PARAM = 's'
const ENTRY_PARAM = 'e'

export function parseLocation(search: string): AppState {
  const params = new URLSearchParams(search)
  const entry = Number(params.get(ENTRY_PARAM))
  return {
    sessionId: params.get(SESSION_PARAM),
    filter: filterFromParams(params),
    entry: Number.isInteger(entry) && entry > 0 ? entry : null,
  }
}

export function stateToSearch(state: AppState): string {
  const params = filterToParams(state.filter, { includeSort: state.filter.sort === 'desc' })
  if (state.sessionId) params.set(SESSION_PARAM, state.sessionId)
  if (state.entry !== null) params.set(ENTRY_PARAM, String(state.entry))
  const search = params.toString()
  return search ? `?${search}` : ''
}

type Updater = AppState | ((previous: AppState) => AppState)

/** App state mirrored into the query string. `push` creates a history entry (used when switching sessions). */
export function useAppState(): [AppState, (updater: Updater, options?: { push?: boolean }) => void] {
  const [state, setState] = useState<AppState>(() => parseLocation(window.location.search))
  const latest = useRef(state)

  useEffect(() => {
    const onPopState = () => {
      latest.current = parseLocation(window.location.search)
      setState(latest.current)
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const update = useCallback((updater: Updater, options: { push?: boolean } = {}) => {
    const next = typeof updater === 'function' ? updater(latest.current) : updater
    latest.current = next
    setState(next)
    const url = `${window.location.pathname}${stateToSearch(next)}`
    if (options.push) {
      window.history.pushState(null, '', url)
    } else {
      window.history.replaceState(null, '', url)
    }
  }, [])

  return [state, update]
}
