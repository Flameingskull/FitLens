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
