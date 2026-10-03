> **Status (2026-10-03): in progress.** Builds A–C done (1.0.72–1.0.74). Parity Builds D–I done (1.0.75–1.0.85,
> with #143, #145 and the Body Tracker redone against the full screenshot set). Build J (1.0.87): #147 (Settings as
> FitNotes's single list), #148 (Copy Workout, Workout Time, Share/Create Workout, 1RM calculator and settings), #127
> (Breakdown Date list and arrows) and #126 (full-screen New Exercise). **Remaining:** #95 (screenshot tests,
> optional). Not verified: nothing squeezed at 320dp (owner's phone test). Next: `/safe-build 95`, or close the plan
> by moving #95 out of it. Tracker: the plan item "FitNotes parity and polish (plan, 2026-10-02)", blocked by #95.

# FitNotes parity and polish: plan across several builds

## Context

The owner's main problems, ahead of every other backlog item:

- The screens have drifted away from FitNotes.
- Text sizing and font aren't right.
- The exercise screen wastes space.
- The "all sets done" buttons sit under Android's navigation bar.
- The chart fill is too faint, and there's only one chart type.
- Rest times can't be planned in a workout.
- Some calculations could be more accurate.

The plan delivers these over consecutive builds, starting with the next `/safe-build`. Each build stays within
safe-build's shape: two to four related issues, core and stretch stages, local checkpoints, one push.

Owner decisions (2026-10-02):

- **Font:** Manrope, bundled.
- **Auto-advance:** switch after about 1.5 s, with Undo.
- **After the last exercise:** return to the day log and offer to stop the workout timer.
- **Chart types:** Line, Bar, Area and Step, chosen per graph.
- **Section labels:** FitNotes-style uppercase labels with a rule under them, set in Manrope.

These supersede two earlier rules. Record them in `CLAUDE.md` rule 4, the memories and `docs/CODEMAP.md`:

- **Headings are no longer serif:** Manrope throughout.
- **Bar charts are allowed again**, reversing #116.

## Step 0: backlog (before Build A, no code)

Per standing rule 3, these chat requests become GitHub issues first. `feature-manager` files them, each one imported
into the tracker:

1. **Epic: FitNotes parity pass, every screen.** One child per screen group (below). Each child lists the matching
   FitNotes screenshots and the deviations found.
2. Typography: Manrope, one type scale, no stray `.sp` sizes.
3. Exercise screen: remove the dead space, compact inputs, auto-advance to the next exercise.
4. Charts: denser gold fill, and a chart-type choice (Line, Bar, Area, Step) on every graph.
5. Prescribed rest times in workouts: per set, per exercise, and between exercises.
6. Calculation accuracy: 1RM, unit constants, and an audit of derived metrics.
7. Bug: `UndoSnackbarHost` and bottom actions ignore the navigation-bar insets. A `bug` labelled issue, filed by
   `bug-manager`.

Link them to #79 and #59 where they overlap. Don't duplicate #79's bundles: the parity children reference them.

## Build A: next `/safe-build` (exercise screen, type, chart fill, calculations)

**Core 1. Exercise screen (`ui/SetEntry.kt`, `ui/design/SetViews.kt`)**

- Remove the gap between `FitTabRow` and the inputs:
  - the Track `LazyColumn` starts at 0 top padding;
  - the date and notes item only renders when it has content;
  - the entry `Column` top padding goes from 8dp to 4dp.
- Match FitNotes screenshot 13:
  - an uppercase section label ("WEIGHT (KG)") over a fine gold rule;
  - the value centred between − and + boxes.
- Make `StepperField` smaller:
  - the step buttons from about 56dp to 48dp, still at least `Spacing.touch`;
  - the value text one step down the type scale.
- Auto-advance (around lines 552–566). When the last set is ticked:
  - show a short "Undo" message;
  - after about 1.5 s, replace the top screen with `Screen.SetEntry(date, next, queue)`, using
    `displayOrder(snap, date)` as today. Supersets are already respected;
  - Undo unticks the set (`Workouts.setDone(id, false)`) and cancels the switch.
- On the last exercise: `nav.home(date)` and a "Workout complete" message. If `WorkoutClock.running`, the message
  offers "Stop workout timer" (`WorkoutClock.stop`).
- Remove the snackbar's "Next:" action button.
- The `next`/queue "Next exercise" button stays only for library queues that aren't a logged workout.
- Fix the insets: `UndoSnackbarHost` (`ui/design/Feedback.kt`) gets `navigationBarsPadding()`, so no message or
  action ever sits under the OS buttons.

**Core 2. Typography (`ui/Theme.kt`, `res/font/`)**

- Bundle the Manrope variable or static TTFs (OFL licence; add the licence file to `res/raw` or `LICENSES`).
- Replace `Serif`/`Sans` with one `Manrope` `FontFamily`.
- Retune `LuxuryType` to a calmer scale:
  - display 40/34/28;
  - headline 24/22/20;
  - title 20/16/14;
  - body 16/14/12;
  - labels 14/12/11;
  - letter-spacing kept only on `labelSmall` and `titleSmall`, so text is never squeezed.
- Sweep the 111 hard-coded `.sp` uses (`grep -rn "\.sp"`) so they use typography roles. The only exceptions are
  inside `Theme.kt` and the Canvas renderers (`ShareImages.kt`, `PdfReport.kt`), which get the same font through
  `ResourcesCompat.getFont`.
- Check that nothing truncates at 320dp width (`maxLines` and ellipsis on titles).

**Stretch 3. Chart fill (`ui/Theme.kt:75`)**

- `ChartColors.fill` alpha goes from 0.18 to about 0.32.
- The gradient tail in `Charts.kt:414` goes from 0.02 to 0.08.

**Stretch 4. Calculation accuracy (`data/Records.kt`, `data/Models.kt`, `data/Store.kt`)**

- One exact constant: `KG_PER_LB = 0.45359237` (international definition), with `LB_PER_KG = 1 / KG_PER_LB`.
  Replace the literal `2.2046226` in `Store.kt:231,234`, `CsvExport.kt:106` and `SettingsScreen.kt:239`.
- Exact `CM_PER_IN = 2.54` stays.
- Distance: miles to km uses 1.609344 if any conversion exists. Check `speedOf` and `paceSecondsOf` in
  `TrainingScreen.kt`.
- 1RM:
  - add **Mayhew (1992)**, `100 / (52.2 + 41.9·e^(−0.055r))`, as a `Formula`;
  - make `AUTO` follow the validation literature: LeSuer 1997 and later reviews found Mayhew and Wathan the most
    accurate at 2–10 reps, and all formulas close at 5 reps or fewer. The new AUTO:
    - 1 rep is the weight lifted;
    - 2–10 reps use the mean of Mayhew and Wathan;
    - 11–15 reps use Wathan, marked as estimated;
    - over 15 reps gives no estimate.
  - Keep the user's override. The PR replay (`Workouts.recalculatePrs`) runs once on upgrade, so stored PR flags
    follow the new factor.
- Add JVM tests:
  - `RecordsTest`: known values per formula;
  - a unit round-trip test: 100 kg to lb to kg is exact to 1e-9.

## Build B: prescribed rest (rest automation)

**Data (Db v17, migration keeps every row, with an upgrade test in `DbMigrationTest`):**

- `routine_day_set.rest_seconds INTEGER NULL`: per set.
- `routine_day_exercise.rest_seconds INTEGER NULL`: uniform for that exercise's sets.
- `routine_day_exercise.rest_after_seconds INTEGER NULL`: the rest before the next exercise.
- Carry these through `PlannedSet` and `PlannedExercise` (`data/Routines.kt`), `save`/`load`, `copy`,
  `migrateSavedWorkouts`, and `Backups` (same tables, so no format change).
- Logging a day (`Workouts.logPlanned`) copies the prescription onto the logged sets: new
  `workout_set.rest_seconds` and an exercise-level `rest_after` held in a small `workout_rest` table keyed by date
  and exercise. Rest still applies after the routine is edited.

**Resolution order** in `startRestAfterSet` (`ui/SetEntry.kt:131`):

1. The set's own rest.
2. The workout exercise's rest.
3. The exercise's `restSeconds`.
4. The global `PortableSettings.restSeconds`.

When the ticked set is the exercise's last, use `rest_after` (the between-exercises rest) if one is set. That pairs
with Build A's auto-advance, so the next exercise opens with its rest already counting down.

**UI (`ui/WorkoutEditorScreen.kt`):**

- `PlannedSetsSheet` gets a per-set rest field, plus a "Same rest for every set" toggle with one `RestLengthStepper`.
- Each exercise row shows "Rest 90 s · then 2 min".
- The day menu gets "Set rest for every exercise".
- Reuse `RestLengthStepper` and `REST_CHOICES` from `ui/RestTimer.kt`.

## Build C: chart types on every graph

- Add `enum ChartKind { LINE, BAR, AREA, STEP }`.
- `LineChart` (`ui/Charts.kt`) becomes `FitChart(kind, …)`:
  - Line is today's chart: a red line over the gold fill;
  - Area is the fill plus a thin red edge, without markers;
  - Bar is gold-filled columns with red tops;
  - Step is a red step line over the gold fill.
  - Grid, baseline and colours stay shared.
- The choice is a `DropdownPill` in `GraphOptionChips` (`ui/ChartViews.kt`).
- The choice is remembered per graph in `PortableSettings` (a `graphKind` map keyed by graph id).
- Apply it to every graph:
  - exercise Graph pane;
  - body graph;
  - Analysis Workouts and Exercises;
  - full-screen chart (bars keep `valueZoom = false`);
  - share images (`ShareImages.renderGraph`).
- The Breakdown tab's donut stays.
- Update the CODEMAP "New graph" row.

## Builds D onwards: the FitNotes parity pass, one screen group per build

For each group:

1. Lay the FitLens screen beside the FitNotes screenshots in `nimbalyst-local/fitnotes-screens/2026-09-28/`
   (1–14).
2. List every difference: element, position, size, order, spacing, wording and behaviour.
3. Fix all of them.

The visual rule is FitNotes's structure, with the FitLens skin applied only to surfaces:

- **Lists, labels and values float on the glass, without boxes.** `raisedGlass` stays only where FitNotes has a
  card or a button.
- Gold hairlines take the place of FitNotes's blue rules.

FitLens-only screens follow the same pattern.

| Build | Screen group | Main files |
| --- | --- | --- |
| D | Day log and the workout drawer (#81, #84, #8) | `DayScreen.kt`, `WorkoutDrawer.kt`, `SetViews.kt` (`ExerciseCard`) |
| E | Exercise screen: History and Graph tabs, records, info | `TrainingScreen.kt`, `ExerciseStats.kt`, `ExerciseLibrary.kt` (`ExerciseInfoSheet`) |
| F | Library, categories, routine switcher, workout editor (#83, #91, #126) | `ExerciseLibrary.kt`, `WorkoutEditorScreen.kt`, `WorkoutSheets.kt` |
| G | Calendar, Body tracker and measurement | `CalendarScreen.kt`, `BodyScreen.kt`, `BodyMeasurementScreen.kt` |
| H | Analysis and Settings | `AnalysisScreen.kt`, `AnalysisTabs.kt`, `BreakdownTab.kt`, `SettingsScreen.kt`, `design/SettingsRows.kt` |
| I | FitLens-only screens: photos, viewer, slideshow, PDF (#92) | `PhotosScreen.kt`, `PhotoViewerScreen.kt`, `SlideshowScreen.kt` |

The shared pieces in `ui/design/` change first in Build D, so later groups inherit them: `FitTopBar`, `FitTabRow`,
`SetRow`, `ExerciseCard` and `SettingsRows`.

#95 (screenshot tests) would let CI catch drift. Recommend it as Build D's stretch.

## Verification (each build)

CI is the compiler, because there's no local Android SDK.

- **Before the push:**
  - `./gradlew testDebugUnitTest` runs in CI;
  - add tests for the new `Records` formulas, the unit constants, and the v17 migration.
- **After the push:** check `ci-logs/status.txt`, or use `gh run watch`.
- **Owner test, Build A:**
  - log a workout day;
  - the inputs sit directly under the tabs;
  - ticking the last set switches to the next exercise after a moment, and Undo stays;
  - the last exercise returns to the day log;
  - no message sits under the navigation bar;
  - Manrope appears everywhere, with nothing squeezed at the smallest phone width;
  - the chart fill is denser;
  - 1RM values look plausible against FitNotes.
- **Owner test, Build B:** build a workout with per-set and between-exercise rests, log it, and tick through it. The
  rests follow the plan and the screen auto-advances.
- **Owner test, Build C:** switch each graph between Line, Bar, Area and Step, then reopen it and check the choice
  is remembered.
- **Owner test, Builds D onwards:** compare each screen side by side with the FitNotes screenshots.
- **Every build:**
  - `RELEASE_NOTES.md` is written;
  - `docs/CODEMAP.md` is updated;
  - the README refresh happens at 1.0.74, which lands within these builds.
