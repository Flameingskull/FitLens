## Overview

This build makes logging more precise. Sets can now be marked as warm-up, drop or failure sets. Warm-ups stay out
of your records and statistics unless you choose to count them. You can also record how hard each set felt, as RPE
or reps in reserve. The calendar and weekly analysis can now start the week on Monday, Saturday or Sunday, and the
weight buttons can step by the amount you choose.

This update upgrades FitLens's database. Every set you've logged or imported is kept and becomes a working set.
Backups from earlier versions restore the same way.

## What's new

- **Set types.** On the set entry screen, choose **Working**, **Warm-up**, **Drop set** or **To failure** for each
  set. Warm-up, drop and failure sets carry a small gold **W**, **D** or **F** in your set lists and exercise
  history, and TalkBack reads the type aloud.
- **Warm-ups out of the numbers.** By default, warm-up sets are left out of personal records, estimated maxes,
  volume, exercise graphs, the Analysis screens, the records board and the PDF training summary. They always show in
  your history. **Settings → Workout & logging → Count warm-up sets in records and stats** includes them again, and
  your PR marks are updated as soon as you switch it.
- **Effort per set (optional).** Turn on **Settings → Workout & logging → Effort per set** to record **RPE** (6 to
  10, in half steps) or **RIR** (reps in reserve, 0 to 5+). It appears as large chips on the set entry screen. It's
  always optional: tap the chosen value again to clear it.
  - Your sets show it as "RPE 8" or "2 RIR", and TalkBack reads "RPE 8" or "2 reps in reserve".
  - Effort is stored once, so switching between RPE and RIR, or turning the field off, never changes or deletes
    what you've recorded.
- **Week start.** **Settings → Units & display → Week starts on** sets Monday, Saturday or Sunday. It's used by the
  calendar and by weekly totals and breakdowns. It's also in first-run setup.
- **Weight step.** **Settings → Units & display → Weight step** sets how much the + and − buttons change the weight,
  for example 1.25 kg or 5 lbs.

## Improved

- **Richer CSV export.** Workout exports gain `set_type` and `RPE` columns.

## Known limitations

- Per-exercise and per-measurement units, and distance and length units, are still to come.
- Effort isn't shown in the PDF report yet.
- A set's type and effort are chosen as you log. There's no way yet to change several sets at once.
