## Overview

This is a small update that makes FitLens use its words consistently. An **exercise** is one movement, such as
Bench Press. A **workout** is a group of exercises with their sets. The day log used to label adding a single exercise
as starting a workout, and it now says what it does. The README has also been brought up to date with the app as it
now works.

Your data isn't touched by this update.

## Improved

- **Clearer empty day.** A day with nothing logged now reads **No workout logged**, with **Add exercise** and
  **Copy previous workout**. Before, it said "Start new workout" but opened the picker for a single exercise.
- **Removing an exercise from a workout.** On an exercise card's ⋮ menu, **Remove from this workout** deletes that
  exercise's sets for the day. It no longer says "Delete", which suggested the exercise itself would go. The exercise
  stays in your library, and the message that follows offers **Undo**.
- **Clearer menu items.** The day log's menu now reads **Copy this workout to another day**, **Move this workout to
  another day** and **Delete this workout**. **Exercises** is renamed **Exercise history**, so it isn't confused with
  the exercise library.
- **README refreshed.** The project page describes the day log, the FitNotes-style navigation, analysis, goals, set
  types and effort, and how FitLens names exercises, workouts and routines.

## Known limitations

- A workout is still built one exercise at a time, or copied from a previous day. **Saved workouts** are named groups
  of exercises with their sets, which you can reuse, change and add to any day in one go. They are planned in
  [#100](https://github.com/Flameingskull/FitLens/issues/100). **Routines**, your saved workouts split into days you
  name, are planned in [#21](https://github.com/Flameingskull/FitLens/issues/21).
- Setting the workout time, the rest timer and sharing a workout aren't on the day log yet.
