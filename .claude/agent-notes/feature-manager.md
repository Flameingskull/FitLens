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
