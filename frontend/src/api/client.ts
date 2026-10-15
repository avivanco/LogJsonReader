import { filterToParams, type FilterState } from '../lib/filters'
import type { AppConfig, Facets, LogEntry, LogSummary, Page, SessionView, SourceFile } from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function problemMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string }
    return problem.detail ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw new ApiError(response.status, await problemMessage(response))
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

const base = (sessionId: string) => `/api/sessions/${encodeURIComponent(sessionId)}`

export const api = {
  config: () => request<AppConfig>('/api/config'),

  files: () => request<SourceFile[]>('/api/files'),

  session: (id: string) => request<SessionView>(base(id)),

  deleteSession: (id: string) => request<void>(base(id), { method: 'DELETE' }),

  openPath: (path: string) =>
    request<SessionView>('/api/sessions/from-path', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path }),
    }),

  logs: (id: string, filter: FilterState, cursor: string | null, size = 100) => {
    const params = filterToParams(filter, { includeSort: true })
    params.set('size', String(size))
    if (cursor) params.set('cursor', cursor)
    return request<Page<LogSummary>>(`${base(id)}/logs?${params}`)
  },

  entry: (id: string, seq: number) => request<LogEntry>(`${base(id)}/logs/${seq}`),

  facets: (id: string, filter: FilterState) => request<Facets>(`${base(id)}/facets?${filterToParams(filter)}`),

  exportUrl: (id: string, filter: FilterState, format: 'ndjson' | 'csv') => {
    const params = filterToParams(filter)
    params.set('format', format)
    return `${base(id)}/export?${params}`
  },

  /** Uses XMLHttpRequest because `fetch` cannot report upload progress. */
  uploadFile: (file: File, onProgress: (fraction: number) => void) =>
    new Promise<SessionView>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', '/api/sessions')
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) onProgress(event.loaded / event.total)
      }
      xhr.onerror = () => reject(new ApiError(0, 'Network error while uploading the file'))
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText) as SessionView)
          return
        }
        let message = xhr.statusText
        try {
          message = (JSON.parse(xhr.responseText) as { detail?: string }).detail ?? message
        } catch {
          // keep the status text
        }
        reject(new ApiError(xhr.status, message))
      }
      const form = new FormData()
      form.append('file', file)
      xhr.send(form)
    }),
}
