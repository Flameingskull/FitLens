## Overview

This update makes FitLens's bigger workout changes faster and keeps your PR trophies right after every change.
Logging a whole workout day, copying or moving a workout, swapping or merging exercises, deleting a workout and
undoing any of these used to re-read your entire training history. Now each one re-reads only the exercises and days
it changed, so these actions take about as long with years of history as with a few weeks.

Install it over the current app as usual. There's no database upgrade, and everything you've logged, your photos and
your settings are kept.

## Improved

- **Faster workout changes.** Logging a workout day, swapping an exercise, merging two exercises, deleting an
  exercise, deleting a workout, copying or moving a workout or sets, deleting history for chosen exercises, and the
  Undo for each of these now refresh only the exercises and days they touched.
- **Quicker PR checks.** After a change, personal records are worked out again for the affected exercises only,
  instead of for every exercise you've ever logged.

## Fixed

- **PR trophies after copying or moving.** Copying a workout or sets to another day, moving a workout, or undoing a
  move now updates the trophies. A copied set that beats your best is marked as a record, and a later set loses its
  mark if an earlier day now beats it.
- **PR trophies after editing or deleting a set.** Changing a set's weight, reps or date, or deleting a set, now
  updates the trophies of that exercise. If you delete a record, the next set that beats everything before it gets
  the trophy. Deleting a whole workout and undoing a delete do the same.

## Known limitations

- A change in one area still redraws every open screen, even when that screen didn't change. This will be fixed in
  a later update.
- Settings › About › Save speed still shows the timing of each save. Its readings over the next few releases will
  show whether saving stays quick as your history grows.
- The TalkBack, 200% font size and small-screen checks still need to be done on a phone.
