## Overview

This update makes the Today page easier to read. Body value names no longer break in the middle of a word, the change
under each value is shorter and clearer, and on a day with only body values or photos, the ways to start a workout
are in view. Text throughout the app now wraps between words, in balanced lines.

There are no database changes. Everything you've logged is kept as it was.

## Fixed

- **Body values read cleanly.** Each measurement's name and value share one line, and the name is never split
  ("Bodywei / ght" is gone). The change sits on its own line underneath, in plain text rather than spaced-out
  capitals: "▼ 0.7 kg since 21 Aug · was 113.25 kg", or "No change since 18 Sept" when the value is the same.
- **Start a workout without scrolling.** On a day with body values or photos but no workout, **Add exercise**,
  **Add workout** and **Copy previous workout** now sit in one row just below them. Before, they were pushed off the
  bottom of the screen.
- **Better wrapping everywhere.** Headings and running text across the app now break between words, with balanced
  line lengths, and never hyphenate or split a word.

## Improved

- The body tracker list and each measurement's History use the same shorter change wording.
- The README has been refreshed to describe the app as it is in this release.

## Known limitations

- A very long measurement name is shortened with "…" on the Today page rather than wrapping. Tap the row to see it
  in full on the measurement's own screen.
