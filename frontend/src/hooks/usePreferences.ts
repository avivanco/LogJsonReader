import { useCallback, useEffect, useState } from 'react'
import type { TimeZoneMode } from '../lib/time'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'logviewer.theme'
const TZ_KEY = 'logviewer.timezone'

function systemTheme(): Theme {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Light/dark theme: follows the OS until the user picks one, then remembers the choice. */
export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(() => {
    const stored = localStorage.getItem(THEME_KEY)
    return stored === 'light' || stored === 'dark' ? stored : systemTheme()
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  const toggle = useCallback(() => {
    setTheme((current) => {
      const next = current === 'dark' ? 'light' : 'dark'
      localStorage.setItem(THEME_KEY, next)
      return next
    })
  }, [])

  return [theme, toggle]
}

/** Whether timestamps are shown (and typed in date filters) in UTC or in the browser's local time zone. */
export function useTimeZone(): [TimeZoneMode, () => void] {
  const [tz, setTz] = useState<TimeZoneMode>(() => (localStorage.getItem(TZ_KEY) === 'local' ? 'local' : 'utc'))

  const toggle = useCallback(() => {
    setTz((current) => {
      const next = current === 'utc' ? 'local' : 'utc'
      localStorage.setItem(TZ_KEY, next)
      return next
    })
  }, [])

  return [tz, toggle]
}
