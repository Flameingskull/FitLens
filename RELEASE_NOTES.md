## Overview

This is a reliability update. Nothing on screen changes. Instead, every release is now checked automatically against
the things that matter most for your data: FitNotes imports, `.fitlens` backups and restores, and database upgrades
from every earlier version of FitLens. The release process has also been fixed so that one change can never publish
two releases.

There's no database change in this update, and nothing you've logged changes when you install it.

## Improved

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
- **One release per change.** A recent update was published twice (1.0.52 and 1.0.53, with the same contents), and
  the lower number was briefly marked as the latest release. Releases are now built one at a time, in order, and a
  change that has already been released is never published again. 1.0.53 is marked as the latest of that pair.

## Known limitations

- The final step of a restore, reopening the app's data afterwards, is still checked by hand rather than by the
  automated tests.
- Exercises in the workout editor move with up and down arrows rather than a drag handle.
- Distances show "dist" as their unit until unit settings arrive (#7).
