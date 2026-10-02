## Overview

This update gives the sets you're logging most of the exercise screen, adds a done tick to every set (which can start
the rest timer), and makes the Today page easier to read. Body value names no longer break in the middle of a word, the change
under each value is shorter and clearer, and on a day with only body values or photos, the ways to start a workout
are in view. Text throughout the app now wraps between words, in balanced lines.

There are no database changes. Everything you've logged is kept as it was.

## What's new

- **A done tick on every set.** Each set on the exercise screen's Track tab has a tick box. Tick a set off when
  you finish it and, with **Settings → Rest timer → Start after saving a set** on, the rest timer starts straight away
  (in a superset, after the round's last exercise). A tick just after saving the same set doesn't restart a rest that
  has only just begun. Once every set of an exercise is ticked, its card on the Today page shows a gold tick.

## Improved

- **More room for your sets.** The entry area on the exercise screen is more compact while staying easy to use:
  smaller − and + buttons, set type and effort as two dropdowns on one row, slimmer Save and Clear, and no date line
  when you're logging today. The list of sets below gets the space.
- **The exercise's name in full.** As in FitNotes, the top bar has the workout drawer (≡) on the left, then the
  exercise's name, the rest timer, records and info. The phone's back gesture returns to the day.
- **Settings → Workout & logging → Show sets done** (previously "Tick sets off as you do them") now only controls
  the done counts on the Today page and in the workout drawer.
- The body tracker list and each measurement's History use the same shorter change wording as the Today page.
- The README has been refreshed to describe the app as it is in this release.

## Fixed

- **Body values read cleanly.** Each measurement's name and value share one line, and the name is never split
  ("Bodywei / ght" is gone). The change sits on its own line underneath, in plain text rather than spaced-out
  capitals: "▼ 0.7 kg since 21 Aug · was 113.25 kg", or "No change since 18 Sept" when the value is the same.
- **Start a workout without scrolling.** On a day with body values or photos but no workout, **Add exercise**,
  **Add workout** and **Copy previous workout** now sit in one row just below them. Before, they were pushed off the
  bottom of the screen.
- **Better wrapping everywhere.** Headings and running text across the app now break between words, with balanced
  line lengths, and never hyphenate or split a word.

## Known limitations

- A very long measurement name is shortened with "…" on the Today page rather than wrapping. Tap the row to see it
  in full on the measurement's own screen.
