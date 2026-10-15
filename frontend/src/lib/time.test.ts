import { describe, expect, it } from 'vitest'
import { formatBytes, formatCompact, shortLogger } from './format'
import { formatAxisTime, formatTimestamp, fromInputValue, toInputValue } from './time'

describe('time helpers', () => {
  it('formats timestamps in UTC with milliseconds', () => {
    expect(formatTimestamp('2026-10-15T00:08:10.340052226Z', 'utc')).toBe('2026-10-15 00:08:10.340')
  })

  it('round-trips date filter input values in UTC', () => {
    const iso = '2026-10-15T00:08:10.000Z'
    expect(toInputValue(iso, 'utc')).toBe('2026-10-15T00:08:10')
    expect(fromInputValue('2026-10-15T00:08:10', 'utc')).toBe(iso)
    expect(fromInputValue('2026-10-15T00:08', 'utc')).toBe('2026-10-15T00:08:00.000Z')
  })

  it('round-trips date filter input values in local time', () => {
    const iso = '2026-10-15T12:30:45.000Z'
    expect(fromInputValue(toInputValue(iso, 'local'), 'local')).toBe(iso)
  })

  it('treats empty or invalid input as no value', () => {
    expect(fromInputValue('', 'utc')).toBeUndefined()
    expect(fromInputValue('not a date', 'utc')).toBeUndefined()
    expect(toInputValue(undefined, 'utc')).toBe('')
  })

  it('labels axes by the span being shown', () => {
    const t = Date.parse('2026-10-15T13:05:09Z')
    expect(formatAxisTime(t, 'utc', 60_000)).toBe('13:05:09')
    expect(formatAxisTime(t, 'utc', 3_600_000)).toBe('13:05')
    expect(formatAxisTime(t, 'utc', 7 * 86_400_000)).toBe('10-15 13:05')
  })
})

describe('format helpers', () => {
  it('formats byte sizes', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(500 * 1024 * 1024)).toBe('500 MB')
  })

  it('compacts large counts for chart ticks', () => {
    expect(formatCompact(999)).toBe('999')
    expect(formatCompact(1000)).toBe('1K')
    expect(formatCompact(12_900)).toBe('13K')
    expect(formatCompact(4_200_000)).toBe('4.2M')
  })

  it('shortens logger names', () => {
    expect(shortLogger('com.acme.billing.InvoiceService')).toBe('InvoiceService')
    expect(shortLogger('plain')).toBe('plain')
    expect(shortLogger(null)).toBe('')
  })
})
