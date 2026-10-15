import type { Level } from '../api/types'

/** Ordered from least to most severe; UNKNOWN (lines that were not valid JSON) comes first. */
export const LEVELS: Level[] = ['UNKNOWN', 'TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

export const LEVEL_COLOR: Record<Level, string> = {
  UNKNOWN: 'var(--lvl-unknown)',
  TRACE: 'var(--lvl-trace)',
  DEBUG: 'var(--lvl-debug)',
  INFO: 'var(--lvl-info)',
  WARN: 'var(--lvl-warn)',
  ERROR: 'var(--lvl-error)',
  FATAL: 'var(--lvl-fatal)',
}

export function isLevel(value: string): value is Level {
  return (LEVELS as string[]).includes(value)
}
