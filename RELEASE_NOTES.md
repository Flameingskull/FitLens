## Overview

A documentation release. **The app itself is unchanged from 1.0.13.** It's published because every change to FitLens
gets a matching release. Installing it over 1.0.13 is safe and keeps all your data, but you don't need to.

## Improved

- **README rewritten for the app as it is now.** FitLens has been a workout logger since 1.0.8, but the README still
  called logging "in progress". It now:
  - leads with logging: the exercise library, set-by-set entry, workout editing and Undo;
  - adds a "just want to start logging?" route to first-time setup that doesn't need FitNotes;
  - lists what's actually still missing: personal records for sets logged in FitLens (#23), reordering sets and
    exercises (#70), undoing a restore (#47) and a dedicated Settings screen (#38);
  - explains why release numbers can skip. Pull-request checks share the build counter, so 1.0.8 was followed by
    1.0.13. A higher number is always the newer build. A fix is tracked in #78.

## Known limitations

- Personal records still aren't calculated for sets logged in FitLens. The "PR" mark comes only from imported
  FitNotes data.
- Settings still live on the Sync tab, and restoring a backup can't be undone yet.
- Moving a workout onto a day that already has one can leave two comments and two sets of times on that day.
