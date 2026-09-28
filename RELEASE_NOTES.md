## Overview

This build changes how FitLens looks and how your sets read. Each set is now laid out in labelled columns (Set,
Weight and Reps, or Distance and Time), so you no longer have to read "80 kg × 8 reps" like a formula. The whole app
also gains depth: cards, bars and buttons are now glass in the FitLens black, imperial purple and gold, instead of flat
blocks.

There's no database change in this update, and nothing you've logged changes when you install it.

## What's new

- **Sets in labelled columns.** In the day log, the set entry screen and an exercise's **History**, sets appear under
  a **SET · WEIGHT · REPS** heading, with each value in its own column. The numbers are large and lined up, with the
  unit small beside them.
  - The columns follow the exercise type. Strength exercises show Weight and Reps. Cardio shows Distance and Time, and
    timed exercises show Time.
  - Values that an imported exercise recorded outside its type still get a column, so nothing you logged is hidden.
  - Bodyweight sets show **BW** in the Weight column.
  - TalkBack reads each set as one sentence, for example "Set 2, 85 kilograms, 6 reps, personal record".
- **Glass depth.** Screens sit on a soft imperial purple and gold glow.
  - Exercise cards, stat tiles and the date bar are raised glass, with a gold top edge and a shadow beneath.
  - Set rows are wells set into the glass.
  - The top bar is purple glass above its gold rule.
  - Main buttons are polished gold. Secondary buttons are clear purple glass with a gold edge.

## Improved

- One-line set summaries now read "80 kg · 8 reps" instead of "80 kg × 8 reps". This covers the calendar, day
  sharing, the PDF report and delete prompts. Saved workouts read "3 sets · 100 kg · 5 reps".
- With **Mark sets complete** on, the set entry screen shows the selected set by its gold outline instead of a
  "Selected" label, which keeps the columns lined up.

## Known limitations

- The glass is drawn with gradients and shadows, not a live blur of what's behind it.
- Pop-up sheets and dialogs keep their solid backgrounds for now.
- Exercises that record weight and time together (for example a weighted plank) need the new exercise types in #14.
  Until then, a lift can't be set up to record time.
- Distances show without a unit until unit settings arrive.
