import { defineConfig } from '@playwright/test'

/**
 * End-to-end tests drive the real app, so start the backend first (it serves the UI too):
 *   cd backend && ./gradlew bootRun        (or: java -jar logviewer.jar)
 * then `npm run e2e`. Set E2E_BASE_URL to target another instance and E2E_BROWSER_CHANNEL (default: chromium bundled
 * with Playwright) to e.g. `msedge` or `chrome` to reuse an installed browser instead of downloading one.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  reporter: [['list']],
  outputDir: process.env.E2E_OUTPUT_DIR ?? 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    channel: process.env.E2E_BROWSER_CHANNEL || undefined,
    viewport: { width: 1360, height: 860 },
    trace: 'retain-on-failure',
  },
})
