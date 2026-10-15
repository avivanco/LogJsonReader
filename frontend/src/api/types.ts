export type Level = 'UNKNOWN' | 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL'

export type SessionStatus = 'INGESTING' | 'READY' | 'FAILED'

export interface SessionView {
  id: string
  name: string
  status: SessionStatus
  format: string | null
  bytesTotal: number
  bytesRead: number
  entries: number
  parseErrors: number
  error: string | null
  createdAt: string
}

export interface LogSummary {
  seq: number
  timestamp: string
  level: Level
  logger: string | null
  thread: string | null
  message: string | null
  traceId: string | null
  spanId: string | null
  hasStackTrace: boolean
  attributes: Record<string, string>
}

export interface LogEntry {
  seq: number
  timestamp: string
  level: Level
  logger: string | null
  thread: string | null
  message: string | null
  traceId: string | null
  spanId: string | null
  stackTrace: string | null
  attributes: Record<string, string>
  raw: string
}

export interface Page<T> {
  items: T[]
  nextCursor: string | null
}

export interface ValueCount {
  value: string
  count: number
}

export interface HistogramBucket {
  start: string
  counts: Partial<Record<Level, number>>
}

export interface Histogram {
  start: string
  end: string
  bucketMillis: number
  buckets: HistogramBucket[]
}

export interface Facets {
  total: number
  levelCounts: Partial<Record<Level, number>>
  loggers: ValueCount[]
  threads: ValueCount[]
  attributeKeys: string[]
  histogram: Histogram | null
}

export interface SourceFile {
  path: string
  size: number
  modified: string
}

export interface AppConfig {
  pathSourceEnabled: boolean
}
