# FitLens code map

Read this first, then `grep` for the exact code you need. Don't read whole folders.
Source root: `app/src/main/java/com/fitlens/companion/` (paths below are relative to it).
**Keep it current:** any build that adds, moves or renames a file, or changes a pattern below, updates this map in the
same commit.

Last updated: 1.0.62.

## How data flows

- **One SQLite database**, `fitlens.db`, opened by `data/Db.kt` (`SQLiteOpenHelper`). `Db.VERSION` is 14 (v5 added `workout_set.set_type` and `rpe`; v6 added `exercise_goal`, `exercise.weight_step` and `default_graph`, and `measurement.edited`; v7 added `saved_workout`, `saved_workout_exercise` and `saved_workout_set`; v8 added `routine`, `routine_day` and `workout_origin`; v9 added `workout_set.position` and the `set_position` trigger that gives each new set the next position; v10 added `workout_set.superset`, `saved_workout_exercise.superset` and the `set_superset` trigger that puts a new set into its exercise's group; v11 added `workout_set.done` for "mark sets complete", #19; v12 added `exercise.rest_seconds`, #15; v13 (#106) added `routine_day_exercise` and `routine_day_set`, copied every saved workout into the routine days that used it (unused ones became one-day workouts), re-pointed `workout_origin` at workout and day, and left the `saved_workout*` tables empty so older builds still open the database, #77; v14 added `exercise_comment`, one comment per exercise per date, #107). Schema changes
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
  (`weight(kg)`, `toKg(shown)`, `fmtWeight(kg)`, `weightUnit`). Since 1.0.55 (#60) the snapshot is built from one
  part per `Area` (`LibraryPart`, `SetPart`, `NotesPart`, `BodyPart`, `PhotoPart`, each with its own lookups); its
  public properties delegate to them, so screens don't see the split.
- **Writes:** workout data goes through `data/Workouts.kt`. Its private `write(areas) { w -> }` runs one transaction
  on `Dispatchers.IO`, then `Store.refresh(areas)` re-reads only those areas and shares the rest (default
  `Area.WORKOUT`; library-only writes pass `LIBRARY`, comments and times `NOTES`). Small set writes that don't replay
  PRs use `writeSets { w, scope -> }`: name the touched exercises or dates in `scope` (before a delete, while the rows
  exist) and `Store.refreshSets` re-reads just those and merges them (`mergeSets`). Photo and measurement writes live
  in `Store` and refresh `PHOTOS` / `BODY`; `Goals` and `Routines` refresh `LIBRARY`; a preference change calls
  `Store.refresh()` (no read). `Store.reload()` (everything, preferences included) is for start-up, imports and restores.
  **A new write must name every area it changes**, or the screen shows stale data.
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
| `Models.kt` | Row types (`Category`, `Exercise`, `SetRow` with `setType` and `rpe`, `MeasurementDef`, `MRecord`, `Photo`, `WorkoutTime`), `Sources`, `ExerciseTypes` (FitNotes ids 0–3 plus FitLens types 4–9, #14; `uses*` and `timeBased` drive fields, columns, graphs and records), `SetTypes` (W/D/F badges), `Effort` (RPE/RIR), `Poses`, `Dates` |
| `Store.kt` | `Area`, the snapshot parts and `mergeSets` (#60); `Snapshot` (`exerciseComments`: date → exercise → comment, #107; `allMeasurements`; `usedMeasurements` leaves out those switched off, #27) and `Store` (`reload`, `refresh(areas)`, `refreshSets`, one `Mutex` for snapshot updates; photo and measurement writes: `addManualRecord`, `updateRecord`, `setMeasurementEnabled`, `addStandardMeasurements`, custom metrics) |
| `Workouts.kt` | Categories, exercises and sets: add, update, delete, copy or move workouts (`copyWorkout` returns the new ids), workout comments, exercise comments (#107: `setExerciseComment`, `setExerciseComments` for Undo; copy, move, delete, `deleteHistory`, `deleteSets` and merges carry them), times, undo helpers (`addSets`, `deleteSets`, `moveSets`), `reorderCategories`, `logPlanned` (a workout day's sets, PR replay), `swapExercise` / `setExerciseOf`, `recalculatePrs`, `deleteHistory` (range and/or exercises, skip rules, PR replay in one transaction), `mergeExercises` (#57: moves sets, goals and workout-day entries, re-points import rules and re-keys set skips, PR replay) |
| `Records.kt` | 1RM estimate (`factor`, `oneRepMax`, `weightFor`; `Formula` and `chosen()`, the user's formula, #42), rep maxes (`repMax`, superseding rule), `isNewRecord`, `Period` and `between` filters. `Workouts.recalculatePrs` replays history with it |
| `FitNotesImporter.kt` | `.fitnotes` import (merge-only), body CSV import, `ImportSummary` |
| `Backups.kt` | `.fitlens` export and restore (`writeArchive`, `unpack` (checks before anything live is touched), `installDatabase`), `exportForShare` (share sheet), `manualFileName` (timestamp setting), safety copy with Undo, automatic backups to a folder |
| `Analysis.kt` | The Analysis hub's numbers, pure Kotlin: period totals (`totals`, `Period`, `Metric`, `Filter`), breakdowns (`breakdown`, `windows`, `previousWindow`, `Span`, `Measure`), `percents` (largest remainder). Weeks start on `weekStart` (Monday until #7) |
| `Routines.kt` | Workouts (#106, FitNotes's routines; the code keeps the `Routine` names): `Routine` (name, notes, days), `RoutineDay` (a user-named day with its `PlannedExercise`s), `PlannedExercise` (`fill`: `FILL_LAST` copy previous, `FILL_PLANNED` predefined sets, `FILL_NONE`; superset), `PlannedSet`, `WorkoutOrigin` (which workout and day a logged date came from); `load`, `save` (keeps day ids, rewrites each day's exercises), `delete`, `copy`, `addDay`, `deleteDay`, `setDayExercises`, `nextDay`, `dayById`, `resolve` (the sets an exercise adds on a date; blank predefined values copy last time), `fromDate`, `describe`, `fillLabel`, and the v13 step `migrateSavedWorkouts`. Logging goes through `Workouts.logPlanned`, which writes the origin |
| `Goals.kt` | Exercise goals (#25): `ExerciseGoal`, `GoalKinds` (labels, units, `progress`), `Goals` writes (save, delete, reorder) |
| `CsvExport.kt` | Workouts and body data as CSV (#31, exercise comment column #107): documented columns, RFC 4180 quoting, counts for previews |
| `AutoBackup.kt` | Scheduled backups (`BackupWorker`, JobScheduler), status and failure notifications |
| `BackupSync.kt` | FitNotes backup-folder auto-sync |
| `PhotoImporter.kt` | Photo import, date detection (EXIF, media store, file name, modified), duplicate hashing |
| `StarterLibrary.kt` | Optional starter exercise library |
| `StandardMeasurements.kt` | The standard body measurements (#27) and `missing`; added by `Store.addStandardMeasurements` from setup and the Measurements screen |
| `Settings.kt` | The typed settings layer: `DeviceSettings` (DataStore), `PortableSettings` (`meta`), the one-time move from `meta` |

### `ui/`
| File | Owns |
| --- | --- |
| `MainActivity.kt` | `Screen` (sealed destinations), `Nav` (a simple back stack: `push` / `pop` / `home(date)`, `atHome`), `AppRoot` and `LocalNavBack`. FitNotes-style navigation (#79): no bottom bar; the day log (`Screen.Day`) is the root and every other screen is pushed on it. Shared files open their Settings page (`openSettingsPage`) |
| `SettingsScreen.kt` | Settings (#86): a search field over `CATALOGUE` (every setting, its keywords and page; a new row belongs there too), `SettingsSection` groups, "Run setup again", and the sub-screens: Backups, FitNotes import and Data tools (cards), Units & display, Workout & logging, Rest timer (`RestPage`: `RestLengthStepper` plus `RestAlertOptions`) and Personal records (row pages, edge to edge) |
| `DataToolsScreen.kt` | Settings → Data tools: CSV export (save or share) and Delete workout history (safety copy first), each with its own `RangeChips` range |
| `SetupScreen.kt` | The guided first-run setup (#29): welcome/restore, units, automatic backups, FitNotes import, photos, starter library. Shown once when a phone has no data (`DeviceSettings.setupDone`, checked in `AppRoot`) |
| `GoalsTab.kt` | An exercise's Goals tab and goal editor; `goalKindForGraph` and `goalShown` for goal lines on its graphs |
| `MeasurementGoals.kt` | Body tab: `MeasurementGoalSheet`, `MeasurementOrderSheet`, `changeColour` (by goal direction), `goalText` |
| `SetMarks.kt` | `setMarks(set)`: a set's type badge and effort text (shown and spoken), following the settings. Every set list uses it |
| `Theme.kt` | `Brand` colours, `ChartColors`, `Spacing`, `FitShapes`, `Motion`, `FitLensTheme`, `fitDatePickerColors` / `fitTimePickerColors` (#104). **The only place colours are defined** |
| `Components.kt` | Shared basics: `BackTopBar`, `PlainTopBar` (back arrow from `LocalNavBack`, for screens opened from the day log's menu), `GoldHairline`, `EmptyState`, `Dot`, `SectionTitle`, `UiEvents` / `AppResult` messages |
| `design/` | The redesign's building blocks (#79): `TopBar.kt` (`FitTopBar`), `Tabs.kt` (`FitTabRow`, `RangeChips`, `DateRangePickerDialog`), `Sheets.kt` (`FitSheet`, `ConfirmSheet`, `SearchablePicker`; sheet content keeps its own scroll so a list never drags its sheet closed, and the picker stays open while anything is ticked, #103), `Rows.kt` (`StatTile`, `ListRowWithMenu`), `SetViews.kt` (`StepperField`, `SetRow` with `SetCell` columns, `showIndex` and the `onComment` button, `SetCommentSheet`, `ExerciseCommentRow` (#107), `ExerciseCard`), `Icons.kt` (`FitIcons`: comment and alarm glyphs not in icons-core), `Glass.kt` (#102: `raisedGlass`, `recessedGlass`, `ambientBackdrop`, `GoldButton` (purple glass, gold rim and gold text, #104), `GlassOutlinedButton`), `DayNavigator.kt`, `Feedback.kt` (`UndoSnackbarHost`), `Adaptive.kt` (width buckets), `CompactControls.kt` (#115: `DropdownPill`, `OptionsMenu` with `ToggleOption`; use them instead of rows of chips), `SettingsRows.kt` (#86: `SettingsGroup`, `SettingsSwitchRow`, `SettingsChoiceRow` with its bottom-sheet picker, `SettingsActionRow`, `SettingsNote`; every settings page is built from these). New screens use these |
| `TimelineScreen.kt` | All days (every day with photos, measurements, workout), from the Calendar's top bar |
| `DayScreen.kt` | The day log, home (#81, #8): top bar (Calendar, +, the rest countdown while resting (#109), the ⋮ menu: day actions, then Analysis, Body tracker, Photos, Settings; #111, #106), `DayNavigator` and page swipe by calendar day, photo strip, body values card, summary and comment, `ExerciseCard`s, empty-day actions. Also `describeSet` (one-line "80 kg · 8 reps" for toasts, PDFs and summaries), `defOrder`, `AddMeasurementDialog` |
| `SetColumns.kt` | #101: `SetField`, `setFields` (an exercise's set columns, from its type plus anything recorded) and `setCells` (a set's values and units as `SetCell`s for `SetRow`, #112), `spokenDuration` |
| `SetEntry.kt` | The exercise screen (#82): `SetEntryScreen` (top bar: workout drawer, (i) exercise info (#110), records, rest timer; ⋮ has the calculators) with Track (logging and editing sets, Save / Clear, Update / Delete, a comment button per set, #108), History and Graph tabs in a `HorizontalPager`; `queue` opens exercises chosen together one after another; `page` picks the opening tab. `Screen.SetEntry.setId` opens Track with that set selected (`AppRoot` keys the screen on it). |
| `WorkoutEditorScreen.kt` | `WorkoutEditorScreen` (#106, `Screen.WorkoutEditor(id)`): name, notes, days as cards (+ Add exercise, day menu: rename, duplicate, move, copy to another workout, delete), exercise rows (sets, supersets, swap, remove, move); the ⋮ has Duplicate and Delete workout. `PlannedSetsSheet` (copy previous / predefined / none) and `planSummary` |
| `WorkoutSheets.kt` | The day log's workout sheets (#100, #106): `AddWorkoutSheet` (a workout's day or exercises chosen on the spot, review, `replace`), `SaveAsWorkoutSheet` (new workout, new day, or instead of a day), `logWorkoutDay` (logs a day with Undo, shared with the library's Log all), `exercisePickerItems` for `SearchablePicker`, `exerciseLine` |
| `WorkoutTools.kt` | Day log tools: `WorkoutClock` (a running timer is a `workout_time` start with no finish; `running`, `stop`), `rememberElapsed`, `WorkoutTimeSheet` (#12, time pickers), `ShareWorkoutSheet` (#11, text) |
| `WorkoutDrawer.kt` | The workout drawer (#85), ordering helpers (#70: `dayExercises`, `moveExercise`, `moveSet`; #85: `moveInOrder` keeps supersets together, `saveExerciseOrder`, the drawer's drag handle saves once on release) and supersets (#18: `displayOrder`, `supersetOf`, `supersetLetters`, `supersetMembers`; writes are `Workouts.groupExercises` / `ungroupExercise`) |
| `RestTimer.kt` | The rest timer (#20): `RestTimer` singleton (runs in `AppScope`), `RestSound` (the rest-over ringtone at FitLens's volume; the alert channel is silent), `REST_CHOICES`, `REST_MIN` / `REST_MAX`, `RestTimer.setLength` (a running rest's new length, #105), `rememberRest`, `RestTimerButton` (the top-bar alarm clock that becomes the countdown, #109), `RestLengthStepper` (#105, also in `ExerciseEditorSheet`), `RestAlertOptions` (auto-start, vibrate, sound, volume; shared with Settings → Rest timer, #86), `RestTimerSheet(exercise)` (edits the exercise's own `restSeconds` when it has one, #15), `rememberNotificationAsk` |
| `TimerService.kt` | Foreground service (type specialUse) with one ongoing notification for the rest and workout timers (system chronometer, action buttons), the "Rest over" alert; `refresh` on every timer change, `watch` (from `App`) follows the workout timer |
| `WorkoutEditing.kt` | Workout sheets (#84): `WorkoutCommentSheet`, `DeleteWorkoutSheet`, `CopyOrMoveWorkoutSheet`, `CopyPreviousWorkoutSheet`, each with Undo |
| `ExerciseLibrary.kt` | The exercise library (#83): category list, then a category's exercises (side by side on `WidthBucket.Expanded`), search, long-press multi-select; the title's workout switcher (#106: All exercises, each workout, Create new workout) and `RoutineDayList` (day cards with Log all); `ExerciseInfoSheet` (#110), `ExerciseEditorSheet`, `CategoryManagerSheet` (reorder), `CategoryEditorSheet`, `StarterLibraryDialog`, `categoryColour`, `MergeExerciseFlow` (#57: pick, confirm, safety copy, merge). `Screen.Library(date)` is also the exercise picker |
| `Calculators.kt` | Set calculator (`SetCalculatorSheet`: % of 1RM or a warm-up ramp) and plate calculator (`PlateCalculatorSheet`, `platesPerSide`, `BarEnd`; bar and plates in `PortableSettings`), #28, from the exercise screen's ⋮ menu |
| `ExerciseOverview.kt` | `ExerciseOverviewSheet` (#26): History, Graph, Records, Stats and Goals in one sheet, from the calendar's selected day and the day log |
| `ExerciseStats.kt` | `ExerciseStatsTab` (#24: tiles by period) and `OneRepMaxSheet` (#28: rep maxes and percentages). Stats tiles (`StatItem`) with a date open that day; Custom range via `DateRangePickerDialog` (#24). |
| `TrainingScreen.kt` | `ExerciseDetailScreen` (Records, Stats and Goals tabs, 1RM button), the shared `ExerciseGraphPane` and `ExerciseHistoryPane` (day totals and Copy to today, #22) used by the exercise screen and the overview, `graphLabels`, `e1rm`. Graph names are the `GRAPH_*` constants; `graphLabels` only ever appends (#15 stores the index), #22 added Max volume, Max weight for reps (`RepsForGraph`) and Personal records (`Records.recordProgress`); a `GraphType` with `series` builds the whole line. History set rows open `Screen.SetEntry(date, id, setId = …)`. |
| `AnalysisScreen.kt` | `AnalysisScreen` (top bar; + on Goals) and `AnalysisHub` (#90): tabs Workouts, Breakdown, Exercises, Goals, Records (`TAB_*`); Workouts tab (#51, bar totals; Duration as total or average per workout and `DurationPerWorkout`, #12), `AnalysisFilterChips`, `filterLabel`, `AnalysisNote` |
| `AnalysisTabs.kt` | Analysis → Exercises (`AnalysisExercisesTab`: exercise picker, then `ExerciseGraphPane` keyed by exercise) and Analysis → Goals (`AnalysisGoalsTab`: every goal by exercise, opens `Screen.ExerciseDetail(id, tab = 2)`, + picks an exercise then `GoalEditor`), #90 |
| `BreakdownTab.kt` | Analysis → Breakdown (#52): donut by category or exercise, period stepper, previous-period compare, stat tiles |
| `RecordsBoard.kt` | Analysis → Records (#54): 1RM–15RM grid across exercises, fixed first column and header sharing one horizontal `ScrollState` |
| `BodyScreen.kt` | Body tracker (#88): Track (latest value, change, goal; tap logs via `AddMeasurementDialog(initialName)`), History and Graph tabs. Also `RANGES` and `inRange` for charts |
| `Charts.kt` | Shared charts (#50): `LineChart` (several `LineSeries`, legend, trend, from zero, markers; the line is never broken, #116; `height` defaults to `graphHeight()`, about 45% of the screen, #115), `ChartSelection`, `ChartViewport` (time `from`/`to` and values `yFrom`/`yTo`, #96), `trendOf`, `rememberChartData` (off-main-thread data) |
| `ChartViews.kt` | `DonutChart` (percentages via `Analysis.percents`), `DonutLegend`, `FullScreenDonut` (legend beside it in landscape), `FullScreenChart` (pinch split by direction: across zooms time, up and down zooms values unless `valueZoom = false` for bar charts, #96; pan, reset, TalkBack actions, a `controls` slot; `detectAxisTransformGestures`), `GraphOptionChips` (one compact row, #115: `leading` dropdowns, the range as a `DropdownPill`, Trend, From zero and `extra` in an `OptionsMenu`, `trailing`), `ExpandGraphButton`, `ChartHint` |
| `CalendarScreen.kt` | FitNotes-style calendar (#87): month grid with swipe, category dots, selected day below (beside it on `WidthBucket.Expanded`, #87) with Open day (`nav.home(date)`), the filter bar and dimmed non-matching days (#9) |
| `CalendarFilter.kt` | The calendar filter (#9): `CalendarFilter` (conditions one set must meet, `days`, `describe`, `encode`/`decode` into `DeviceSettings.calendarFilter`) and `CalendarFilterSheet` |
| `PhotosScreen.kt`, `PhotoViewerScreen.kt` | Gallery, poses, review, viewer, compare, share |
| `SlideshowScreen.kt` | Slideshow and video options |
| `FitNotesCards.kt` | `FitNotesCards`: FitNotes backup import and the backup-folder sync, shown in Settings → FitNotes import (the Sync tab was removed in 1.0.23, #35). Photo import lives on the Photos screen and the day log |
| `BackupUi.kt` | `BackupsCard`, shown in Settings → Backups, with Save, Share and Restore and the file-name timestamp toggle |
| `FitNotesImportUi.kt`, `ImportActions.kt` | Import hosts and flows, `runBusy`, `AppScope` |
| `CustomMetrics.kt` | `CustomMetricEditor` (create or edit a custom measurement) |
| `MeasurementsScreen.kt` | `Screen.Measurements` (#88): every measurement with on/off, custom ones, the standard set |
| `MeasurementSheets.kt` | `MeasurementEntrySheet` (#27, #88): log a value, or edit/delete one logged by hand; `AddMeasurementDialog` in `DayScreen.kt` delegates to it |

### Other
| File | Owns |
| --- | --- |
| `report/PdfReport.kt` | The PDF progress report (dark or light), drawn on `android.graphics.pdf` |
| `video/FrameRenderer.kt`, `video/VideoExporter.kt` | Slideshow frames and MP4 export |
| `branding/` (repo root) | `fitlens-icon-source.png`, the owner's final icon artwork (use unedited for every icon), and `fitlens-icon-512.png` (store and README). The launcher icon is `res/mipmap-anydpi-v26/ic_launcher.xml`: the artwork placed in the safe zone (`mipmap-*/ic_launcher_foreground.png`) over `@color/ic_launcher_background` (#3A0D59, its own background). `character/`: the owner's close-up character icons, copied unedited to `res/drawable-*dpi/search_character.png` (24dp, `ui/design/SearchIcon.kt` `SearchFieldIcon`, the leading icon of every search field). `upper-body/`: the waist-up art (144 to 1024 px), resized for the notification large icon (`notification_character.png`, 64dp). The full-body art is also `res/drawable-nodpi/backdrop_character.png`, drawn faintly behind every screen by `ui/design/Backdrop.kt` (`CharacterBackdrop`, in `AppRoot`) |
| `App.kt` | `Application`: initialises `Store` and `Settings`, then starts `AutoBackup` |

## Conventions

- **Vocabulary** (owner decisions on #79 and #106): an *exercise* is one movement; a user-made *workout* (FitNotes's
  routine, `Routine` in code) is exercises grouped by days the user names; a *logged workout* is one date's sets. A
  control that adds one exercise says "Add exercise", never "workout".
- **No black text (#104):** filled controls carry gold text on a dark fill. Use `GoldButton` for primary actions and
  `errorContainer` / `onErrorContainer` for destructive ones; never fill a button or chip with the gold `primary`.

- **Screens** are `@Composable fun XScreen(snap: Snapshot, nav: Nav, …)`. To add a destination, add it to `Screen` in
  `MainActivity.kt` and to the `when` in `AppRoot`, and reach it from the day log's ⋮ menu (`DayScreen`) or another
  screen. There are no tabs: every screen but the day log is pushed and uses a back arrow. `nav.home(date)` returns
  to the day log. Navigation Compose isn't used yet (#37).
- **State:** `rememberSaveable` for UI choices, `remember(keys)` for derived lists. There are no ViewModels yet (#37).
  Launch writes with `AppScope` / `runBusy`, and report results through `UiEvents`.
- **Brand:** colours come from `MaterialTheme.colorScheme`, `Brand` or `LocalChartColors`. Never write `Color(0x…)`
  outside `Theme.kt`. Headings are serif, labels letter-spaced, dividers `GoldHairline`.
- **Glass depth (#102):** surfaces use `ui/design/Glass.kt`: `raisedGlass` for cards and tiles, `recessedGlass`
  for wells, `GoldButton` / `GlassOutlinedButton` instead of `Button` / `OutlinedButton` (destructive buttons with
  error colours stay plain `Button`). The page glow comes from `ambientBackdrop` on the root `Scaffold`, so screens
  and bars stay transparent rather than painting `Brand.Black`.
- **Sets are shown as columns (#101, #112):** list a day's or history's sets with `SetRow(cells = setCells(snap, fields,
  s))`, `fields = setFields(snap, exerciseId, sets)`: each value right-aligned with its unit, no heading row. Lists
  outside set entry pass `showIndex = false`. Keep "×" formulas out of set rows.
- **Stats use `snap.statSets` / `statSetsByExercise`**, which leave out warm-ups unless the setting counts them
  (#43). Lists and history use `sets`. New records, graphs or analysis must use the stat sets.
- **Order within a day** is `SetRow.position` (#70): the snapshot is sorted by date then position, and exercises follow
  their first set's position. Never order a day by set id.
- **Units:** store kg, and show values with `snap.weight(kg)` / `snap.fmtWeight(kg)` plus `snap.weightUnit`. Body
  values keep their own unit in the database; `BodyPart` shows weight units in `weightUnit` (`WeightUnits` in
  `Models.kt`), and `Store` converts typed values back before saving (#117). The plate list is converted on a unit
  change (`withPlatesConverted`).
- **Comments** explain why and cite the issue (`// … (#69)`), like the code around them.
- **Toolchain:** Kotlin 2.0.21, Compose with Material 3, `compileSdk` 35, `minSdk` 29. There's no local Android SDK,
  so CI is the compiler.
- **Tests (#40):** JVM unit tests with Robolectric in `app/src/test` (`./gradlew testDebugUnitTest`), run by CI before
  every release build; a failure stops the release and its names land in `errors.txt`. `data/DbMigrationTest.kt`
  builds older databases by hand (v1, v2, v12, v13; schemas in `OldSchemas.kt`) and opens them with `Db`;
  `FitNotesImporterTest` merges a synthetic FitNotes backup through `FitNotesImporter.merge`; `StoreMergeTest` checks `mergeSets` against a full reload (#60); `ui/ChartViewportTest` covers full-screen zoom; `BackupsTest` covers
  `Backups.writeArchive`, `unpack` and `installDatabase`. All use a plain `Application`, so `Store` and `Settings`
  don't start: test seams take a database or file, not the singletons. **Every database change adds an upgrade test**,
  and every change to the importer's rules or the archive format adds a case.

## Where things usually go

| Change | Touch |
| --- | --- |
| New workout data field | `Db.kt` (VERSION and `onUpgrade`), `Models.kt`, `Store.load`, `Workouts.kt`, and `Backups.kt` if it's a new table |
| New setting | A field in `DeviceSettings` (phone-only) or `PortableSettings` (travels in backups) in `data/Settings.kt`, with its key and default, then a row in the matching `SettingsSection` page |
| Records or 1RM logic | `data/Records.kt` only. Screens and the PDF call it |
| New graph | Build its points in `rememberChartData(keys) { … }`, draw with `LineChart` (no bar charts, #116) or `DonutChart`, add an `ExpandGraphButton` and a `FullScreenChart` (#96) with `GraphOptionChips` as its `controls`, and a `ChartHint` under it |
| New screen | `Screen` and `AppRoot` in `MainActivity.kt`, and a new `ui/XScreen.kt` built from `ui/design/` |
