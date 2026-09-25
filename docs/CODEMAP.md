# FitLens code map

Read this first, then `grep` for the exact code you need. Don't read whole folders.
Source root: `app/src/main/java/com/fitlens/companion/` (paths below are relative to it).
**Keep it current:** any build that adds, moves or renames a file, or changes a pattern below, updates this map in the
same commit.

Last updated: 1.0.22.

## How data flows

- **One SQLite database**, `fitlens.db`, opened by `data/Db.kt` (`SQLiteOpenHelper`). `Db.VERSION` is 4. Schema changes
  bump it and add an `if (oldVersion < N)` block in `onUpgrade` that keeps every row. `.fitlens` restores of older
  backups go through the same upgrade.
- **Settings** (#38) go through `data/Settings.kt` only. Phone-only settings (`DeviceSettings`: folders, schedules,
  last import, safety copy, last result) live in DataStore and never travel in backups. The user's preferences
  (`PortableSettings`: units, logging, PR celebrations) live in `meta`, so they restore from backups. Both are
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
| `Db.kt` | Schema, `VERSION`, `onUpgrade` migrations, `meta` get/set, `Cursor` helpers (`str`, `dbl`, `int`, `lng`) |
| `Models.kt` | Row types (`Category`, `Exercise`, `SetRow`, `MeasurementDef`, `MRecord`, `Photo`, `WorkoutTime`), `Sources`, `ExerciseTypes`, `Poses`, `Dates` |
| `Store.kt` | `Snapshot` and `Store` (load and reload, photo and measurement writes, custom metrics) |
| `Workouts.kt` | Categories, exercises and sets: add, update, delete, copy or move workouts, comments, times, undo re-adds, `recalculatePrs` |
| `Records.kt` | 1RM estimate (`factor`, `oneRepMax`, `weightFor`), rep maxes (`repMax`, superseding rule), `isNewRecord`, `Period` and `between` filters. `Workouts.recalculatePrs` replays history with it |
| `FitNotesImporter.kt` | `.fitnotes` import (merge-only), body CSV import, `ImportSummary` |
| `Backups.kt` | `.fitlens` export and restore, safety copy with Undo, automatic backups to a folder |
| `AutoBackup.kt` | Scheduled backups (`BackupWorker`, JobScheduler), status and failure notifications |
| `BackupSync.kt` | FitNotes backup-folder auto-sync |
| `PhotoImporter.kt` | Photo import, date detection (EXIF, media store, file name, modified), duplicate hashing |
| `StarterLibrary.kt` | Optional starter exercise library |
| `Settings.kt` | The typed settings layer: `DeviceSettings` (DataStore), `PortableSettings` (`meta`), the one-time move from `meta` |

### `ui/`
| File | Owns |
| --- | --- |
| `MainActivity.kt` | `Screen` (sealed destinations), `Nav` (a simple back stack: `push` / `pop`), `AppRoot` with the bottom tabs and `LocalOpenSettings` |
| `SettingsScreen.kt` | Settings (`SettingsSection` rows) and its sub-screens: Backups, Import & sync, Units & display, Personal records |
| `Theme.kt` | `Brand` colours, `ChartColors`, `Spacing`, `FitShapes`, `Motion`, `FitLensTheme`. **The only place colours are defined** |
| `Components.kt` | Shared basics: `BackTopBar`, `PlainTopBar` (shows the Settings gear), `GoldHairline`, `EmptyState`, `Dot`, `SectionTitle`, `UiEvents` / `AppResult` messages |
| `design/` | The redesign's building blocks (#79): `TopBar.kt` (`FitTopBar`), `Tabs.kt` (`FitTabRow`, `RangeChips`, `DateRangePickerDialog`), `Sheets.kt` (`FitSheet`, `ConfirmSheet`, `SearchablePicker`), `Rows.kt` (`StatTile`, `ListRowWithMenu`), `SetViews.kt` (`StepperField`, `SetRow`, `ExerciseCard`), `DayNavigator.kt`, `Feedback.kt` (`UndoSnackbarHost`), `Adaptive.kt` (width buckets). New screens use these |
| `TimelineScreen.kt` | Log tab (every day with photos, measurements, workout) |
| `DayScreen.kt` | One day: photos, measurements, the workout and where logging starts. Also `describeSet`, `defOrder` |
| `SetEntry.kt` | Logging and editing sets for one exercise on one day |
| `WorkoutEditing.kt` | Workout comment, delete, copy or move dialogs |
| `ExerciseLibrary.kt` | Exercise library, category manager, editors, `ExercisePickerDialog`, `categoryColour` |
| `TrainingScreen.kt` | Training tab and `ExerciseDetailScreen` (Graph, History and Records tabs), `e1rm` |
| `BodyScreen.kt` | Body measurements graphs and stats. Also `RANGES` and `inRange` for charts |
| `Charts.kt` | Shared charts (#50): `LineChart` (several `LineSeries`, legend, trend, from zero, gaps, markers), `ChartSelection`, `ChartViewport`, `trendOf`, `rememberChartData` (off-main-thread data) |
| `ChartViews.kt` | `BarChart`, `DonutChart`, `FullScreenChart` (pinch, pan, reset, TalkBack actions), `ExpandGraphButton`, `ChartHint` |
| `CalendarScreen.kt` | Month grid |
| `PhotosScreen.kt`, `PhotoViewerScreen.kt` | Gallery, poses, review, viewer, compare, share |
| `SlideshowScreen.kt` | Slideshow and video options |
| `SyncScreen.kt` | Sync tab: `FitNotesCards` (also used by Settings → Import & sync) and photo import. #35 folds it into Settings |
| `BackupUi.kt` | `BackupsCard`, shown in Settings → Backups |
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
  `MainActivity.kt` and to the `when` in `AppRoot`. Navigation Compose isn't used yet (#37).
- **State:** `rememberSaveable` for UI choices, `remember(keys)` for derived lists. There are no ViewModels yet (#37).
  Launch writes with `AppScope` / `runBusy`, and report results through `UiEvents`.
- **Brand:** colours come from `MaterialTheme.colorScheme`, `Brand` or `LocalChartColors`. Never write `Color(0x…)`
  outside `Theme.kt`. Headings are serif, labels letter-spaced, dividers `GoldHairline`.
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
| New graph | Build its points in `rememberChartData(keys) { … }`, draw with `LineChart` / `BarChart` / `DonutChart`, add an `ExpandGraphButton` and a `FullScreenChart` (#96), and a `ChartHint` under it |
| New screen | `Screen` and `AppRoot` in `MainActivity.kt`, and a new `ui/XScreen.kt` built from `ui/design/` |
