## Overview

This update finishes moving FitLens's data layer to Room, Android's standard database library. The last two pieces,
the FitNotes import and backups, now go through Room too, so every read and every save in the app uses queries that
are checked against the database when the app is built. You won't see a new screen. What changes is that a mistake
in any of them now stops the build instead of reaching your phone.

Install it over the current app as usual. There's no database upgrade this time, so the app opens as quickly as
before, with everything you've logged, your photos and your settings kept.

## Improved

- **Checked FitNotes imports.** Importing a FitNotes backup adds its categories, exercises, sets, workout comments,
  workout times and body values through checked queries, in one step: the whole import is saved, or none of it is.
  The rules are unchanged: an import only adds, skips what's already there, and respects your renames, deletions and
  edits in FitLens.
- **An exact import summary.** The summary you see before importing comes from a full trial run of the import that
  is then undone, so it matches what the import really adds.
- **Body Tracker CSV imports** save through the same checked queries, skipping values you already have.
- **Backups** prepare the database through Room before it's copied into the backup file.
- **More tests.** Every build now checks that the trial run keeps nothing, that body values are imported once while
  your own order and goals are kept, that values for a custom metric go into it, and that a CSV import adds each
  value once.

## Known limitations

- Save speed is unchanged for now. Settings › About › Save speed still shows the timing of each set you save.
- The TalkBack, 200% font size and small-screen checks still need to be done on a phone.
- Backups made with FitLens 1.0.113 or later can't be restored into 1.0.112 or earlier. Update the app first.
