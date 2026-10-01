## Overview

More of the screen goes to your data. Rows of filter chips have been replaced by compact dropdowns across the app,
and the last Settings pages (Backups, FitNotes import and Data tools) now match the rest of Settings. Screens also
slide in and out as you move around the app.

There's no database change in this update, and nothing you've logged changes when you install it.

## Improved

- **Compact dropdowns instead of rows of chips.** Each of these choices is now one dropdown that shows the current
  choice:
  - the period on an exercise's Stats and Records tabs, with a custom date range at the end of the list;
  - what All days shows;
  - the pose filter and grouping on Photos (each pose still shows its photo count), and the pose in the photo viewer;
  - Analysis's records board (actual or estimated, which exercises, sort order) and Breakdown (measure, split, span);
  - the date range in Data tools.

  The workout-length graph's Trend and From zero options are now in its ⋮ menu, as on the other graphs. The set entry
  screen keeps its visible buttons, because you use them mid-workout.
- **Backups, FitNotes import and Data tools match the rest of Settings.** These pages are now built like the other
  Settings pages: grouped headings over a gold line, rows you tap, switches, and choices that open a list. The current
  backup folder, FitNotes folder, date range or exercises show in gold on their rows. How often to back up and how many
  backups to keep are each one row, as are the CSV export's data and weight unit. An action that can't be used yet is
  dimmed and explains why, for example the PDF report before anything has been logged.
- **Easier to find in Settings search.** Searching for "keep the newest automatic backups", "back up after changes"
  or "sync the FitNotes folder automatically" now finds these settings.
- **Screens slide.** Opening a screen slides it in, and Back slides it away. Moving to another day or to the next
  exercise in a set still happens in place, without an animation.
- **Clearer period names.** Periods read "Last month", "Last 3 months", "Last year" and "All time" instead of 1M, 3M,
  1Y and All.

## Known limitations

- The rest of the motion and polish pass (#93), with haptics and a shared empty, loading and error state for every
  screen, is still to come.
- The calendar (#87) and the photo, compare, slideshow, video and PDF screens (#92) haven't been restyled yet.
