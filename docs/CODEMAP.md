# FitLens code map

Read this first, then `grep` for the exact code you need. Don't read whole folders.
Source root: `app/src/main/java/com/fitlens/companion/` (paths below are relative to it).
**Keep it current:** any build that adds, moves or renames a file, or changes a pattern below, updates this map in the
same commit.

Last updated: 1.0.29.

## How data flows

- **One SQLite database**, `fitlens.db`, opened by `data/Db.kt` (`SQLiteOpenHelper`). `Db.VERSION` is 6 (v5 added `workout_set.set_type` and `rpe`; v6 added `exercise_goal`, `exercise.weight_step` and `default_graph`, and `measurement.edited`). Schema changes
  bump it and add an `if (oldVersion < N)` block in `onUpgrade` that keeps every row. `.fitlens` restores of older
  backups go through the same upgrade.
- **Settings** (#38) go through `data/Settings.kt` only. Phone-only settings (`DeviceSettings`: folders, schedules,
  last import, safety copy, last result) live in DataStore and never travel in backups. The user's preferences
  (`PortableSettings`: units, logging (keep screen on, `autofillSource`, `autoSelectNext`), PR celebrations) live in
  `meta`, so they restore from backups (day log: `homeShowCategories`, `homeSetsShown`, #8). The old phone-only `meta` rows are deleted once DataStore has loaded, and
  again after a restore (`Settings.dropLegacyDeviceRows`, #98). Both are
  loaded once at start-up and held in memory: read `Settings.current()` / `currentPortable()`, observe
  `Settings.device` / `portable` in Compose, and change them with `updateDevice` / `updatePortable` (or
  `updateDeviceNow` from background work). The only other `meta` writer is the FitNotes importer's weight unit,
  inside its own transaction; `Store.reload()` re-reads the preferences after it.
- **Reads:** `data/Store.kt` loads everything into one immutable `Snapshot` and publishes it as
  `Store.snapshot: StateFlow<Snapshot?>`. Screens take `snap: Snapshot` as a parameter. `Snapshot` has the lookups
  (`exercises`, `categories`, `setsByExercise`, `setsByDate`, `photosByDate`, `recordsByName`) and unit helpers
  (`weight(kg)`, `toKg(shown)`, `fmtWeight(kg)`, `weightUnit`).
- **Writes:** workout data goes through `data/Workouts.kt`. Its private `write { w -> }` runs one transaction on
  `Dispatchers.IO`, then calls `Store.reload()`, so the UI updates on its own. Photo and measurement writes live in
  `Store` itself. Every write reloads the whole snapshot (#60 tracks making this incremental).
- **Ownership:** each row has `source` = `Sources.FITLENS` or `Sources.FITNOTES` (`data/Models.kt`). FitNotes
  imports merge and never overwrite FitLens rows. Editing or deleting an imported row records an `import_rule` so
  re-imports respect the change.
- **Dates** are ISO `yyyy-MM-dd` strings. Use `Dates` in `data/Models.kt` (`today()`, `parse`, `epochDay`, `long`,
  `medium`, `short`). ISO strings compare correctly as text.

## Files

### `data/`
| File | Owns |
| --- | --- |
| `Db.kt` | Schema, `VERSION`, `onUpgrade` migrations, `meta` get/set/`deleteMeta`, `Cursor` helpers (`str`, `dbl`, `int`, `lng`) |
| `Models.kt` | Row types (`Category`, `Exercise`, `SetRow` with `setType` and `rpe`, `MeasurementDef`, `MRecord`, `Photo`, `WorkoutTime`), `Sources`, `ExerciseTypes`, `SetTypes` (W/D/F badges), `Effort` (RPE/RIR), `Poses`, `Dates` |
| `Store.kt` | `Snapshot` and `Store` (load and reload, photo and measurement writes, custom metrics) |
| `Workouts.kt` | Categories, exercises and sets: add, update, delete, copy or move workouts (`copyWorkout` returns the new ids), comments, times, undo helpers (`addSets`, `deleteSets`, `moveSets`), `recalculatePrs`, `deleteHistory` (range and/or exercises, skip rules, PR replay in one transaction) |
| `Records.kt` | 1RM estimate (`factor`, `oneRepMax`, `weightFor`), rep maxes (`repMax`, superseding rule), `isNewRecord`, `Period` and `between` filters. `Workouts.recalculatePrs` replays history with it |
| `FitNotesImporter.kt` | `.fitnotes` import (merge-only), body CSV import, `ImportSummary` |
| `Backups.kt` | `.fitlens` export and restore, `exportForShare` (share sheet), `manualFileName` (timestamp setting), safety copy with Undo, automatic backups to a folder |
| `Analysis.kt` | The Analysis hub's numbers, pure Kotlin: period totals (`totals`, `Period`, `Metric`, `Filter`), breakdowns (`breakdown`, `windows`, `previousWindow`, `Span`, `Measure`), `percents` (largest remainder). Weeks start on `weekStart` (Monday until #7) |
| `Goals.kt` | Exercise goals (#25): `ExerciseGoal`, `GoalKinds` (labels, units, `progress`), `Goals` writes (save, delete, reorder) |
| `CsvExport.kt` | Workouts and body data as CSV (#31): documented columns, RFC 4180 quoting, counts for previews |
| `AutoBackup.kt` | Scheduled backups (`BackupWorker`, JobScheduler), status and failure notifications |
| `BackupSync.kt` | FitNotes backup-folder auto-sync |
| `PhotoImporter.kt` | Photo import, date detection (EXIF, media store, file name, modified), duplicate hashing |
| `StarterLibrary.kt` | Optional starter exercise library |
| `Settings.kt` | The typed settings layer: `DeviceSettings` (DataStore), `PortableSettings` (`meta`), the one-time move from `meta` |

### `ui/`
| File | Owns |
| --- | --- |
| `MainActivity.kt` | `Screen` (sealed destinations), `Nav` (a simple back stack: `push` / `pop` / `home(date)`, `atHome`), `AppRoot` and `LocalNavBack`. FitNotes-style navigation (#79): no bottom bar; the day log (`Screen.Day`) is the root and every other screen is pushed on it. Shared files open their Settings page (`openSettingsPage`) |
| `SettingsScreen.kt` | Settings (`SettingsSection` rows, grouped, plus "Run setup again") and its sub-screens: Backups, FitNotes import and Data tools (Data, backup & import), Units & display, Workout & logging, Personal records |
| `DataToolsScreen.kt` | Settings → Data tools: CSV export (save or share) and Delete workout history (safety copy first), each with its own `RangeChips` range |
| `SetupScreen.kt` | The guided first-run setup (#29): welcome/restore, units, automatic backups, FitNotes import, photos, starter library. Shown once when a phone has no data (`DeviceSettings.setupDone`, checked in `AppRoot`) |
| `GoalsTab.kt` | An exercise's Goals tab and goal editor; `goalKindForGraph` and `goalShown` for goal lines on its graphs |
| `MeasurementGoals.kt` | Body tab: `MeasurementGoalSheet`, `MeasurementOrderSheet`, `changeColour` (by goal direction), `goalText` |
| `SetMarks.kt` | `setMarks(set)`: a set's type badge and effort text (shown and spoken), following the settings. Every set list uses it |
| `Theme.kt` | `Brand` colours, `ChartColors`, `Spacing`, `FitShapes`, `Motion`, `FitLensTheme`. **The only place colours are defined** |
| `Components.kt` | Shared basics: `BackTopBar`, `PlainTopBar` (back arrow from `LocalNavBack`, for screens opened from the day log's menu), `GoldHairline`, `EmptyState`, `Dot`, `SectionTitle`, `UiEvents` / `AppResult` messages |
| `design/` | The redesign's building blocks (#79): `TopBar.kt` (`FitTopBar`), `Tabs.kt` (`FitTabRow`, `RangeChips`, `DateRangePickerDialog`), `Sheets.kt` (`FitSheet`, `ConfirmSheet`, `SearchablePicker`), `Rows.kt` (`StatTile`, `ListRowWithMenu`), `SetViews.kt` (`StepperField`, `SetRow`, `ExerciseCard`), `DayNavigator.kt`, `Feedback.kt` (`UndoSnackbarHost`), `Adaptive.kt` (width buckets). New screens use these |
| `TimelineScreen.kt` | All days (every day with photos, measurements, workout), from the day log's menu |
| `DayScreen.kt` | The day log, home (#81, #8): top bar (Calendar, +, the ⋮ menu to every screen), `DayNavigator` and page swipe by calendar day, photo strip, body values card, summary and comment, `ExerciseCard`s, empty-day actions. Also `describeSet`, `defOrder`, `AddMeasurementDialog` |
| `SetEntry.kt` | Logging and editing sets for one exercise on one day |
| `WorkoutEditing.kt` | Workout sheets (#84): `WorkoutCommentSheet`, `DeleteWorkoutSheet`, `CopyOrMoveWorkoutSheet`, `CopyPreviousWorkoutSheet`, each with Undo |
| `ExerciseLibrary.kt` | Exercise library, category manager, editors, `ExercisePickerDialog`, `categoryColour` |
| `TrainingScreen.kt` | Exercises (every exercise with history) and `ExerciseDetailScreen` (Graph, History and Records tabs), `e1rm` |
| `AnalysisScreen.kt` | `AnalysisScreen` and `AnalysisHub` (#90): Workouts tab (#51, bar totals), `AnalysisFilterChips`, `filterLabel`, `AnalysisNote` |
| `BreakdownTab.kt` | Analysis → Breakdown (#52): donut by category or exercise, period stepper, previous-period compare, stat tiles |
| `RecordsBoard.kt` | Analysis → Records (#54): 1RM–15RM grid across exercises, fixed first column and header sharing one horizontal `ScrollState` |
| `BodyScreen.kt` | Body measurements graphs and stats. Also `RANGES` and `inRange` for charts |
| `Charts.kt` | Shared charts (#50): `LineChart` (several `LineSeries`, legend, trend, from zero, gaps, markers), `ChartSelection`, `ChartViewport`, `trendOf`, `rememberChartData` (off-main-thread data) |
| `ChartViews.kt` | `BarChart` (trend, partial last bar), `DonutChart` (percentages via `Analysis.percents`), `FullScreenChart` (pinch, pan, reset, TalkBack actions, a `controls` slot), `GraphOptionChips` (range, Trend, From zero), `ExpandGraphButton`, `ChartHint` |
| `CalendarScreen.kt` | Month grid; a tap opens the day log on that date (`nav.home(date)`) |
| `PhotosScreen.kt`, `PhotoViewerScreen.kt` | Gallery, poses, review, viewer, compare, share |
| `SlideshowScreen.kt` | Slideshow and video options |
| `FitNotesCards.kt` | `FitNotesCards`: FitNotes backup import and the backup-folder sync, shown in Settings → FitNotes import (the Sync tab was removed in 1.0.23, #35). Photo import lives on the Photos screen and the day log |
| `BackupUi.kt` | `BackupsCard`, shown in Settings → Backups, with Save, Share and Restore and the file-name timestamp toggle |
| `FitNotesImportUi.kt`, `ImportActions.kt` | Import hosts and flows, `runBusy`, `AppScope` |
| `CustomMetrics.kt` | Custom metric dialogs |

### Other
| File | Owns |
| --- | --- |
| `report/PdfReport.kt` | The PDF progress report (dark or light), drawn on `android.graphics.pdf` |
| `video/FrameRenderer.kt`, `video/VideoExporter.kt` | Slideshow frames and MP4 export |
| `App.kt` | `Application`: initialises `Store` and `Settings`, then starts `AutoBackup` |

## Conventions

- **Screens** are `@Composable fun XScreen(snap: Snapshot, nav: Nav, …)`. To add a destination, add it to `Screen` in
  `MainActivity.kt` and to the `when` in `AppRoot`, and reach it from the day log's ⋮ menu (`DayScreen`) or another
  screen. There are no tabs: every screen but the day log is pushed and uses a back arrow. `nav.home(date)` returns
  to the day log. Navigation Compose isn't used yet (#37).
- **State:** `rememberSaveable` for UI choices, `remember(keys)` for derived lists. There are no ViewModels yet (#37).
  Launch writes with `AppScope` / `runBusy`, and report results through `UiEvents`.
- **Brand:** colours come from `MaterialTheme.colorScheme`, `Brand` or `LocalChartColors`. Never write `Color(0x…)`
  outside `Theme.kt`. Headings are serif, labels letter-spaced, dividers `GoldHairline`.
- **Stats use `snap.statSets` / `statSetsByExercise`**, which leave out warm-ups unless the setting counts them
  (#43). Lists and history use `sets`. New records, graphs or analysis must use the stat sets.
- **Units:** store kg, and show values with `snap.weight(kg)` / `snap.fmtWeight(kg)` plus `snap.weightUnit`.
- **Comments** explain why and cite the issue (`// … (#69)`), like the code around them.
- **Toolchain:** Kotlin 2.0.21, Compose with Material 3, `compileSdk` 35, `minSdk` 29. There's no local Android SDK,
  so CI is the compiler. There are no tests yet (#40).

## Where things usually go

| Change | Touch |
| --- | --- |
| New workout data field | `Db.kt` (VERSION and `onUpgrade`), `Models.kt`, `Store.load`, `Workouts.kt`, and `Backups.kt` if it's a new table |
| New setting | A field in `DeviceSettings` (phone-only) or `PortableSettings` (travels in backups) in `data/Settings.kt`, with its key and default, then a row in the matching `SettingsSection` page |
| Records or 1RM logic | `data/Records.kt` only. Screens and the PDF call it |
| New graph | Build its points in `rememberChartData(keys) { … }`, draw with `LineChart` / `BarChart` / `DonutChart`, add an `ExpandGraphButton` and a `FullScreenChart` (#96) with `GraphOptionChips` as its `controls`, and a `ChartHint` under it |
| New screen | `Screen` and `AppRoot` in `MainActivity.kt`, and a new `ui/XScreen.kt` built from `ui/design/` |
