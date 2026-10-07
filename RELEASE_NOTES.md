## Overview

This update lets you fix the dates of photos you imported before 1.0.120, and makes the app do less work behind the
scenes while you log a workout.

Install it over the current app as usual. There's no database upgrade, and everything you've logged, your photos and
your settings are kept.

## What's new

- **Re-read photo dates.** On the Photos screen, the ⋮ menu has a new **Re-read photo dates** action. It goes through
  the photos you've already imported and dates each one the way new imports are dated: the camera's capture date
  first, then the date in the original file name, then the photo's "last edited" time (flagged for you to check).
  It tells you how many photos changed, and you can undo it straight away.
- **Your own dates are safe.** A date you set or confirmed by hand is never changed, and a date that came from your
  phone's gallery is only replaced by the camera's own capture date.

## Improved

- **Less work after every change.** Lists, pickers and graphs now recalculate only when the data they show changes.
  Saving a set no longer redoes the work behind the photo gallery, the body tracker or the exercise library, and
  adding a photo or a body value no longer redoes the work behind your workout screens.

## Known limitations

- Re-reading dates can only use what FitLens keeps: the photo's own metadata and its original file name. The gallery
  date and file date your phone had at import time aren't stored, so a photo with neither a capture date nor a dated
  file name keeps its current date.
- Every screen is still redrawn after a change, even when its numbers are reused. Splitting that further is planned.
