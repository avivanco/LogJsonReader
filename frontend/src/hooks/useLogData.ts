import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { SessionStatus } from '../api/types'
import { filterKey, type FilterState } from '../lib/filters'

const PAGE_SIZE = 100

/** While a file is still loading, results are partial, so poll to show them growing. */
function pollWhileIngesting(status: SessionStatus | undefined) {
  return status === 'INGESTING' ? 2000 : false
}

export function useFacets(sessionId: string, filter: FilterState, status: SessionStatus | undefined) {
  return useQuery({
    queryKey: ['facets', sessionId, filterKey(filter)],
    queryFn: () => api.facets(sessionId, filter),
    placeholderData: keepPreviousData,
    refetchInterval: pollWhileIngesting(status),
  })
}

/** Keyset-paginated log rows; the cursor returned by one page is the parameter of the next. */
export function useInfiniteLogs(sessionId: string, filter: FilterState, status: SessionStatus | undefined) {
  return useInfiniteQuery({
    queryKey: ['logs', sessionId, filterKey(filter)],
    queryFn: ({ pageParam }) => api.logs(sessionId, filter, pageParam, PAGE_SIZE),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    refetchInterval: pollWhileIngesting(status),
  })
}

export function useEntry(sessionId: string, seq: number | null) {
  return useQuery({
    queryKey: ['entry', sessionId, seq],
    queryFn: () => api.entry(sessionId, seq as number),
    enabled: seq !== null,
    staleTime: Infinity,
  })
}
