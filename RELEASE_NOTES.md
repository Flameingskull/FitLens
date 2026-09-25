## Overview

This build brings the first part of FitLens's own personal-records engine. Sets you log in FitLens now earn a PR mark
when you save them, and every screen uses one improved one-rep-max estimate. The Records tab can show your records for
the last workout, week, month, year or all time. None of your existing data changes.

## What's new

- **PR marks for sets you log.** A new set gets a PR mark as soon as you save it if it's heavier than anything you've
  lifted for that many reps or more. Before this build, only sets imported from FitNotes could carry one.
- **Records by period.** The Records tab on each exercise has a period filter: **Workout** (the most recent session),
  **Week**, **Month**, **Year** and **All**. The summary and the 1RM to 15RM table follow the period you choose.

## Improved

- **A more accurate estimated 1RM.** Up to 10 reps, the estimate is the average of the Epley and Brzycki formulas.
  From 11 to 20 reps it follows a gentler curve, so a high-rep set no longer overstates your strength. Sets of 13 to 20
  reps now count towards the estimate, where before anything over 12 reps was ignored. The graphs, the exercise list,
  the Records tab and the PDF report all use this one estimate.
- **Rep-max records follow the superseding rule.** A set of equal or heavier weight for more reps counts as the record
  for every lower rep count too. When that happens, the table shows the reps that set it, for example
  "100 kg × 5" in the 3RM row.

## Known limitations

- Sets logged before this build, and sets you edit, keep the PR mark they had. A "Recalculate personal records"
  option is still to come.
- There's no custom date range on the Records tab yet, and no PR celebration or notification.
- Because the estimate changed, estimated 1RM values and graphs will read a little differently from earlier builds.
