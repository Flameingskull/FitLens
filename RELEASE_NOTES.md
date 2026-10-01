## Overview

Distances and body measurements now have proper units. Choose kilometres, miles or metres for distances, and
centimetres or inches for body measurements. Cardio exercises gain pace on their Stats tab, and you can share a day's
workout straight from the calendar.

This update includes a small database change: each exercise can now store its own distance unit. Everything you've
logged is kept exactly as it was.

## What's new

- **Distance units.** Settings → Units & display (and first-run setup) has a distance unit: kilometres, miles or
  metres. Every distance shows its unit, for example "5 km" instead of "5 dist". This covers set rows, the Track tab's
  Distance field, history, the Distance graph, workout plans, the calendar filter, shared workouts, the PDF report and
  CSV exports.
- **A distance unit for each exercise.** In the exercise editor, a distance exercise can keep the unit from Settings
  or use its own, for example metres for swimming and miles for an outdoor run.
- **Length units for body measurements.** Choose centimetres or inches for measurements such as your waist, chest or
  arms. Values are shown and entered in your chosen unit, while the values you logged are stored unchanged, so you can
  switch back at any time.
- **Pace on cardio stats.** The Stats tab of a distance-and-time exercise shows Best pace (with the day it happened)
  and Average pace for the chosen period, plus Longest distance. Pace is per kilometre or mile, or per 100 m when the
  exercise uses metres.
- **Share from the calendar.** When the selected day has sets, the calendar's ⋮ menu offers to share that day's
  workout, using the same checklist and options as the day log.

## Improved

- The Stats tab's distance figures (Most distance in a workout, Total distance) now show their unit, and are left out
  for timed exercises that don't record distance.
- CSV workout exports add a **Distance unit** column at the end, so spreadsheets built on earlier exports keep their
  columns.

## Known limitations

- Changing a distance unit relabels distances you've already logged rather than converting them. Your earlier
  distances had no unit, so FitLens can't safely work out what they were. Pick the unit you've been logging in.
- Weight can't be overridden for each exercise yet, and units can't be overridden for each body measurement (#7).
- The branded image version of a shared workout (#11) is still to come.
