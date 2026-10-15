import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api } from '../api/client'
import type { SessionView } from '../api/types'

/**
 * Loads a session and, while it is still being ingested, follows its server-sent progress events. When ingestion
 * finishes, cached facets/log pages are invalidated so the final numbers show up.
 */
export function useSession(sessionId: string) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['session', sessionId],
    queryFn: () => api.session(sessionId),
    retry: false,
    staleTime: Infinity,
  })
  const status = query.data?.status

  useEffect(() => {
    if (status !== 'INGESTING') return
    const source = new EventSource(`/api/sessions/${encodeURIComponent(sessionId)}/events`)
    source.addEventListener('progress', (event) => {
      const view = JSON.parse((event as MessageEvent<string>).data) as SessionView
      queryClient.setQueryData(['session', sessionId], view)
      if (view.status !== 'INGESTING') source.close()
    })
    // The server completes the stream when ingestion ends; an error means the connection dropped, so re-read once.
    source.onerror = () => {
      source.close()
      void queryClient.invalidateQueries({ queryKey: ['session', sessionId] })
    }
    return () => source.close()
  }, [sessionId, status, queryClient])

  useEffect(() => {
    if (status === 'READY' || status === 'FAILED') {
      void queryClient.invalidateQueries({ queryKey: ['facets', sessionId] })
      void queryClient.invalidateQueries({ queryKey: ['logs', sessionId] })
    }
  }, [sessionId, status, queryClient])

  return query
}
