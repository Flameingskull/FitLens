## Overview

Workouts can now plan your rest, not just your sets. Give each set its own rest, one rest for every set of an
exercise, and a longer break before the next exercise. When you log the workout, the rest timer follows the plan,
and together with 1.0.72's automatic move to the next exercise, the next exercise opens with its break already
counting down.

This update upgrades the database to add the new rest fields. Everything you've logged and every workout you've
built is kept as it was; nothing has a rest set until you add one.

## What's new

- **Rest in the workout editor.** Open an exercise's sets in a workout (**Edit workout → tap an exercise**). Under
  **Rest**, choose:
  - **Between sets:** one rest for every set, or turn off **Same rest for every set** to give each predefined set
    its own rest in a new rest column;
  - **Before the next exercise:** the break after the exercise's last set.
  **Default** leaves either one unset, so the exercise's own rest, and then your usual rest, apply as before.
- **Set rest for every exercise.** A day's ⋮ menu has **Set rest for every exercise**, to give a whole day the same
  rest between sets and before each next exercise in one step.
- **See the plan at a glance.** Each exercise in the editor, and on the library's workout day cards, shows its rest,
  for example **Rest 90 s · then 2 min**.

## Improved

- **The rest timer follows your workout.** After a set, the timer uses, in order: that set's planned rest, the
  exercise's planned rest in the workout you logged, the exercise's own rest, then your usual rest. After an
  exercise's last set it uses the planned rest before the next exercise.
- **Plans stay with the day you logged.** The rest is copied onto the day when you log a workout, so editing the
  workout later doesn't change a day already logged. Copying or moving a day, swapping or merging exercises, and
  saving a logged day as a workout all keep its rests.

## Known limitations

- Rests apply to workouts logged from a workout day from this version on. Days logged before this update use the
  exercise's own rest or your usual rest, as before.
- Choosing a chart type (line, bar, area or step) for each graph comes in the next update (#137), followed by the
  screen-by-screen FitNotes comparison (#134).
