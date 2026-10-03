## Overview

This update is about the foundations. FitLens now targets **Android 16**, the level Google Play requires from this
year, and it is built with the matching Android tools. The app's look is now checked automatically: every build
draws the shared buttons, rows, cards and bars at two phone widths and at large text, and stops if any of them
changes unexpectedly. Analysis is quicker to come back to, because its heavier calculations run in the background
and are remembered.

There are no database changes. Everything you've logged is kept as it was, and so are all your settings. Install it
over the current app as usual.

## Improved

- **Ready for Android 16.** FitLens now targets Android 16 (API 36) and still runs on Android 10 or newer. We
  checked the Android 16 changes that could affect it: the full-screen layout, the back gesture, the rest and workout
  timer notification, and photo and backup access all work as before.
- **Analysis is smoother with long histories.** The Breakdown donut, the calendar filter, the Workouts totals,
  workout length and the Records board are worked out in the background, so scrolling and tapping stay responsive.
  Results are remembered: going back to a breakdown or graph you've just seen shows it at once. Adding a photo or a
  body value no longer makes them work everything out again.
- **Breakdown says "Working it out…"** the first time it calculates, instead of briefly showing "Nothing to break
  down".
- **Checks on every build.** Screenshots of the top bar, exercise card, set rows, stat tiles, settings rows, tabs and
  buttons are compared on every build, at 360dp and 411dp and at double text size, so layout slips are caught before
  a release reaches your phone. They use made-up data only.

## Known limitations

- Screenshot checks cover the shared components for now. Whole screens will be added page by page, with made-up data.
- Background calculation covers Analysis and the calendar filter. Measuring save-to-screen times over several years
  of data, the last part of the performance work (#60), is still to come.
- Please check on your phone that the app updates over the installed version, and that photo import, automatic
  backups and backup and restore still work as before.
