## Overview

**FitLens can now log workouts.** Until this build, FitLens could read a FitNotes backup and show it beautifully, but
it couldn't record a single set of its own. 1.0.8 adds the exercise library, the set logging screen and workout
editing, so FitLens works as a logger in its own right. FitNotes imports still merge into the same place and are
never overwritten.

This build also fixes a restore message that could tell you your data was safe when it wasn't.

## What's new

- **Exercise library.** Browse every exercise and category in one place, with quick add, notes, editing and deletion.
  Star the exercises you use most and they come first in every picker. If you're starting without a FitNotes backup,
  a starter library of common exercises can be added on request — it's never added silently, and never on top of
  exercises you already have.
- **Set logging.** Tap an exercise on any day to record sets, with the weight, reps, distance or time that exercise
  actually uses. Each new set is pre-filled from the last time you did that exercise, steppers nudge the numbers, and
  sets can be edited, deleted or given a comment. Deleting a set offers **Undo**.
- **Workout editing.** Add exercises to a day, write a workout comment, and copy, move or delete a whole workout.
  Deleting a workout offers **Undo** too.
- **Any day is now loggable.** The calendar used to open only days that already had data, so there was no way to log
  on a new day. Every day opens now, and a new button on the home screen jumps straight to today.

## Improved

- **Outlined controls are easier to see.** Switch tracks, text field borders, chips and checkboxes were drawn in a
  line too faint to make out in bright light or on a dimmed screen. They now use a brighter border tone that meets
  the accessibility minimum for controls, while chart grid lines keep their original fine hairline finish.
- **Empty screens point somewhere useful.** The home and Training screens used to offer only "import from FitNotes".
  They now also offer logging your first workout and opening the exercise library.
- **Dependencies stay current by themselves.** Dependabot now proposes weekly dependency updates and monthly Actions
  updates, with the Kotlin toolchain grouped so its pieces move together instead of breaking the build one at a time.

## Fixed

- **Restore now tells you the truth when it fails.** If a restore failed after the backup had already taken the place
  of your old data, FitLens still reported that your current data was unchanged. It now says plainly that the backup
  has replaced your previous data, and it reopens the database straight away rather than leaving the app holding a
  closed one.

## Known limitations

- **No personal records yet for sets you log in FitLens.** The "PR" marker is still only carried over from FitNotes
  imports; FitLens doesn't calculate records of its own. That's the records engine (#23), due next.
- **Settings still live on the Sync tab.** The dedicated Settings screen (#38) hasn't landed yet.
- **No drag-to-reorder** for sets or exercises within a workout, and no rest timer, routines or supersets yet.
- Restoring a backup still can't be undone. A safety copy with Undo (#47) is planned.

## Upgrading

Install this APK over your existing FitLens. Your data is kept: the database upgrades in place and adds the new
exercise "favourite" field without touching any existing row.
