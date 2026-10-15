import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import type { Histogram, Level } from '../../api/types'
import { LevelSwatch } from '../../components/LevelBadge'
import { formatCompact, formatCount } from '../../lib/format'
import { LEVELS, LEVEL_COLOR } from '../../lib/levels'
import { formatAxisTime, formatTimestamp, type TimeZoneMode } from '../../lib/time'

interface Props {
  histogram: Histogram
  from?: string
  to?: string
  tz: TimeZoneMode
  onRangeChange: (from: string, to: string) => void
}

const HEIGHT = 132
const MARGIN = { top: 8, right: 8, bottom: 22, left: 40 }
const MAX_BAR = 24
const GAP = 2
const RADIUS = 4

interface Slot {
  index: number
  start: number
  counts: Partial<Record<Level, number>>
  total: number
}

/** Rectangle with only the top corners rounded (bars are square at the baseline). */
function roundedTop(x: number, y: number, width: number, height: number, radius: number): string {
  const r = Math.max(0, Math.min(radius, width / 2, height))
  return `M${x},${y + height}V${y + r}Q${x},${y} ${x + r},${y}H${x + width - r}Q${x + width},${y} ${x + width},${y + r}V${y + height}Z`
}

function niceMax(value: number): number {
  if (value <= 1) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return step * magnitude
}

/**
 * Stacked column chart of entries per time bucket, split by level. Drag across the bars (or click one) to filter the
 * log to that time range. A legend, a hover tooltip and a table view make every value reachable without color.
 */
