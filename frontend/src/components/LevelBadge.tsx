import type { Level } from '../api/types'
import { LEVEL_COLOR } from '../lib/levels'

/** Colored mark for a level. UNKNOWN is hatched so it is distinguishable without hue. */
export function LevelSwatch({ level, size = 10 }: { level: Level; size?: number }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 rounded-[3px] ${level === 'UNKNOWN' ? 'swatch-unknown' : ''}`}
      style={{ width: size, height: size, background: level === 'UNKNOWN' ? undefined : LEVEL_COLOR[level] }}
    />
  )
}

/** Swatch + level name: identity is always carried by the text as well as the color. */
export function LevelBadge({ level }: { level: Level }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-ink">
      <LevelSwatch level={level} />
      {level}
    </span>
  )
}
