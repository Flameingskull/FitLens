## Overview

This build adds **exercise comments**: a note on one exercise within a day's workout, such as "left shoulder tight,
stopped early". It also adds automated checks that protect your data on every update, and a refreshed README that
matches the app as it is now.

This update changes the database (version 14) by adding one table for exercise comments. Nothing you've logged
changes when you install it.

## What's new

- **Exercise comments.** Each exercise in a day's workout can carry one comment for that date.
  - On the exercise screen's **Track** tab, the **Exercise comment** row under the sets adds or edits it.
  - On the day log, the exercise card's menu has **Add exercise comment** (or **Edit exercise comment**), and the
    comment shows on the card under the sets.
  - It also appears in the exercise's **History** under that date, in the calendar's selected day, and in **Share
    workout** when comments are included. CSV exports gain an **Exercise comment** column.
  - Saving an empty comment removes it.

## Improved

- **Comments follow the workout.** Copying, moving or deleting a workout copies, moves or deletes its exercise
  comments, and Undo puts them back. Removing an exercise from a day, replacing a day's workout, deleting workout
  history, deleting an exercise and merging two exercises all handle its comments too. When a move or merge lands two
  comments on the same exercise and day, they're joined rather than one being lost.
- **Safer updates.** Every release now runs automated tests before it's built. They recreate databases the way
  earlier versions of FitLens left them, including the saved workouts and routines merged in 1.0.51, and check that
  every row survives the upgrade. If a test fails, the release isn't published.
- **README.** The project page now describes the app as released: workouts with named days, exercise types, set and
  exercise comments, the rest countdown, the FitLens look and the new tests.

## Known limitations

- The automated tests cover database upgrades so far. Tests for FitNotes imports and for backup and restore are still
  to come (#40).
- FitNotes's own exercise comments aren't imported yet; imports never touch the comments you write in FitLens.
- Exercises in the workout editor move with up and down arrows rather than a drag handle.
- Distances show "dist" as their unit until unit settings arrive (#7).