export function Timeline({ histogram, from, to, tz, onRangeChange }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(800)
  const [hover, setHover] = useState<number | null>(null)
  const [drag, setDrag] = useState<{ anchor: number; current: number } | null>(null)
  const [tableView, setTableView] = useState(false)

  useEffect(() => {
    const element = containerRef.current
    if (!element) return
    setWidth(Math.max(element.clientWidth, 320))
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(entry.contentRect.width, 320)))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const { slots, t0, bucketMs, max } = useMemo(() => {
    const bucket = histogram.bucketMillis
    const first = Math.floor(Date.parse(histogram.start) / bucket) * bucket
    const last = Math.floor(Date.parse(histogram.end) / bucket) * bucket
    const count = Math.round((last - first) / bucket) + 1
    const byStart = new Map(histogram.buckets.map((b) => [Date.parse(b.start), b.counts]))
    const all: Slot[] = Array.from({ length: count }, (_, index) => {
      const start = first + index * bucket
      const counts = byStart.get(start) ?? {}
      return { index, start, counts, total: Object.values(counts).reduce((sum, n) => sum + (n ?? 0), 0) }
    })
    return { slots: all, t0: first, bucketMs: bucket, max: niceMax(Math.max(1, ...all.map((s) => s.total))) }
  }, [histogram])

  const plotWidth = width - MARGIN.left - MARGIN.right
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom
  const step = plotWidth / slots.length
  const barWidth = Math.max(1, Math.min(MAX_BAR, step - GAP))
  const y = (value: number) => MARGIN.top + plotHeight - (value / max) * plotHeight
  const spanMs = slots.length * bucketMs

  const presentLevels = LEVELS.filter((level) => slots.some((slot) => (slot.counts[level] ?? 0) > 0))

  const slotAt = (event: PointerEvent<SVGRectElement>): number => {
    const box = event.currentTarget.getBoundingClientRect()
    const index = Math.floor(((event.clientX - box.left) / box.width) * slots.length)
    return Math.min(slots.length - 1, Math.max(0, index))
  }

  const commit = (a: number, b: number) => {
    const low = Math.min(a, b)
    const high = Math.max(a, b)
    onRangeChange(new Date(t0 + low * bucketMs).toISOString(), new Date(t0 + (high + 1) * bucketMs - 1).toISOString())
  }

  // Current filter range, drawn as a selection overlay (clamped to the plot).
  const range = useMemo(() => {
    const lo = from ? Date.parse(from) : t0
    const hi = to ? Date.parse(to) : t0 + spanMs
    const toX = (ms: number) => MARGIN.left + Math.min(1, Math.max(0, (ms - t0) / spanMs)) * plotWidth
    return from || to ? { x1: toX(lo), x2: toX(hi) } : null
  }, [from, to, t0, spanMs, plotWidth])

  const dragRange = drag
    ? {
        x1: MARGIN.left + Math.min(drag.anchor, drag.current) * step,
        x2: MARGIN.left + (Math.max(drag.anchor, drag.current) + 1) * step,
      }
    : null
  const selection = dragRange ?? range

  const hovered = hover !== null ? slots[hover] : null
  const ticks = [0, max / 2, max]
  const tickCount = Math.max(2, Math.min(6, Math.floor(plotWidth / 130)))
  const xTicks = Array.from({ length: tickCount }, (_, i) => {
    const fraction = i / (tickCount - 1)
    return { x: MARGIN.left + fraction * plotWidth, label: formatAxisTime(t0 + fraction * spanMs, tz, spanMs) }
  })

  return (
    <section aria-label="Timeline" className="border-b border-line bg-surface px-4 pt-2 pb-1">
      <div className="mb-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-2">
        <span className="font-medium text-ink">Entries over time</span>
        <ul className="flex flex-wrap gap-x-3" aria-label="Legend">
          {presentLevels.map((level) => (
            <li key={level} className="inline-flex items-center gap-1.5">
              <LevelSwatch level={level} />
              {level}
            </li>
          ))}
        </ul>
        <span className="text-muted">Drag across the bars to filter by time</span>
        <button
          type="button"
          onClick={() => setTableView((current) => !current)}
          aria-pressed={tableView}
          className="ml-auto rounded border border-line px-2 py-0.5 hover:bg-hover"
        >
          {tableView ? 'Show chart' : 'Show as table'}
        </button>
      </div>

      {tableView ? (
        <div className="max-h-44 overflow-auto">
          <table className="w-full text-left text-xs tabular-nums">
            <caption className="sr-only">Entries per time bucket and level</caption>
            <thead className="sticky top-0 bg-surface text-ink-2">
              <tr>
                <th className="py-1 pr-3 font-medium">Bucket start ({tz === 'utc' ? 'UTC' : 'local'})</th>
                {presentLevels.map((level) => (
                  <th key={level} className="py-1 pr-3 font-medium">
                    {level}
                  </th>
                ))}
                <th className="py-1 font-medium">Total</th>
              </tr>
            </thead>
            <tbody>
              {slots
                .filter((slot) => slot.total > 0)
                .map((slot) => (
                  <tr key={slot.start} className="border-t border-line">
                    <td className="py-0.5 pr-3">{formatTimestamp(new Date(slot.start).toISOString(), tz)}</td>
                    {presentLevels.map((level) => (
                      <td key={level} className="py-0.5 pr-3">
                        {formatCount(slot.counts[level] ?? 0)}
                      </td>
                    ))}
                    <td className="py-0.5">{formatCount(slot.total)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div ref={containerRef} className="relative">
          <svg
            width={width}
            height={HEIGHT}
            role="img"
            aria-label={`Histogram of ${formatCount(slots.reduce((sum, s) => sum + s.total, 0))} entries across ${slots.length} time buckets`}
          >
            <defs>
              <pattern id="unknown-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="2" height="4" fill="var(--lvl-unknown)" />
              </pattern>
            </defs>

            {ticks.map((tick) => (
              <g key={tick}>
                <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)} stroke="var(--line)" strokeWidth={1} />
                <text x={MARGIN.left - 6} y={y(tick)} textAnchor="end" dominantBaseline="middle" fontSize={10} fill="var(--muted)">
                  {formatCompact(Math.round(tick))}
                </text>
              </g>
            ))}
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(0)} y2={y(0)} stroke="var(--axis)" strokeWidth={1} />

            {selection && (
              <rect
                x={selection.x1}
                y={MARGIN.top}
                width={Math.max(2, selection.x2 - selection.x1)}
                height={plotHeight}
                fill="var(--accent)"
                opacity={0.12}
              />
            )}

            {slots.map((slot) => {
              const x = MARGIN.left + slot.index * step + (step - barWidth) / 2
              const levels = LEVELS.filter((level) => (slot.counts[level] ?? 0) > 0)
              let bottom = y(0)
              return (
                <g key={slot.start} opacity={hover !== null && hover !== slot.index ? 0.55 : 1}>
                  {levels.map((level, position) => {
                    const height = ((slot.counts[level] ?? 0) / max) * plotHeight
                    const top = bottom - height
                    const isTop = position === levels.length - 1
                    // The 2px surface gap sits between stacked segments; the baseline segment keeps its full height.
                    const drawnHeight = Math.max(1, position === 0 ? height : height - GAP)
                    const drawnTop = bottom - drawnHeight
                    bottom = top
                    const fill = level === 'UNKNOWN' ? 'url(#unknown-hatch)' : LEVEL_COLOR[level]
                    return isTop ? (
                      <path key={level} d={roundedTop(x, drawnTop, barWidth, drawnHeight, RADIUS)} fill={fill} />
                    ) : (
                      <rect key={level} x={x} y={drawnTop} width={barWidth} height={drawnHeight} fill={fill} />
                    )
                  })}
                </g>
              )
            })}

            {xTicks.map((tick, i) => (
              <text
                key={i}
                x={tick.x}
                y={HEIGHT - 6}
                fontSize={10}
                fill="var(--muted)"
                textAnchor={i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'}
              >
                {tick.label}
              </text>
            ))}

            {/* One transparent hit area over the whole plot: hover and drag-to-select work between bars too. */}
            <rect
              x={MARGIN.left}
              y={0}
              width={plotWidth}
              height={HEIGHT - MARGIN.bottom}
              fill="transparent"
              style={{ cursor: 'crosshair', touchAction: 'none' }}
              onPointerDown={(event) => {
                event.currentTarget.setPointerCapture(event.pointerId)
                const index = slotAt(event)
                setDrag({ anchor: index, current: index })
              }}
              onPointerMove={(event) => {
                const index = slotAt(event)
                setHover(index)
                setDrag((current) => (current ? { ...current, current: index } : current))
              }}
              onPointerUp={(event) => {
                if (drag) commit(drag.anchor, slotAt(event))
                setDrag(null)
              }}
              onPointerLeave={() => setHover(null)}
            />
          </svg>

          {hovered && hovered.total > 0 && (
            <div
              role="tooltip"
              className="pointer-events-none absolute z-10 rounded-md border border-line bg-surface px-3 py-2 text-xs shadow-md"
              style={{
                top: 0,
                left: Math.min(width - 190, Math.max(0, MARGIN.left + hovered.index * step + step / 2 - 90)),
                width: 180,
              }}
            >
              <p className="mb-1 font-medium">{formatTimestamp(new Date(hovered.start).toISOString(), tz)}</p>
              {LEVELS.filter((level) => (hovered.counts[level] ?? 0) > 0)
                .reverse()
                .map((level) => (
                  <p key={level} className="flex items-center justify-between gap-2 tabular-nums">
                    <span className="inline-flex items-center gap-1.5">
                      <LevelSwatch level={level} />
                      {level}
                    </span>
                    {formatCount(hovered.counts[level] ?? 0)}
                  </p>
                ))}
              <p className="mt-1 flex justify-between border-t border-line pt-1 font-medium tabular-nums">
                <span>Total</span>
                {formatCount(hovered.total)}
              </p>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
