import { useCallback } from 'react'
import { UploadPanel } from './features/upload/UploadPanel'
import { Workspace } from './features/workspace/Workspace'
import { EMPTY_STATE, useAppState } from './hooks/useAppState'
import { useTheme, useTimeZone } from './hooks/usePreferences'
import type { FilterState } from './lib/filters'

export default function App() {
  const [state, update] = useAppState()
  const [theme, toggleTheme] = useTheme()
  const [tz, toggleTz] = useTimeZone()

  const changeFilter = useCallback(
    (updater: (previous: FilterState) => FilterState) =>
      // The open entry is fetched by its line number, so it stays valid (and open) while the filter changes.
      update((previous) => ({ ...previous, filter: updater(previous.filter) })),
    [update],
  )
  const selectEntry = useCallback(
    (seq: number | null) => update((previous) => ({ ...previous, entry: seq })),
    [update],
  )

  if (!state.sessionId) {
    return (
      <UploadPanel
        onOpened={(session) => update({ ...EMPTY_STATE, sessionId: session.id }, { push: true })}
      />
    )
  }

  return (
    <Workspace
      key={state.sessionId}
      sessionId={state.sessionId}
      filter={state.filter}
      entry={state.entry}
      tz={tz}
      theme={theme}
      onFilterChange={changeFilter}
      onSelectEntry={selectEntry}
      onToggleTz={toggleTz}
      onToggleTheme={toggleTheme}
      onOpenAnother={() => update(EMPTY_STATE, { push: true })}
    />
  )
}
