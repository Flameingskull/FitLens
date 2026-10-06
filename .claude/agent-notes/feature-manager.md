# feature-manager notes

What earlier runs learned that isn't obvious from the code or `docs/CODEMAP.md`. One dated line each; delete lines
that stop being true. The repository is public: no personal data, secrets or `FIL.n` keys.

- 2026-09-25: No local Android SDK. CI is the compiler, so check imports, named arguments and `@OptIn`s by hand.
  1.0.17 failed on a missing experimental opt-in for the Material 3 top bar.
- 2026-09-25: Every workout write goes through `Workouts.write`, which reloads the whole `Snapshot`. Don't reload again
  from the UI.
- 2026-09-25: 1RM and PR logic lives only in `data/Records.kt` (#23). 1.0.19 and 1.0.20 shipped everything except
  the PR celebration, which shipped in 1.0.21 (#23 closed).
- 2026-09-25: PR rule: a set is a PR when it's strictly heavier than every earlier set of at least as many reps
  (ties aren't PRs). New sets get it on save (`Workouts.addSet`); `Workouts.recalculatePrs` replays history.
- 2026-09-25: Settings (1.0.21, #38): never call `Db.getMeta` / `setMeta` for a setting; use `data/Settings.kt`.
  The phone-only `meta` rows left for downgrades are cleaned up by #98. The importer's `weight_unit` write is the
  one deliberate exception.
- 2026-09-25: The name `Settings` clashes with `androidx.compose.material.icons.filled.Settings`. Don't import both
  in one file; `FitTopBar(onSettings = …)` already draws the gear.
- 2026-09-25: Charts (1.0.22, #50): every graph uses `ui/Charts.kt` / `ui/ChartViews.kt`, with colours from
  `LocalChartColors.palette` via `seriesColor(i)`. `FrameRenderer` has its own unrelated `ChartSeries` (video), so
  don't reuse that name. #96 still needs Y-axis zoom and the buttons on the redesign's future graph screens.
- 2026-09-25: Cleaning `meta` rows that DataStore's one-time migration copies must happen *after* DataStore loads
  (1.0.23, #98), never in `Db.onUpgrade`: the upgrade runs first, so a user jumping from 1.0.20 would lose them.
  Data-only clean-ups like this don't need a `Db.VERSION` bump. The Sync tab is gone (#35); FitNotes import lives in
  Settings → FitNotes import (`ui/FitNotesCards.kt`), and a new full-screen graph passes `GraphOptionChips` as
  `controls`.
- 2026-09-25: Shared files for the share sheet go in `cacheDir/exports` (the only FileProvider path) and are
  sent with `shareFile` (`ui/PhotoViewerScreen.kt`). Delete the previous file of the same kind first (1.0.24).
- 2026-09-25: Anything that deletes user data in bulk takes `Backups.safetyCopy` first and aborts if it fails, so
  Settings → Backups → Undo can reverse it (1.0.24, #32). First-run UI keys off `DeviceSettings.setupDone`.
- 2026-09-25: Analysis maths lives in `data/Analysis.kt` (pure Kotlin, unit-testable); screens only format. A
  `@Composable` (e.g. `categoryColour`) can't be called inside `remember { }`; build colours from the raw Int there.
- 2026-09-26: A value the user sets in FitLens on a FitNotes-owned row (e.g. a measurement's goal or order) needs a
  marker (`measurement.edited`) that the importer checks, or the next import silently overwrites it (1.0.28).
- 2026-09-26: Navigation is FitNotes-style since 1.0.29 (#79, #81): no bottom bar, `Screen.Day` is the root, and new
  destinations go in the day log's ⋮ menu (`DayScreen`), not a tab. `PlainTopBar` takes its back arrow from
  `LocalNavBack`. Workout dialogs are sheets in `ui/WorkoutEditing.kt`; undo a copy with the ids `copyWorkout`
  returns (`Workouts.deleteSets`). Still open from bundle 3: workout time (#12), share (#11) and the rest-timer sheet.
- 2026-09-26: Vocabulary (owner decision on #79): exercise = one movement; workout = a group of exercises with
  prescribed sets (saved workout #100, logged workout = one date); routine = saved workouts split into user-named days
  (#21, rewritten on top of #100). Write issues in these terms, and never label a single-exercise action as a workout.
- 2026-09-26: 1.0.32 (#83, #82 partly): + opens `Screen.Library(date)`, which is the picker too; tapping an exercise
  replaces the library with `Screen.SetEntry` so Back returns to the day log. The exercise screen's History and Graph
  are `ExerciseGraphPane` / `ExerciseHistoryPane` in `ui/TrainingScreen.kt`. A day only shows an exercise once it has
  a set, so "adding several exercises" is a queue until saved workouts (#100) give exercises planned sets. Still open:
  #82 needs the rest timer (#20); #83 needs the unit override (#7) and merge (#57) in the editor and overflow.
- 2026-09-26: Saved workouts (1.0.33, #100) are database v7 (`data/SavedWorkouts.kt`). Adding one logs every set at
  once (FitNotes "Log All") via `Workouts.logPlanned`, so it undoes with `deleteSets`. Routines (#21) should reference
  `saved_workout` ids per day rather than hold exercises. Still missing: a logged day doesn't record which saved
  workout it came from (needed for "next day" suggestions and "swap for good" from the day log).
- 2026-09-26: Routines (1.0.34, #21) are database v8 (`data/Routines.kt`). The library title is the routine switcher
  (`PortableSettings.lastRoutineId`, also used by Add workout). `workout_origin` (one row per date) drives the
  next-day suggestion; `deleteWorkout` clears it and `moveWorkout` moves it. `Screen.Routines` and
  `Screen.SavedWorkouts` share names with the data objects, so always write `Screen.X` for the destination.
- 2026-09-26: 1.0.35: workout timer = `workout_time` row with a start and blank finish (`WorkoutClock`); rest timer is
  the in-app `RestTimer` singleton. Still open: a foreground service + notification so both survive the screen off
  (#12, #20), per-exercise rest length (#15), share as image (#11). Settings `workoutTimerAuto`, `restSeconds`,
  `restAutoStart`, `restVibrate` are portable (`meta`). The manifest now has VIBRATE.
- 2026-09-26: 1.0.36: timers survive the screen off via `ui/TimerService.kt` (FGS type specialUse; never start it from
  the background, `refresh` updates a running instance instead). README refreshed at 1.0.36, next due 1.0.41.
- Anything that re-points data from one exercise id to another (merge, #57) must also re-point `import_rule`
  exercise links and re-key `set` skip rules (their key starts with the exercise id), or the next FitNotes import
  brings back duplicates or deleted sets. `Workouts.mergeExercises` is the reference.
- A left-edge swipe to open a drawer conflicts with Android's gesture-navigation back swipe. Open drawers from a
  button instead (1.0.42, #17).
- A notification channel's sound can't be changed once created. To change it, create a channel with a new id and
  delete the old one (`TimerService`, 1.0.44). The rest-over sound is played in-app by `RestSound` so its volume applies.
- `categoryColour` and `exercisePickerItems` are `@Composable`: never call them inside `remember { }` or
  `rememberChartData { }` (1.0.43 CI failure).
- 1.0.45: every e1RM goes through `Records.factor(reps, formula = chosen())`; never inline a formula. Panes reused in
  a `ModalBottomSheet` must not sit inside `FitSheet` (its content scrolls, so a LazyColumn pane would crash);
  `ExerciseOverviewSheet` uses `ModalBottomSheet` directly with a bounded height.
- 2026-09-28: Owner design direction: set rows as labelled columns driven by exercise type (#101), faked glass depth via shared `ui/design` modifiers, no blur/Haze (#102). Owner wants weight + time lifts (#14; asked if it should be a priority slice).
- 1.0.49 (#112, #108, #109, #105, #111): only `material-icons-core` is a dependency, so new glyphs go in
  `ui/design/Icons.kt` (`FitIcons`, Material path data) rather than adding icons-extended. Top-bar widgets that aren't
  plain icons (the rest countdown) go in `FitTopBar(trailing = …)`. A stepper that writes settings or the database
  debounces its commit and flushes on dispose (`RestTimerSheet`), so holding + doesn't reload the snapshot every step.
- 2026-09-29 (1.0.50, #106): saved workouts and routines are one model, database v13. A workout is `Routine` in code
  (`data/Routines.kt`), its days own their exercises (`routine_day_exercise`, `routine_day_set`); `SavedWorkouts.kt`,
  `Screen.SavedWorkouts`, `Screen.Routines` and `Screen.RoutineEditor` are gone (use `Screen.WorkoutEditor(id)`). The
  old `saved_workout*` tables stay empty rather than dropped, because older builds must still open the database (#77):
  never drop a table in a migration.
- 2026-09-29 (1.0.52, #107, #40 slice): exercise comments live in `exercise_comment` (v14), keyed by date and
  exercise, FitLens-only (no skip rules). Any new path that copies, moves or deletes sets must carry them, and its
  Undo must restore them (`Workouts.setExerciseComments`). JVM tests now exist (`app/src/test`, Robolectric, run in
  CI before the release build): every database change adds an upgrade test to `DbMigrationTest`.
- 2026-09-29 (1.0.54, #40 closed): tests cover migrations (v1, v2, v12, v13), the FitNotes merge and backup/restore.
  Release runs share one concurrency group and skip a commit that already has a `build-*` tag, after one push
  published 1.0.52 and 1.0.53 twice. A release's number is run number + 3 and can jump if a run is used up; read the
  actual release name from `gh release list` after the build rather than assuming latest + 1.
- 1.0.54 (#60 slice, #96): writes no longer reload everything. `Workouts.write(areas)` refreshes only the named
  `Area`s; set-only writes that don't replay PRs use `writeSets` and name their exercises or dates in the scope
  (look up a set's exercise *before* deleting it). A write that forgets an area leaves that part of the screen stale,
  so name every area a new write touches. Still open on #60: per-area flows so unrelated screens don't recompose, and
  timing on a large real dataset. Full-screen graphs zoom values via `ChartViewport.yFrom`/`yTo`; bar charts pass
  `valueZoom = false`.
- 1.0.56 (#22 slice, #24 slice, #90, icon): graph names are `GRAPH_*` constants in `ui/TrainingScreen.kt`; add new
  graphs only at the end of `graphLabels`, because `exercise.default_graph` stores the index. Analysis has five tabs
  (`TAB_*` in `AnalysisScreen.kt`). The launcher icon is the owner's artwork, unedited (`branding/`); never redraw it.
  Left on #22: cardio pace/speed graphs (need #7) and share a graph as an image. Left on #24: pace tiles (#7).
- 1.0.58 (#86 slice, #87 slice, README refresh): settings pages use `ui/design/SettingsRows.kt`; a new setting adds a
  row there and an entry to `CATALOGUE` in `SettingsScreen.kt` so search finds it. Rest-timer options live once, in
  `RestAlertOptions`. `FitTopBar` needs `@OptIn(ExperimentalMaterial3Api::class)` on the calling composable (1.0.57
  CI failure). A failed CI run uses up its version number, so the README refresh is checked against the version the
  release actually gets. Left on #86: Backups / Import / Data tools pages on the rows, sub-screen transitions. Left
  on #87: share.
- 1.0.62 (#115 slice, #116, #117; owner chat requests): graphs size from the screen and use one compact control row
  (`GraphOptionChips` with `DropdownPill` / `OptionsMenu`); lines never break; no bar charts. Body values in kg or lbs
  are converted for display in `BodyPart` and back in `Store` writes, so stored values never change. Left on #115: the
  same compact-controls pass on every other screen.
- 1.0.94 (#37): Navigation Compose runs behind `Nav`. The `NavHost` has one destination, `Route(s)`, whose argument
  is the `Screen` as JSON, so no screen needs its own route or nav types. Never write to a stack directly: use
  `nav.replace(screen)` (same kind updates the entry in place, another kind pops and pushes). `nav.top` / `atHome`
  update synchronously after each call, so loops like `while (!nav.atHome && nav.top !is Screen.Day) nav.pop()` end.
  Screen state that must survive rotation goes in a `ScreenState` ViewModel (`ui/ScreenState.kt`).
- 1.0.96 (#46, #92): a stored option list or blob goes in `PortableSettings` as text with a plain-Kotlin codec that
  validates on read (`data/MediaPrefs.kt`, `SlideshowPrefs`), unit-tested in `src/test`. A screen that remembers its
  options saves a moment after the last change and on leaving, and only once something changed, so opening it never
  turns defaults into saved values. Inside `Modifier.semantics { }`, a local named `selected` shadows the semantics
  property: write `this.selected = …`. Icons missing from icons-core are added to `FitIcons` from the Material paths.
- 1.0.98 (#14): user-defined exercise types live in `ExerciseTypes.custom` (refreshed by `Store.loadLibrary`), so type checks need no snapshot. A custom metric's graph labels are "Best X" / "Total X"; metric names can't be weight, reps, distance or time, so labels never collide with built-in graphs.
- 1.0.101 (#41, #94): strings.xml needs `\'` and `\"` escaped (a bare apostrophe fails the resource merge). A settings
  row that can't be used stays visible with `disabledReason` rather than being hidden. A "reset" of preferences keeps
  what the user built (pins, comparisons, profile) and phone state (folders, schedules); Undo re-applies the old
  `PortableSettings` whole, which `withPlatesConverted` leaves alone because the plate list differs.
- 1.0.102 (#41, #94, #93): a Settings page whose preferences should reset together gets a `PreferenceGroup` and the
  section's `group`; add new fields of that page to `withGroupFrom` or they won't reset. Undo re-applies only the
  group's values, so a change made meanwhile on another page survives. Haptics go through `ui/design/Haptics.kt`
  (`confirm` for saves and ticks, `record` for PRs), never a raw `performHapticFeedback`. Moving many literals to
  strings.xml is quickest with a Python script written to a file (the shell mangles backslashes in heredocs).
- 1.0.104 (#94): exercise screen tabs, library and workout sheets moved to strings.xml. Source files mix CRLF and LF
  in the working tree: a replacement script must read with `newline=""`, match on LF and write back the original
  ending, or every line shows as changed. `GRAPH_*` labels and `metricGraphLabels` are stored keys (pinned graphs,
  chart kinds), so translate them only through a separate display mapping, never by changing the constants.
- 1.0.105 (#94): Analysis, Stats, Goals, the body tracker and chart controls moved to strings.xml. Data-layer enums keep their `label`; the UI maps them in `ui/AnalysisText.kt`. Making a helper `@Composable` (as `relativeDayLabel`) breaks any plain wrapper that calls it (`WorkoutDrawer.relativeLabel`): grep every caller. A string read with `getString(id)` and no arguments isn't formatted, so `%%` shows literally: use `formatted="false"` and a bare `%`.
- 1.0.107 (#94): the rest of the UI moved to strings.xml. Android trims a resource string's leading and trailing spaces and collapses double spaces unless the text is wrapped in double quotes (`"  ·  %1$s"`): check every suffix and separator. Text inside `semantics { }`, `onClick` or `remember { }` can't call `stringResource`; read it into a `val` first or use `res.getString`. A `@Composable`'s default parameter can call `stringResource`. Plain JUnit tests that call a helper now taking `Resources` need Robolectric (`TrendTest`).
- 1.0.108–1.0.109 (#156): the data layer has no `Resources`. Functions that take a `Context` word their results with it; plain results carry a typed value the UI words (`Backups.Refusal`, `Analysis.Slice` with an empty label, `SeedResult.text(res)`); refusals throw `WorkoutDataException(R.string.x, args)` and screens show `e.text(res)` (its `message` is null, so never show `e.message` for one: use `e.userText(res)`). The Bash tool mangles heredocs holding apostrophes or backslashes: write scripts and replacement lists with the Write tool.
- 1.0.110 (#41): Settings rows come in six kinds in `ui/design/SettingsRows.kt` (switch, choice, action with `value` and `warning`, folder, danger, number) plus `SettingsStatusCard`; use them rather than a raw `Button` or a stack of `SettingsNote`s. A problem always shows an icon and text, never colour alone. Put `focusRing()` before the row's `clickable` so keyboard focus is outlined in gold. A setting that needs notifications calls `NotificationAccess.ask(reason)` when switched on and shows `NotificationsOffNote` under it; it stays on if refused, because the feature still works in the app.
- 2026-10-06 (1.0.113, #36 first slice): Room opens `fitlens.db` (v21). A schema change now means: the entity in
  `data/Schema.kt`, `Db.VERSION`, and an `if (oldVersion < N)` step in `Db.upgrade` that leaves the table exactly as
  Room would create it (same types, NOT NULLs and `defaultValue`s), or Room refuses to open. `RoomSchemaTest` catches
  a mismatch in CI. Keep writing SQL through `Db.writableDatabase`; never call Room's main-thread-checked APIs from
  `getMeta`. Still open on #36: DAOs, committed schema JSON, no queries on the main thread.
- 2026-10-06 (1.0.114, #36 second slice): DAOs in `data/Daos.kt` now back the snapshot loads, `meta`, body, photo
  and goal code, so `getMeta`/`setMeta` are Room queries and **must run off the main thread** (Room throws
  otherwise; tests wrap them in `offMain`). New reads of an existing table belong in a DAO, mapped with `toModel()`;
  group multi-row edits with `Db.transaction { }`, and chunk id lists by `Db.MAX_IDS`. Room's `@Query` bind names
  avoid SQL keywords (`order`, `key`, `from`, `to`). Still open on #36: workout writes (`Workouts.kt`, `Routines.save`),
  imports and backups on DAOs, and committing `app/schemas/.../21.json` (CI now saves it to `ci-logs` under `schemas/`).
- 2026-10-06 (1.0.115, #36 third slice): `Workouts.kt` and `Routines.kt` write only through `WorkoutDao` and
  `RoutineDao`; their `write { w -> }` blocks get the DAO, not a database. A new workout query goes in `WorkoutDao`.
  `workout_set` and the other non-autogenerate tables are inserted with explicit-column `@Query("INSERT ...")`
  returning `Long` (an `@Insert` of the entity would write id 0); position/superset 0 still lets the triggers decide.
  An optional range or list is one query with `(:fromDate IS NULL OR ...)` and `(:everyExercise OR x IN (:ids))`.
  `app/schemas/.../21.json` is committed and is a `debug` assets folder (Robolectric loads debug assets; `test` source-set assets are NOT merged, which cost a CI run); `ExportedSchemaTest` (MigrationTestHelper)
  validates upgrades against it, so a new `Db.VERSION` must commit its new JSON from `ci-logs` in the same build or
  the next. Still open on #36: the FitNotes import and backups (`Backups`, `AutoBackup`, `BackupSync`) on DAOs.
- 2026-10-06 (1.0.118, #36 closed): the FitNotes import (`ImportDao`) and the backup checkpoint (`MaintenanceDao`)
  are on DAOs, and `Db.writableDatabase` is `internal`: app code never writes SQL directly any more, only `Db.upgrade`
  steps and tests do. A dry run inside `Db.transaction { }` rolls back by throwing a private exception that carries
  the result (`FitNotesImporter.DryRun`); Room rethrows a RuntimeException unchanged. Tests call DAO-backed code in
  `runBlocking(Dispatchers.IO)`.
- 2026-10-06 (1.0.119, #60): any write whose changed sets are known goes through `Workouts.writeSets(kind)`, naming
  exercises, dates and extra `scope.areas`; if it can move a record it ends with `replayPrs(w, scope.exercises)`. Never
  call the full `replayPrs(w)` from a per-day or per-exercise write: it reads every weighted set in the history.
  `replayPrs` takes `countWarmups` so tests can call it without loading `Settings`.
