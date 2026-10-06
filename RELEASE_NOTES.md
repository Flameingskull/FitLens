## Overview

This update takes the move of FitLens's data layer to Room, Android's standard database library, most of the way
there. Everything you do while logging now saves through Room: sets, comments, rest times, supersets, your exercise
library and your workouts. You won't see a new screen. What changes is that every one of these saves is now checked
against the database when the app is built, so a mistake stops the build instead of reaching your phone.

Install it over the current app as usual. There's no database upgrade this time, so the app opens as quickly as
before, with everything you've logged, your photos and your settings kept.

## Improved

- **Checked workout saves.** Adding, editing, ticking, commenting on, reordering and deleting sets all go through
  queries that are checked when the app is built. So do supersets, copying or moving a workout, swapping an exercise,
  logging a whole workout day, deleting history and recalculating records.
- **Checked library and workout edits.** Categories, exercises, exercise types, merging two exercises, the starter
  library, and creating, copying and editing workouts and their days are covered in the same way.
- **Photo imports** add each photo through the same checked queries, and still skip duplicates.
- **Stronger upgrade checks.** The database layout is now recorded in the source code. Every build checks that an
  older FitLens database upgrades to exactly that layout, so later versions can be checked against it too.

## Known limitations

- The FitNotes import still saves through the older code path, and backups still work on the database file
  directly. Both move to Room in a later update.
- Save speed is unchanged for now. Settings › About › Save speed still shows the timing of each set you save.
- The TalkBack, 200% font size and small-screen checks still need to be done on a phone.
