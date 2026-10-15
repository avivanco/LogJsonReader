import { expect, test, type Page } from '@playwright/test'

// 400 INFO, 200 DEBUG, 200 WARN, 200 ERROR lines one second apart, plus one non-JSON line.
const LEVELS = ['INFO', 'INFO', 'DEBUG', 'WARN', 'ERROR']
const fixture = [
  ...Array.from({ length: 1000 }, (_, i) =>
    JSON.stringify({
      '@timestamp': new Date(Date.UTC(2026, 9, 15, 0, 0, i)).toISOString(),
      level: LEVELS[i % 5],
      thread_name: `worker-${i % 2}`,
      logger_name: `com.acme.Service${i % 3}`,
      message: `event number ${i}`,
      trace_id: `trace-${i % 50}`,
      username: `user${i % 4}`,
      ...(i % 5 === 4 ? { stack_trace: 'java.lang.IllegalStateException: boom\n\tat com.acme.Service.run(Service.java:42)' } : {}),
    }),
  ),
  'this line is not json',
].join('\n')

async function openFixture(page: Page) {
  await page.goto('/')
  await page.getByLabel('Log file').setInputFiles({
    name: 'server.log',
    mimeType: 'text/plain',
    buffer: Buffer.from(fixture),
  })
  await expect(page.getByText('1,001 entries')).toBeVisible()
  await expect(page.getByRole('status')).toHaveCount(0) // ingestion progress is gone once loaded
}

const matching = (page: Page) => page.getByText(/matching entries/).locator('strong')

test('upload, filter by severity, inspect an entry and follow its trace', async ({ page }) => {
  await openFixture(page)

  await expect(page.getByText('Logstash / ECS')).toBeVisible()
  await expect(matching(page)).toHaveText('1,001')
  await expect(page.getByRole('button', { name: /ERROR\s*200/ })).toBeVisible()

  // Severity filter narrows the table and the count, and stays reflected in the URL.
  await page.getByRole('button', { name: /^ERROR/ }).click()
  await expect(matching(page)).toHaveText('200')
  await expect(page).toHaveURL(/level=ERROR/)
  await expect(page.getByRole('row').nth(1)).toContainText('event number 4')

  // Open the first error: details show the stack trace and the attributes.
  await page.getByRole('row').nth(1).click()
  const drawer = page.getByRole('complementary', { name: /Entry 5/ })
  await expect(drawer).toContainText('IllegalStateException')
  await expect(drawer).toContainText('user0')
  await expect(page).toHaveURL(/e=5/)

  // Pivot to the whole trace: the error filter is kept, so only errors of that trace remain.
  await drawer.getByRole('button', { name: 'Show whole trace' }).click()
  await expect(page.getByRole('list', { name: 'Active filters' })).toContainText('trace: trace-4')
  await expect(matching(page)).toHaveText('20')

  await page.keyboard.press('Escape')
  await expect(drawer).toBeHidden()

  await page.getByRole('button', { name: 'Clear all filters' }).click()
  await expect(matching(page)).toHaveText('1,001')
})

test('full-text search, newest-first ordering and shareable links', async ({ page }) => {
  await openFixture(page)

  await page.getByLabel('Search').fill('event number 999')
  await expect(matching(page)).toHaveText('1')

  await page.getByLabel('Search').fill('')
  await page.getByLabel('Order').selectOption('desc')
  // Newest first: the non-JSON last line (keeps the last timestamp, highest line number) leads.
  await expect(page.getByRole('row').nth(1)).toContainText('this line is not json')

  // A copied link reopens the very same view.
  const url = page.url()
  expect(url).toContain('sort=desc')
  await page.goto(url)
  await expect(page.getByLabel('Order')).toHaveValue('desc')
  await expect(matching(page)).toHaveText('1,001')
})

test('time range from the timeline brush and the table view of the chart', async ({ page }) => {
  await openFixture(page)

  const plot = page.getByRole('img', { name: /Histogram of 1,001 entries/ })
  const box = (await plot.boundingBox())!
  // Drag across the first third of the plot area.
  await page.mouse.move(box.x + 60, box.y + 40)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width * 0.3, box.y + 40, { steps: 5 })
  await page.mouse.up()

  await expect(page).toHaveURL(/from=/)
  const filtered = Number((await matching(page).innerText()).replace(/,/g, ''))
  expect(filtered).toBeGreaterThan(0)
  expect(filtered).toBeLessThan(1001)

  await page.getByRole('button', { name: 'Show as table' }).click()
  await expect(page.getByRole('table')).toContainText('Total')
})

test('theme toggle switches between light and dark', async ({ page }) => {
  await openFixture(page)

  const before = await page.locator('html').getAttribute('data-theme')
  await page.getByRole('button', { name: /Switch to/ }).click()
  const after = await page.locator('html').getAttribute('data-theme')
  expect(after).not.toBe(before)
})
