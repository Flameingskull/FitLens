## Overview

This build brings saved workouts and routines together as one feature: **workouts**, made of days you name yourself,
as FitNotes does with routines. It also fixes a bug that could throw away a workout while you were building it,
replaces black text on buttons with the brand gold, and adds an info button to the exercise screen.

This update changes the database (version 13). Everything you've saved moves into the new workouts on its own the first
time the app opens. Nothing you've logged changes.

## What's new

- **Workouts with named days.** A workout is now one thing: a name, optional notes, and days you name freely, such as
  "Monday" or "Push Day". Each day holds its own exercises. Open them from the exercise library's title (**+** on the
  day log): the list shows **All exercises**, each of your workouts, and **Create new workout**.
- **Log all.** Choosing a workout in the library shows its days as cards, each listing its exercises and how their
  sets are filled. **Log all** adds the whole day to the date you're on, with **Undo**. The day FitLens suggests next is
  marked in gold. Tap an exercise on a card to open it on its own.
- **A new workout editor.** Each day is a card with **+** (Add exercise) and a menu to rename, duplicate, move, copy to
  another workout or delete the day. **Add day** creates another. Each exercise's menu handles supersets, swapping and
  removing, and up and down arrows reorder it. **Duplicate workout** and **Delete workout** are in the editor's ⋮ menu.
- **How each exercise's sets are filled.** Tap an exercise in the editor to choose **Copy previous** (repeat what you
  did last time), **Predefined** (a list of sets, where a blank weight or reps copies last time's value) or **None**
  (the exercise opens for you to log as you go). Adding a single exercise asks straight away.
- **Exercise info.** The exercise screen's top bar has an **(i)** button that shows the exercise's notes, weight
  increment, rest time, default graph and type at a glance. **Edit** opens the full editor.

## Improved

- **Your saved workouts and routines move across automatically.** Each routine keeps its days, and each day takes a
  copy of the exercises and sets of the saved workout it used. A saved workout that no routine used becomes a one-day
  workout with the same name. Days you've already logged still count towards the next-day suggestion. Restoring an
  older `.fitlens` backup goes through the same move.
- **Add workout, Replace this workout and Save as a workout day** on the day log's ⋮ menu use the new workouts. Add
  workout lists every workout's days (the one the library shows first, with its next day marked), or lets you choose
  exercises on the spot and save them as a new workout. **Save as a workout day** saves the day's sets as a new
  workout, a new day of an existing one, or in place of an existing day.
- **Gold text throughout.** Main buttons are now imperial purple glass with a gold rim and gold text, and delete
  buttons are deep wine with gold text. Snackbars, the chosen day or time in date and time pickers, and the plates in
  the plate calculator no longer use black text.

## Fixed

- **Scrolling no longer closes the exercise picker.** Scrolling a list in a sheet, such as the exercise picker while
  building a workout, could drag the whole sheet closed and lose everything you'd chosen. Lists in sheets now only
  scroll. While you have exercises ticked, the picker closes only with **Cancel** (#103).

## Removed

- The separate **Workouts** and **Routines** pages, and their entries in the day log's ⋮ menu. Everything they did is
  now in the workout editor and the library.
- **Edit exercise** from the exercise screen's ⋮ menu. It's now the **Edit** button in exercise info.

## Known limitations

- Exercises in the editor move with up and down arrows rather than a drag handle.
- Comments on an individual exercise within a day's workout are still to come (#107).
- Distances show "dist" as their unit until unit settings arrive (#7).
