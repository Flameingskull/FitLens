## Overview

This build brings **saved workouts**. A workout is a group of exercises with their sets. You can now save one, such
as "Push A" or "Leg day", and add the whole workout to any day in one go. As with FitNotes's "Log All", every set
is logged at once, ready for you to update as you train. You can build a workout from scratch, save a day you've
already logged, and swap exercises in and out.

This update upgrades FitLens's database to add saved workouts. Nothing you've logged, imported or set up changes, and
backups from earlier versions restore the same way.

## What's new

- **Saved workouts.** **⋮ → Workouts** lists your saved workouts. Create one with **+**, then:
  - give it a name and optional notes;
  - add its exercises with a picker where you can tick several at once, and drag them into order;
  - tap an exercise to choose its sets: **As last time** (repeats what you did the last time you trained it), or
    **These sets** (the weights, reps, times or distances you plan);
  - swap an exercise for another, or remove it.

  Workouts can be copied and deleted, and a deleted workout can be brought back with **Undo**.
- **Add a whole workout to a day.** On the day log, **⋮ → Add workout** (or **Add workout** on an empty day) lets you
  choose a saved workout, or **build a new workout** by ticking several exercises. A review lists every exercise and
  the sets it will add. Untick any you're skipping today, then add them all at once. Exercises with nothing to repeat
  yet open one after another so you can log them. A workout you build can be saved for next time, and every addition
  can be undone.
- **Save a day as a workout.** **⋮ → Save as a workout** turns a day you've logged into a saved workout, keeping its
  exercises in order with either those sets or "as last time".
- **Swap and replace.** An exercise card's menu now has **Swap exercise**, which moves that day's sets to another
  exercise, with Undo. **⋮ → Replace this workout** swaps the day's sets for a saved workout.

## Improved

- **The empty day** now offers **Add workout**, **Add exercise** and **Copy previous workout**.
- **Deleting an exercise** also removes it from your saved workouts.

## Known limitations

- **Routines**, your saved workouts split into days you name with a suggested next day, come next
  ([#21](https://github.com/Flameingskull/FitLens/issues/21)).
- A logged workout doesn't yet remember which saved workout it came from. Swapping an exercise on the day log
  changes that day only; to change a saved workout for good, use the workout editor.
- Prescribed sets don't include effort (RPE/RIR) or set comments.
