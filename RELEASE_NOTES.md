## Overview

This update continues the move of FitLens's data layer to Room, Android's standard database library. Last time Room
took over the database file. Now the app reads your data through it and saves your settings, body values, photo
edits and goals through it. You won't see a new screen, but these saves are now checked more strictly when the app is
built and are safer when the phone is under pressure.

Install it over the current app as usual. There's no database upgrade this time, so it opens as quickly as before,
with everything you've logged, your photos and your settings kept.

## Improved

- **Checked reads.** Your workouts, sets, comments, rest times, routines, body values and photos are loaded through
  queries that are checked against the database when the app is built. A mistake in one now stops the build
  instead of reaching your phone.
- **Settings saved in one step.** Changing a preference now saves all of them together in one write. If the app is
  closed or the phone switches off part-way through, no preference is left half-saved.
- **Safer body and goal edits.** Adding a body value, setting a measurement goal or unit, reordering measurements,
  editing a custom metric and reordering goals each save as a single step: all of it is saved or none of it is.
- **Large photo selections.** Changing the date, pose or confirmation of many photos at once is split into safe
  batches, so very large selections work on older phones too.

## Known limitations

- Workout logging, imports and backups still save through the older code path. They move to Room in a later update.
- Save speed is unchanged for now. Settings › About › Save speed still shows the timing of each set you save.
- The TalkBack, 200% font size and small-screen checks still need to be done on a phone.
