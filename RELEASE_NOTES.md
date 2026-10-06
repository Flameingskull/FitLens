## Overview

This update changes how FitLens stores your data, not what you see. The database that holds your workouts, body
values, photos and settings is now managed by Room, Android's standard database library. Room checks the database
every time it's upgraded, so a future update that would damage or mismatch your data stops before it's used, instead
of failing later. It also lays the groundwork for faster saving and loading in later updates.

Install it over the current app as usual. The first time it opens, FitLens upgrades the database in place, which may
take a moment longer than usual with a long history. Everything you've logged, your photos, poses and settings are
kept.

## Improved

- **A checked database.** Every table is rebuilt once into the exact layout Room expects, keeping every row and
  value. If anything didn't match, the upgrade stops and leaves your data as it was rather than losing any of it.
- **Backups.** Restoring a `.fitlens` backup from any earlier version goes through the same upgrade and check.
- **Installing an older version.** If an older FitLens is ever installed over this one, or this one over a later
  one, the app still opens with your data instead of closing on start.

## Known limitations

- This is the first part of the move to Room. The app still reads and writes its data the same way as before, so
  there's no speed change yet. Faster per-screen updates come in later builds.
- Backups made with this version can't be restored into FitLens 1.0.112 or earlier. Update the app first.
- The TalkBack, 200% font size and small-screen checks still need to be done on a phone.
