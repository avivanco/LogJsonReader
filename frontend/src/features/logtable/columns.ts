export const ATTRIBUTE_COLUMN_PREFIX = 'attr:'

/** Header text for an optional column id: 'logger', 'thread', 'trace' or 'attr:<name>'. */
export function columnLabel(column: string): string {
  if (column === 'logger') return 'Logger'
  if (column === 'thread') return 'Thread'
  if (column === 'trace') return 'Trace ID'
  return column.startsWith(ATTRIBUTE_COLUMN_PREFIX) ? column.slice(ATTRIBUTE_COLUMN_PREFIX.length) : column
}
