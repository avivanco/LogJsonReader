import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Histogram } from '../../api/types'
import { Timeline } from './Timeline'

const histogram: Histogram = {
  start: '2026-10-15T00:00:00Z',
  end: '2026-10-15T00:02:00Z',
  bucketMillis: 60_000,
  buckets: [
    { start: '2026-10-15T00:00:00Z', counts: { INFO: 8, ERROR: 2 } },
    { start: '2026-10-15T00:02:00Z', counts: { WARN: 5 } },
  ],
}

describe('Timeline', () => {
  it('lists only the levels present in the legend', () => {
    render(<Timeline histogram={histogram} tz="utc" onRangeChange={vi.fn()} />)

    const legend = screen.getByRole('list', { name: 'Legend' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['INFO', 'WARN', 'ERROR'])
  })

  it('describes the chart for assistive technology', () => {
    render(<Timeline histogram={histogram} tz="utc" onRangeChange={vi.fn()} />)

    expect(screen.getByRole('img')).toHaveAccessibleName('Histogram of 15 entries across 3 time buckets')
  })

  it('offers a table view with the same numbers, skipping empty buckets', async () => {
    render(<Timeline histogram={histogram} tz="utc" onRangeChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Show as table' }))

    const table = screen.getByRole('table')
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3) // header + two non-empty buckets
    expect(within(rows[1]).getByText('2026-10-15 00:00:00.000')).toBeInTheDocument()
    expect(within(rows[1]).getByText('10')).toBeInTheDocument()
    // WARN count and the bucket total are both 5
    expect(within(rows[2]).getAllByText('5')).toHaveLength(2)
  })
})
