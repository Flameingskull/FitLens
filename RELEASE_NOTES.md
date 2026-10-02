## Overview

This update starts a pass to bring FitLens back in line with FitNotes, beginning with the screen you use most in the
gym. The exercise screen is more compact and moves on by itself when an exercise is finished, the whole app uses a
new typeface, graphs are easier to read, and 1RM estimates follow the research more closely.

There are no database changes. Everything you've logged is kept as it was.

## What's new

- **Moves on by itself.** Tick the last set of an exercise and FitLens shows "Done. Next: …" with **Undo**, then
  opens the next exercise of the day about a second and a half later, in your workout's order with supersets kept
  together. Undo unticks the set and stays, or comes back if the next exercise had already opened.
- **Workout complete.** After the last exercise of the day, FitLens returns to the day log. If the workout timer is
  running, the message offers **Stop workout timer**.
- **A new typeface.** Manrope, a clean and highly legible typeface, is used everywhere: screens, shared images, the
  PDF report and the progress video. Headings are no longer serif, and text sizes follow one calmer scale, so titles
  are no longer squeezed on smaller phones.

## Improved

- **A more compact exercise screen.** The weight and reps sit straight under the Track, History and Graph tabs.
  Each field has a FitNotes-style heading ("WEIGHT (KG)") over a fine gold rule, with the value centred between
  square − and + buttons, leaving more room for your sets.
- **Richer graph fill.** The gold fill under every graph line is deeper, so the shape of your progress reads at a
  glance.
- **More accurate 1RM estimates.** The Automatic formula now follows the validation studies: from 2 to 10 reps it
  uses the mean of the Mayhew and Wathan formulas, the two found most accurate in that range; from 11 to 15 reps it
  uses Wathan and marks the estimate as approximate (≈); above 15 reps it doesn't estimate. Mayhew is also available
  as a formula of its own under **Settings → Personal records**. If you chose a formula yourself, it's kept.
- **Exact pound conversion.** Every kg/lb conversion now uses the exact definition of the pound (0.45359237 kg)
  instead of a rounded figure.
- The exercise screen's **Next exercise** button now appears only for exercises picked together in the library that
  aren't in the day's workout yet. Exercises already in the workout are reached by moving on automatically.

## Fixed

- Messages such as "Set deleted · Undo" no longer appear behind Android's navigation buttons (#140).

## Known limitations

- Estimated 1RMs from sets of 16 to 20 reps are no longer shown with the Automatic formula, which may remove a few
  points from an "Estimated 1RM" graph. Personal record marks are unaffected: they're based on the weight lifted for
  each rep count, not on an estimate.
- Choosing a chart type (line, bar, area or step) for each graph comes in a later update (#137), as do prescribed
  rest times in workouts (#138) and the screen-by-screen FitNotes comparison (#134).
