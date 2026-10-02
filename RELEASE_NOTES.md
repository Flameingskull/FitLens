## Overview

This update rebuilds the body tracker to work exactly as it does in FitNotes. The body tracker now opens on a list of
your measurements. Each measurement has its own Track, History and Graph tabs, laid out like the exercise screen, so
logging your bodyweight or waist works the way you log a set.

There are no database changes. Every measurement and value you've logged is kept as it was.

## What's new

- **Your measurements, listed.** The body tracker lists every measurement you have switched on (Bodyweight, Body Fat,
  Waist and the rest, in your order) with its latest value and date. Measurements you haven't logged yet are listed
  too, ready for a first value.
- **Track a measurement as you track a set.** Tap a measurement to open its **Track** tab: the day (swipe or use the
  arrows to change it, or tap it to pick a date), the value with − and + buttons, the time and a comment. **Save**
  and **Clear** log a new value, and the day's values are listed underneath. Tap one to select it, then **Update** or
  **Delete** it. Deleting can be undone. A new value starts from your latest one, as in FitNotes.
- **History.** Every day you logged the measurement, newest first, with the time, comment and change since the value
  before. Tap a value to open it on Track.
- **Graph.** The graph keeps its ranges, trend line, goal line, photo days, full-screen view and statistics. It now
  belongs to the measurement you opened.

## Improved

- **Changes are shown in full figures.** Wherever FitLens shows how something moved, it now gives the value itself,
  the change in its own unit and the value it moved from, for example "82.4 kg" and "▲ +0.4 kg from 82 kg on
  1 Oct", rather than a bare "+0.4":
  - the body values on the day log, the body tracker list and each measurement's History;
  - Analysis totals ("+2 workouts vs the week before (3 workouts)");
  - Breakdown, which now shows the selected slice's figure under its percentage, and its value the period before;
  - exercise goals, which now say how much is left ("10 kg to go").
- A measurement's goal is set from its ⋮ menu and shown on its Track tab.
- TalkBack reads body values as "Value 1, 82.4 kilograms, at 07:30" instead of calling them sets.

## Fixed

- **No more black text.** Some text showed in black on the dark backgrounds, across several screens and sheets. All
  text is now ivory or gold.

## Known limitations

- Values imported from FitNotes can be viewed but not changed in FitLens, because the next import would bring the
  original back. Change them in FitNotes and import again.
