## Overview

This build brings **routines**. A routine is your saved workouts split into days you name, such as Push, Pull and
Legs, or Day A and Day B. As in FitNotes, the exercise library's title now switches between all your exercises and
your routines. A routine lists its days, marks the one that comes next, and logs that day's whole workout when you
tap it.

This update upgrades FitLens's database to add routines. Nothing you've logged, imported or set up changes, and
backups from earlier versions restore the same way.

## What's new

- **Routines.** **⋮ → Routines** lists them. Create one with **+**, then:
  - name it, and add notes if you like;
  - add a day for each workout, name it, and choose the saved workout it uses;
  - drag days into order, edit or remove them, and copy a day into another routine.

  Routines can be duplicated (for example "PPL v2" from "PPL") and deleted, and a deleted routine can be brought
  back with **Undo**.
- **The routine switcher.** Tap the title of the exercise library (the **+** on the day log) to choose **All
  exercises**, one of your routines, or **New routine**. FitLens remembers your choice. A routine shows its days,
  with the next one marked in gold. Tap a day to review its exercises and sets, then log the whole workout at once,
  with Undo.
- **The next day, suggested.** FitLens remembers which saved workout, and which routine day, each logged day was
  started from, and suggests the day after the last one you did. **Add workout** on the day log now shows your
  current routine's days first, with the next one marked.
- **Save a day into a routine.** **⋮ → Save as a workout** can now replace one of your saved workouts instead of
  adding a new one. It can also put the workout straight into a routine, as a new day or in place of an existing
  day's workout. **Undo** puts everything back.

## Improved

- **Moving a workout** to another day keeps track of the saved workout it came from, and deleting a workout clears
  it, so the next-day suggestion stays right.

## Known limitations

- A day in a routine uses one saved workout. Supersets and circuits within a workout come later
  ([#18](https://github.com/Flameingskull/FitLens/issues/18)).
- Routines from FitNotes backups aren't imported.
- If you delete a saved workout that a routine day uses, that day shows "No workout chosen yet" until you pick
  another.
