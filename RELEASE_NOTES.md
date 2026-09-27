## Overview

This build adds FitNotes's **mark sets complete** mode. Turn it on to tick off each set as you do it, see how far
through each exercise and the workout you are, and be offered the next exercise when one is finished. It also rounds
off supersets: you can now build them from the workout drawer, and they come along when you copy or move a workout.

This update upgrades FitLens's database to remember which sets you've ticked. Nothing you've logged changes, and
backups from earlier versions restore the same way.

## What's new

- **Mark sets complete.** Turn on **Settings → Workout & logging → Tick sets off as you do them**. Then:
  - every set on the day log and the exercise screen has a tick box;
  - each exercise card shows how many of its sets are done, and the day's summary shows the whole workout's progress;
  - ticking an exercise's last set offers **Next** in the message that follows, straight to the next exercise in the
    workout (supersets kept together), or tells you the workout is done;
  - the workout drawer shows each exercise's progress.

  It works well with saved workouts: add the whole workout, then tick each set off as you go.
- **Supersets from the drawer.** Each exercise in the workout drawer has a menu with **Superset with the next
  exercise** and **Remove from superset**.

## Improved

- **Supersets travel with a workout.** Copying a workout to another day brings its supersets. Moving one keeps its
  groups without mixing them into the target day's.
- **Undo puts a set back exactly.** A deleted set restored with Undo keeps its tick, its place and its superset.
- **The README** has been brought up to date with everything since 1.0.36.

## Known limitations

- Ticks can't be added to sets in a saved workout before it's logged. Add the workout to a day first.
- Exercises and sets are reordered with buttons rather than by dragging.
