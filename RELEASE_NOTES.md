## Overview

This build tidies up your exercise library and finishes the workout drawer. Duplicate exercises, such as
"Bench Press" and "Barbell Bench Press", can now be merged into one, with their full history. Exercises in the
workout drawer can be dragged into a new order. The Analysis breakdown chart can also open full screen, like every
other graph in FitLens.

There's no database change in this update, and nothing you've logged changes when you install it.

## What's new

- **Merge duplicate exercises.** In the exercise library, open an exercise's menu and choose **Merge into…**, then
  pick the exercise to keep. Everything moves across: every set with its date, place, superset and tick, its goals,
  and its places in saved workouts and routines. The kept exercise keeps its name, category and settings. It takes
  the other exercise's notes and favourite star only if it has none of its own.
  - Personal records are worked out again across the joined history.
  - Later FitNotes imports respect the merge: the old name's history is added to the kept exercise instead of
    coming back as a duplicate, and sets you deleted stay deleted.
  - A safety copy is taken first, so you can undo a merge from **Settings → Backups** for 7 days.
- **Drag to reorder in the workout drawer.** Each exercise in the drawer on the exercise screen has a drag handle.
  The list follows your finger, and the new order is saved when you let go.
- **Full-screen breakdown chart.** **Analysis → Breakdown** has a full-screen button (or double-tap the chart). Full
  screen gives the chart more room. Turn the phone sideways and the legend sits beside the chart.

## Improved

- **Moving exercises keeps supersets together.** Moving an exercise up or down, whether you drag it in the drawer or
  use Move up and Move down on the day log, swaps it with the neighbouring exercise within its superset. Outside a
  superset, it moves past the neighbouring superset as a whole, so a superset is never split.
- **Workouts and routines are complete.** Saved workouts, the workout editor, routines and their days, and adding a
  workout to a day, all in the new design.

## Known limitations

- An exercise can only be merged into another of the same type (weight and reps, distance and time, or time), so
  weights never mix with distances or times.
- The workout drawer opens from the menu button on the exercise screen. It doesn't open with a swipe from the left
  edge, because Android uses that swipe as its back gesture.
- Full-screen graphs zoom and pan along the timeline only. Zooming the vertical scale will come in a later build.
