## Overview

This update makes FitLens quicker to respond as your history grows. Saving a set no longer reloads everything you've
ever logged. Full-screen graphs can now zoom the values as well as the timeline. Every release is also now checked
automatically against what matters most for your data: FitNotes imports, `.fitlens` backups and restores, and
upgrades from every earlier version of FitLens.

There's no database change in this update, and nothing you've logged changes when you install it.

## What's new

- **Zoom the values on full-screen graphs.** In full screen, pinch across to zoom the timeline as before, or pinch
  up and down to zoom the values, so small changes in a long, flat line become easy to see. Dragging moves in both
  directions, and Reset zoom or a double tap shows the whole graph again. TalkBack users get matching actions: zoom
  in or out on the values, and show higher or lower values. Bar charts always start at zero, so only their timeline
  zooms.

## Improved

- **Faster saves during a workout.** FitLens used to re-read every set, photo and body value after every change.
  Now each change re-reads only what it touched:
  - saving, editing, deleting, ticking off or commenting a set, changing a superset, reordering a day and undoing a
    delete re-read only the sets of the exercises involved;
  - workout and exercise comments and workout times re-read only the comments and times;
  - changes to exercises, categories, workouts and goals re-read only the exercise library;
  - photo and body-tracker changes re-read only photos or body values;
  - changing a setting such as the weight unit applies straight away without reading the database at all.

  The more history you have, the bigger the difference. Changes that recalculate personal records across your whole
  history (logging a workout day, copying or moving workouts, merging exercises), imports and restores still refresh
  everything, as before.
- **FitNotes imports are tested on every release.** A small made-up FitNotes backup is imported into FitLens before
  each release is built. The tests confirm that an import:
  - adds everything the first time, and nothing when the same backup is imported again;
  - matches exercises by name and never overwrites or deletes anything you created in FitLens;
  - counts identical sets correctly (3 × 5 × 100 kg stays three sets);
  - respects exercises you renamed or deleted in FitLens.
- **Backups and restores are tested on every release.** The tests save a backup, change the data, and restore it,
  checking that every set, body value, comment and photo comes back. They also check that a backup from an older
  FitLens is upgraded when you restore it, and that a damaged file, a non-FitLens file or a backup from a newer FitLens
  is refused before anything on your phone is touched.
- **Upgrades from every version are tested.** Databases are rebuilt the way the very first versions of FitLens left
  them and upgraded to today's, checking that every workout, comment, time and body value survives.

## Fixed

- **One release per change.** A recent update was published twice (1.0.52 and 1.0.53, with the same contents), and
  the lower number was briefly marked as the latest release. Releases are now built one at a time, in order, and a
  change that has already been released is never published again.

## Known limitations

- Screens still redraw after every change, even when the change was in another area. Redrawing only the affected
  screens, and measuring save times on a phone with years of history, are still to come (#60).
- The final step of a restore, reopening the app's data afterwards, is still checked by hand rather than by the
  automated tests.
- Exercises in the workout editor move with up and down arrows rather than a drag handle.
- Distances show "dist" as their unit until unit settings arrive (#7).
