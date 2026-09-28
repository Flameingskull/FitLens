## Overview

This build completes FitLens's body tracker. All your measurements are now managed on one screen. There's a standard
set to start from, and any value you logged can be changed. An exercise's history now shows each day's totals and can
copy a past workout's sets into today. The README has also been brought up to date.

There's no database change in this update, and nothing you've logged changes when you install it.

## What's new

- **Measurements screen.** In the body tracker, the edit button opens **Measurements**, a list of every measurement.
  - Switch a measurement off to hide it from the body tracker, the day log and the pickers. Its values are kept, and
    FitNotes imports keep your choice.
  - Create, edit and delete your own measurements with their own units. This replaces the old Custom metrics dialog.
  - **Add the standard measurements** adds bodyweight, body fat, waist, chest, hips, arms, thighs, calves, neck and
    shoulders, skipping any you already have. First-run setup offers the same.
- **Edit a logged value.** Tap a value in the body tracker's **History** to change its value, date, time or comment,
  or to delete it. Values imported from FitNotes are shown with a note that they're changed in FitNotes, because the
  next import would bring the original back.
- **Day totals and Copy to today.** On an exercise's **History** tab, each day shows its sets, reps and volume (or
  distance and time). **Copy to today** repeats that day's sets today, with Undo.

## Improved

- Logging a measurement, from the day log or the body tracker, now opens a sheet in the FitLens design, with the
  date, time and comment together.
- The README describes the app as it is at this release, including everything added since 1.0.41.

## Known limitations

- The standard tape measurements use centimetres. Other length units will come with unit settings.
- A value imported from FitNotes can't be edited in FitLens.
- Sets within an exercise are still reordered with buttons rather than by dragging.
