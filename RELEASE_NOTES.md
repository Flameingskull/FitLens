## Overview

This build lets you put a workout in the order you trained it, and adds FitNotes's **workout drawer** to the exercise
screen, so you can move between exercises without going back to the day log. Exercises can be moved up and down on
the day log and in the drawer, and sets can be moved within an exercise.

This update upgrades FitLens's database to remember the order of your sets. Everything you've logged keeps the order
it already had, and backups from earlier versions restore the same way.

## What's new

- **Workout drawer.** The menu button on the exercise screen opens a panel listing the day's exercises in order, each
  with its category and set count, and the one you're on picked out. The panel also shows the day's totals. From
  there you can:
  - tap an exercise to go straight to it;
  - move exercises up or down;
  - **Add exercise**;
  - go **Back to the day log**.
- **Reorder exercises.** An exercise card's menu on the day log has **Move up** and **Move down**. An exercise's sets
  move with it.
- **Reorder sets.** On the exercise screen, tap a set, then use **Move set up** or **Move set down**.

## Improved

- **The order sticks.** The day log, calendar, sharing, the PDF report and Save as a workout all follow the order
  you set. New sets always go to the end, and a set you delete and bring back with **Undo** returns to its old place.

## Known limitations

- Reordering uses buttons and TalkBack actions. Dragging items into place is still to come.
- Supersets and circuits aren't available yet ([#18](https://github.com/Flameingskull/FitLens/issues/18)).
