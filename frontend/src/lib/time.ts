export type TimeZoneMode = 'utc' | 'local'

const pad = (value: number, width = 2) => String(value).padStart(width, '0')

interface Parts {
  year: number
  month: number
  day: number
  hours: number
  minutes: number
  seconds: number
  millis: number
}

function parts(date: Date, tz: TimeZoneMode): Parts {
  return tz === 'utc'
    ? {
        year: date.getUTCFullYear(),
        month: date.getUTCMonth() + 1,
        day: date.getUTCDate(),
        hours: date.getUTCHours(),
        minutes: date.getUTCMinutes(),
        seconds: date.getUTCSeconds(),
        millis: date.getUTCMilliseconds(),
      }
    : {
        year: date.getFullYear(),
        month: date.getMonth() + 1,
        day: date.getDate(),
        hours: date.getHours(),
        minutes: date.getMinutes(),
        seconds: date.getSeconds(),
        millis: date.getMilliseconds(),
      }
}

/** `2026-10-15 00:08:10.340` */
export function formatTimestamp(iso: string, tz: TimeZoneMode): string {
  const p = parts(new Date(iso), tz)
  return `${p.year}-${pad(p.month)}-${pad(p.day)} ${pad(p.hours)}:${pad(p.minutes)}:${pad(p.seconds)}.${pad(p.millis, 3)}`
}

/** Short label for chart axes: time of day for short spans, date + hour for longer ones. */
export function formatAxisTime(ms: number, tz: TimeZoneMode, spanMs: number): string {
  const p = parts(new Date(ms), tz)
  const time = `${pad(p.hours)}:${pad(p.minutes)}`
  if (spanMs <= 2 * 86_400_000) {
    return spanMs <= 5 * 60_000 ? `${time}:${pad(p.seconds)}` : time
  }
  return `${pad(p.month)}-${pad(p.day)} ${time}`
}

/** Value for an `<input type="datetime-local" step="1">`, in the chosen time zone. */
export function toInputValue(iso: string | undefined, tz: TimeZoneMode): string {
  if (!iso) return ''
  const p = parts(new Date(iso), tz)
  return `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hours)}:${pad(p.minutes)}:${pad(p.seconds)}`
}

/** Inverse of {@link toInputValue}; returns undefined for an empty or invalid value. */
export function fromInputValue(value: string, tz: TimeZoneMode): string | undefined {
  if (!value) return undefined
  const withSeconds = value.length === 16 ? `${value}:00` : value
  const date = tz === 'utc' ? new Date(`${withSeconds}Z`) : new Date(withSeconds)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}
