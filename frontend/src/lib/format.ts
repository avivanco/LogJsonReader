export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unit]}`
}

/** 1,284 -> "1,284"; 12,900 -> "12.9K"; 4,200,000 -> "4.2M" (compact from 1,000 up, for chart ticks). */
export function formatCompact(value: number): string {
  if (value < 1000) return String(value)
  if (value < 1_000_000) return `${trim(value / 1000)}K`
  return `${trim(value / 1_000_000)}M`
}

export function formatCount(value: number): string {
  return value.toLocaleString('en-US')
}

/** Last segment of a dotted logger name: `com.acme.billing.InvoiceService` -> `InvoiceService`. */
export function shortLogger(logger: string | null): string {
  if (!logger) return ''
  const index = logger.lastIndexOf('.')
  return index >= 0 ? logger.slice(index + 1) : logger
}

function trim(value: number): string {
  return value.toFixed(value >= 10 ? 0 : 1).replace(/\.0$/, '')
}
